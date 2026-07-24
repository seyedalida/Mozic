package com.example.mozic.core.media

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.mozic.core.common.result.getOrNull
import com.example.mozic.core.domain.model.PlayerState
import com.example.mozic.core.domain.model.Song
import com.example.mozic.core.domain.player.PlayerController
import com.example.mozic.core.domain.repository.LibraryRepository
import com.example.mozic.core.domain.repository.SongRepository
import com.example.mozic.core.domain.repository.UserPreferencesRepository
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

private const val POSITION_TICK_PLAYING_MS = 250L
private const val POSITION_TICK_IDLE_MS = 1_000L
private const val SLEEP_TIMER_TICK_MS = 1_000L

/**
 * How often [Media3PlayerController.persistState] actually writes to disk while a queue is
 * loaded (I3, `doc/CLAUDE_PERSON_A.md` §5.7) — ticked from the same position loop that already
 * runs every [POSITION_TICK_PLAYING_MS]/[POSITION_TICK_IDLE_MS], so this just throttles disk
 * writes rather than adding a second timer. A kill between writes loses at most this much
 * resume accuracy, which is a fine trade for not hitting DataStore several times a second.
 */
private const val PERSIST_INTERVAL_MS = 5_000L

/**
 * Single-player fade-out/fade-in crossfade (`doc/CLAUDE_PERSON_A.md` §5.6) — the
 * cut-list-safe alternative to a true dual-player crossfade: the last/first
 * [CROSSFADE_DURATION_MS] of each track ramp the shared [MediaController]'s
 * volume down then back up instead of overlapping two players.
 */
private const val CROSSFADE_DURATION_MS = 3_000L
private const val CROSSFADE_STEP_MS = 50L

/**
 * The real [PlayerController]. Talks to [PlaybackService]'s ExoPlayer only through a
 * [MediaController] — see A1 in `doc/CLAUDE_PERSON_A.md` for why. Everyone else (Home, Search,
 * Library, Playlists, Downloads view models) already codes against [PlayerController]; this
 * class is bound in place of `FakePlayerController` via [com.example.mozic.core.media.di.MediaModule].
 */
@Singleton
class Media3PlayerController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val songRepository: SongRepository,
    private val libraryRepository: LibraryRepository,
    private val playbackSourceResolver: PlaybackSourceResolver,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val scope: CoroutineScope,
) : PlayerController {

    private val internalState = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = internalState.asStateFlow()

    private val playbackStateStore = PlaybackStateStore(context)

    @Volatile
    private var queueSongsById: Map<String, Song> = emptyMap()

    /** Read on every position tick — cheaper than collecting the settings [Flow] from there directly. */
    @Volatile
    private var crossfadeEnabled: Boolean = true

    private var sleepJob: Job? = null
    private var fadeInJob: Job? = null

    /** Cancelled and replaced on every (re)connect so a stale controller never leaks a running loop. */
    private var tickJob: Job? = null

    private var lastPersistedAtMs = 0L

    /** A `var`, not a `val`: [buildController] replaces this with a fresh [Deferred] on every reconnect. */
    private var controllerDeferred: Deferred<MediaController> = buildController()

    init {
        scope.launch {
            userPreferencesRepository.preferences
                .map { it.crossfadeEnabled }
                .distinctUntilChanged()
                .collect { crossfadeEnabled = it }
        }
    }

    /**
     * [MediaController] talks to [PlaybackService]'s session across a binder connection that
     * dies whenever the service does (e.g. `onTaskRemoved` stopping it while paused, or the
     * whole process being killed). Without rebuilding on disconnect, every command sent through
     * a now-dead [controllerDeferred] would silently no-op forever — the actual "no leaked
     * players" edge case I3 targets isn't a literal ExoPlayer leak (the service already releases
     * its own player/session in `onDestroy`), it's this controller-side reference outliving the
     * connection it was built for.
     */
    private fun buildController(): Deferred<MediaController> = scope.async {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val controller = MediaController.Builder(context, token)
            .setListener(
                object : MediaController.Listener {
                    override fun onDisconnected(controller: MediaController) {
                        controller.release()
                        controllerDeferred = buildController()
                    }
                },
            )
            .buildAsync()
            .await()
        onControllerConnected(controller)
        controller
    }

    private fun onControllerConnected(controller: MediaController) {
        attachListener(controller)
        scope.launch { seedControllerIfEmpty(controller) }
        tickJob?.cancel()
        tickJob = tickPosition(controller)
    }

    /**
     * Reseeds a freshly (re)connected, empty [MediaController] with whatever queue we know
     * about — the in-memory one if this process already had one loaded (a mid-session
     * reconnect, e.g. after the service was stopped while paused), otherwise the one persisted
     * to disk by a previous process (I3's process-death restore). Either way the player ends up
     * `prepare()`d and paused at the right position, never auto-playing on its own.
     */
    private suspend fun seedControllerIfEmpty(controller: MediaController) {
        if (controller.mediaItemCount > 0) return
        val current = internalState.value
        if (current.queue.isNotEmpty()) {
            seedController(controller, current.queue, current.queueIndex, current.positionMs, current.speed)
            return
        }
        val restored = loadRestoredQueue() ?: return
        queueSongsById = restored.songs.associateBy { it.id }
        internalState.update {
            it.copy(
                queue = restored.songs,
                queueIndex = restored.index,
                currentSong = restored.songs[restored.index],
                positionMs = restored.positionMs,
                speed = restored.speed,
            )
        }
        seedController(controller, restored.songs, restored.index, restored.positionMs, restored.speed)
    }

    private suspend fun seedController(
        controller: MediaController,
        songs: List<Song>,
        index: Int,
        positionMs: Long,
        speed: Float,
    ) {
        val mediaItems = songs.map { playbackSourceResolver.resolve(it) }
        val startIndex = index.coerceIn(0, mediaItems.lastIndex)
        controller.setMediaItems(mediaItems, startIndex, positionMs.coerceAtLeast(0L))
        controller.playbackParameters = PlaybackParameters(speed)
        controller.prepare()
        controller.pause()
    }

    private suspend fun loadRestoredQueue(): RestoredQueue? {
        val saved = runCatching { playbackStateStore.load() }.getOrNull() ?: return null
        val resolved = saved.queueSongIds.mapNotNull { id -> songRepository.song(id).getOrNull() }
        if (resolved.isEmpty()) {
            playbackStateStore.clear()
            return null
        }
        return RestoredQueue(
            songs = resolved,
            index = saved.queueIndex.coerceIn(0, resolved.lastIndex),
            positionMs = saved.positionMs,
            speed = saved.speed,
        )
    }

    private data class RestoredQueue(val songs: List<Song>, val index: Int, val positionMs: Long, val speed: Float)

    override fun play(songId: String) {
        scope.launch {
            val song = songRepository.song(songId).getOrNull() ?: return@launch
            setQueueAndPlay(listOf(song), startIndex = 0)
        }
    }

    override fun playQueue(songIds: List<String>, startIndex: Int, shuffle: Boolean) {
        scope.launch {
            val resolved = songIds.mapNotNull { id -> songRepository.song(id).getOrNull() }
            if (resolved.isEmpty()) return@launch
            val ordered = if (shuffle) resolved.shuffled() else resolved
            setQueueAndPlay(ordered, startIndex.coerceIn(0, ordered.lastIndex))
        }
    }

    override fun pause() = withController { it.pause() }

    override fun resume() = withController { it.play() }

    override fun togglePlayPause() = withController { if (it.isPlaying) it.pause() else it.play() }

    /**
     * Resets [internalState] synchronously rather than waiting on the
     * controller round-trip: [PlayerStateMapper.apply] deliberately keeps the
     * previous [PlayerState.currentSong] when `player.currentMediaItem` is
     * null (to avoid flicker during ordinary transitions), so relying on it
     * here would leave the mini player showing the stopped song.
     */
    override fun stop() {
        withController { controller ->
            controller.stop()
            controller.clearMediaItems()
        }
        queueSongsById = emptyMap()
        internalState.update { PlayerState() }
        lastPersistedAtMs = 0L
        scope.launch { playbackStateStore.clear() }
    }

    override fun next() = withController { it.seekToNextMediaItem() }

    override fun previous() = withController { it.seekToPreviousMediaItem() }

    override fun seekTo(positionMs: Long) = withController { it.seekTo(positionMs) }

    override fun setSpeed(speed: Float) = withController { it.playbackParameters = PlaybackParameters(speed) }

    override fun toggleShuffle() = withController { it.shuffleModeEnabled = !it.shuffleModeEnabled }

    override fun cycleRepeatMode() = withController { controller ->
        controller.repeatMode = when (controller.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    override fun setSleepTimer(duration: Duration?) {
        sleepJob?.cancel()
        if (duration == null) {
            internalState.update { it.copy(sleepTimerRemainingMs = null) }
            return
        }
        sleepJob = scope.launch {
            var remaining = duration.inWholeMilliseconds
            while (remaining > 0) {
                delay(SLEEP_TIMER_TICK_MS)
                remaining -= SLEEP_TIMER_TICK_MS
                internalState.update { it.copy(sleepTimerRemainingMs = remaining.coerceAtLeast(0L)) }
            }
            pause()
            internalState.update { it.copy(sleepTimerRemainingMs = null) }
        }
    }

    private suspend fun setQueueAndPlay(songs: List<Song>, startIndex: Int) {
        queueSongsById = songs.associateBy { it.id }
        internalState.update {
            it.copy(queue = songs, queueIndex = startIndex, currentSong = songs[startIndex])
        }
        val mediaItems = songs.map { song -> playbackSourceResolver.resolve(song) }
        val controller = controllerDeferred.await()
        controller.setMediaItems(mediaItems, startIndex, /* startPositionMs = */ 0L)
        controller.prepare()
        controller.play()
        persistState(force = true)
    }

    private fun attachListener(controller: MediaController) {
        controller.addListener(
            object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) {
                    internalState.update { current ->
                        PlayerStateMapper.apply(player, current) { id -> queueSongsById[id] }
                    }
                }

                /**
                 * Fires on every real playback start — the initial item of a
                 * new queue, a skip, *and* autoplay-advance to the next queued
                 * song — never on a bare UI tap-to-play with no resulting
                 * transition. This is the one seam B5 flagged: only real
                 * playback transitions should count towards Recently Played,
                 * not every screen's own click handler. Same reasoning applies
                 * to bumping the catalog's `popularity` column — a separate
                 * `scope.launch` (not chained after the one above) so a
                 * failed/offline popularity RPC can never affect the local
                 * Recently Played write, and vice versa; `scope` is
                 * `SupervisorJob`-backed (`MediaModule`), so either one
                 * throwing doesn't cancel the other or the shared scope.
                 */
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    val songId = mediaItem?.mediaId ?: return
                    scope.launch { libraryRepository.recordPlayed(songId) }
                    scope.launch { runCatching { songRepository.recordPlaybackForPopularity(songId) } }
                    fadeIn(controller)
                }
            },
        )
    }

    private fun tickPosition(controller: MediaController): Job = scope.launch {
        while (isActive) {
            internalState.update { it.copy(positionMs = controller.currentPosition.coerceAtLeast(0L)) }
            applyCrossfadeOut(controller)
            persistState()
            delay(if (controller.isPlaying) POSITION_TICK_PLAYING_MS else POSITION_TICK_IDLE_MS)
        }
    }

    /**
     * Throttled (see [PERSIST_INTERVAL_MS]) disk write of the current queue/position/speed, so a
     * killed process can restore into roughly the right spot (I3). Ticked from [tickPosition],
     * which already runs whenever a queue is loaded — including while paused, at the slower
     * [POSITION_TICK_IDLE_MS] cadence — so pausing right before a swipe-kill still gets captured
     * within a second, without a second timer.
     */
    private fun persistState(force: Boolean = false) {
        val snapshot = internalState.value
        if (snapshot.queue.isEmpty()) return
        val now = System.currentTimeMillis()
        if (!force && now - lastPersistedAtMs < PERSIST_INTERVAL_MS) return
        lastPersistedAtMs = now
        val toSave = PersistedPlaybackState(
            queueSongIds = snapshot.queue.map { it.id },
            queueIndex = snapshot.queueIndex,
            positionMs = snapshot.positionMs,
            speed = snapshot.speed,
        )
        scope.launch { playbackStateStore.save(toSave) }
    }

    /**
     * Ramps volume down over the last [CROSSFADE_DURATION_MS] of a track, ticked
     * from the same loop that already polls position — cheap, no extra timer.
     * Skipped while [fadeInJob] owns the volume (right after a transition),
     * otherwise this would fight the fade-in and mute the next track.
     */
    private fun applyCrossfadeOut(controller: MediaController) {
        if (!crossfadeEnabled) {
            // Snaps volume back up immediately if the setting is toggled off mid-fade.
            controller.volume = 1f
            return
        }
        if (fadeInJob?.isActive == true || !controller.isPlaying) return
        val durationMs = controller.duration
        if (durationMs == C.TIME_UNSET || durationMs <= 0) return
        val remainingMs = durationMs - controller.currentPosition
        controller.volume = if (remainingMs in 0..CROSSFADE_DURATION_MS) {
            (remainingMs.toFloat() / CROSSFADE_DURATION_MS).coerceIn(0f, 1f)
        } else {
            1f
        }
    }

    /** Ramps volume back up from silence on every real transition — initial queue, skip, or autoplay-advance. */
    private fun fadeIn(controller: MediaController) {
        if (!crossfadeEnabled) {
            controller.volume = 1f
            return
        }
        fadeInJob?.cancel()
        fadeInJob = scope.launch {
            controller.volume = 0f
            val steps = (CROSSFADE_DURATION_MS / CROSSFADE_STEP_MS).toInt()
            for (step in 1..steps) {
                delay(CROSSFADE_STEP_MS)
                controller.volume = (step.toFloat() / steps).coerceIn(0f, 1f)
            }
        }
    }

    private fun withController(action: (MediaController) -> Unit) {
        scope.launch { action(controllerDeferred.await()) }
    }

    private suspend fun <T> ListenableFuture<T>.await(): T =
        suspendCancellableCoroutine { cont ->
            Futures.addCallback(
                this,
                object : FutureCallback<T> {
                    override fun onSuccess(result: T) = cont.resume(result)

                    override fun onFailure(t: Throwable) = cont.resumeWithException(t)
                },
                MoreExecutors.directExecutor(),
            )
            cont.invokeOnCancellation { cancel(false) }
        }
}
