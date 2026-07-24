package com.example.mozic.feature.player

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mozic.core.designsystem.R as DesignSystemR
import com.example.mozic.core.designsystem.theme.dimens
import com.example.mozic.core.designsystem.theme.mozicColors
import com.example.mozic.core.domain.model.PlayerState
import com.example.mozic.core.domain.model.RepeatMode
import com.example.mozic.core.domain.model.Song
import com.example.mozic.core.ui.animation.LocalSharedTransitionScope
import com.example.mozic.core.ui.color.rememberDominantColor
import com.example.mozic.core.ui.component.CoverImage
import com.example.mozic.core.ui.component.DownloadIconButton
import java.util.Locale

/** `tween(600)` per the palette-gradient spec — a song change should feel like a soft cross-fade. */
private const val PALETTE_ANIMATION_DURATION_MS = 600

/**
 * Blend the extracted swatch 30% toward the theme background before painting
 * it — an unblended photo color can be too saturated/dark for onSurface text
 * to stay readable on top of it, in both themes.
 */
private const val PALETTE_BACKGROUND_BLEND = 0.3f

/** One full rotation every 8s — a slow, ambient vinyl spin, not attention-grabbing. */
private const val DISC_DEGREES_PER_SECOND = 360f / 8f

/** Design handoff's "NOW PLAYING" label: 11px, letter-spacing 2px, uppercase. */
private val NOW_PLAYING_TITLE_LETTER_SPACING = 2.sp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun NowPlayingScreen(
    onBackClick: () -> Unit,
    onShareClick: (songId: String) -> Unit,
    onAddToPlaylistClick: (songId: String) -> Unit,
    modifier: Modifier = Modifier,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val extras by viewModel.extras.collectAsStateWithLifecycle()
    val song = state.currentSong

    val snackbarHostState = remember { SnackbarHostState() }
    val upgradeMessage = stringResource(DesignSystemR.string.premium_upsell_body)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                PlayerEffect.ShowUpgradePrompt -> snackbarHostState.showSnackbar(upgradeMessage)
            }
        }
    }

    // Design handoff: Share/Sleep timer/Speed moved off the top bar into the
    // three-dot overflow menu (`PlayerOverflowMenu`); tapping "Sleep timer"/
    // "Speed" there closes the menu and opens one of these two dialogs
    // instead of a nested dropdown — Compose has no first-class menu-in-menu,
    // and this reuses the same AlertDialog pattern already established by
    // `feature/playlists`' CreatePlaylistDialog.
    var menuExpanded by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }

    val backgroundColor = MaterialTheme.colorScheme.background
    val dominantColor by rememberDominantColor(model = song?.coverImageUrl, fallback = backgroundColor)
    val animatedAccent by animateColorAsState(
        targetValue = lerp(dominantColor, backgroundColor, PALETTE_BACKGROUND_BLEND),
        animationSpec = tween(PALETTE_ANIMATION_DURATION_MS),
        label = "nowPlayingPaletteAccent",
    )
    // Raw Color values here are the extracted-photo exception, same as
    // HomeCarousel's caption scrim — this paints over/behind cover art, not
    // an app surface, so it can't come from the static theme palette.
    val screenBackground = Brush.verticalGradient(listOf(animatedAccent, backgroundColor))

    Scaffold(
        modifier = modifier.background(screenBackground),
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(DesignSystemR.string.player_now_playing_title),
                        style = MaterialTheme.typography.labelSmall,
                        letterSpacing = NOW_PLAYING_TITLE_LETTER_SPACING,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = stringResource(DesignSystemR.string.cd_collapse_player),
                        )
                    }
                },
                actions = {
                    PlayerOverflowMenu(
                        expanded = menuExpanded,
                        onExpandedChange = { menuExpanded = it },
                        actions = PlayerOverflowMenuActions(
                            onAddToPlaylistClick = { song?.let { onAddToPlaylistClick(it.id) } },
                            onSleepTimerClick = { showSleepTimerDialog = true },
                            onSpeedClick = { showSpeedDialog = true },
                            onShareClick = { song?.let { onShareClick(it.id) } },
                        ),
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { innerPadding ->
        val contentModifier = Modifier
            .padding(innerPadding)
            .fillMaxSize()

        if (song == null) {
            NothingPlayingMessage(modifier = contentModifier)
        } else {
            NowPlayingContent(
                song = song,
                state = state,
                extras = extras,
                animatedVisibilityScope = animatedVisibilityScope,
                actions = NowPlayingActions(
                    onPlayPauseClick = viewModel::togglePlayPause,
                    onNext = viewModel::next,
                    onPrevious = viewModel::previous,
                    onSeekFinished = viewModel::seekTo,
                    onLikeClick = viewModel::toggleLike,
                    onDownloadClick = viewModel::requestDownload,
                    onRemoveDownloadClick = viewModel::removeDownload,
                    onUpgradeRequired = viewModel::requestUpgrade,
                    onToggleShuffle = viewModel::toggleShuffle,
                    onCycleRepeatMode = viewModel::cycleRepeatMode,
                ),
                modifier = contentModifier,
            )
        }
    }

    if (showSleepTimerDialog) {
        SleepTimerDialog(
            onSetTimer = viewModel::setSleepTimer,
            onDismiss = { showSleepTimerDialog = false },
        )
    }
    if (showSpeedDialog) {
        PlaybackSpeedDialog(
            onSetSpeed = viewModel::setSpeed,
            onDismiss = { showSpeedDialog = false },
        )
    }
}

@Composable
private fun NothingPlayingMessage(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(DesignSystemR.string.state_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Bundles the Now Playing screen's user-action callbacks to keep
 * [NowPlayingContent] under detekt's parameter-count limit.
 */
private data class NowPlayingActions(
    val onPlayPauseClick: () -> Unit,
    val onNext: () -> Unit,
    val onPrevious: () -> Unit,
    val onSeekFinished: (Long) -> Unit,
    val onLikeClick: () -> Unit,
    val onDownloadClick: () -> Unit,
    val onRemoveDownloadClick: () -> Unit,
    val onUpgradeRequired: () -> Unit,
    val onToggleShuffle: () -> Unit,
    val onCycleRepeatMode: () -> Unit,
)

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun NowPlayingContent(
    song: Song,
    state: PlayerState,
    extras: PlayerExtrasUiState,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    actions: NowPlayingActions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MaterialTheme.dimens.screenHorizontalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        RotatingCover(
            songId = song.id,
            coverUrl = song.coverImageUrl,
            contentDescription = song.title,
            isPlaying = state.isPlaying,
            animatedVisibilityScope = animatedVisibilityScope,
            modifier = Modifier.size(MaterialTheme.dimens.nowPlayingCoverSize),
        )
        Spacer(Modifier.height(MaterialTheme.dimens.spaceLg))
        Text(
            text = song.title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(MaterialTheme.dimens.spaceXxs))
        Text(
            text = song.artistName,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(MaterialTheme.dimens.spaceMd))

        AudioVisualizer(
            isPlaying = state.isPlaying,
            modifier = Modifier.height(MaterialTheme.dimens.visualizerHeight),
        )

        Spacer(Modifier.height(MaterialTheme.dimens.spaceMd))

        PlayerSeekBar(
            positionMs = state.positionMs,
            durationMs = state.durationMs,
            onSeekFinished = actions.onSeekFinished,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(MaterialTheme.dimens.spaceLg))

        TransportRow(state = state, actions = actions, modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.height(MaterialTheme.dimens.spaceLg))

        SecondaryRow(extras = extras, actions = actions, modifier = Modifier.fillMaxWidth())
    }
}

/**
 * Design handoff: Shuffle — Previous — Play/Pause — Next — Repeat, `space-between`.
 *
 * Deliberately left to follow the ambient layout direction rather than
 * forcing LTR: under Farsi (RTL), the whole row visually mirrors, putting
 * the Next button on the left and the Previous button on the right of
 * Play/Pause, matching the app's other RTL-mirrored rows. [Icons.Filled.SkipNext]/
 * [Icons.Filled.SkipPrevious] aren't auto-mirrored, though, so their glyphs
 * stay fixed regardless of position — left uncorrected, the left-hand
 * ("Next") button would still show a right-pointing glyph, i.e. one that
 * visually points *away* from the direction it navigates. Swapping which
 * glyph goes on which button under RTL (without touching which button does
 * what) keeps each glyph pointing the way its button actually moves.
 */
@Composable
private fun TransportRow(state: PlayerState, actions: NowPlayingActions, modifier: Modifier = Modifier) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val previousIcon = if (isRtl) Icons.Filled.SkipNext else Icons.Filled.SkipPrevious
    val nextIcon = if (isRtl) Icons.Filled.SkipPrevious else Icons.Filled.SkipNext
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShuffleButton(enabled = state.shuffleEnabled, onClick = actions.onToggleShuffle)
        IconButton(onClick = actions.onPrevious) {
            Icon(
                imageVector = previousIcon,
                contentDescription = stringResource(DesignSystemR.string.cd_previous),
                modifier = Modifier.size(MaterialTheme.dimens.spaceXl),
            )
        }
        Box(
            modifier = Modifier
                .size(MaterialTheme.dimens.playerControlButtonSize)
                .clip(CircleShape)
                .background(brush = MaterialTheme.mozicColors.accentGradient)
                .clickable(onClick = actions.onPlayPauseClick),
            contentAlignment = Alignment.Center,
        ) {
            if (state.isBuffering) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = stringResource(
                        if (state.isPlaying) DesignSystemR.string.action_pause else DesignSystemR.string.action_play,
                    ),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(MaterialTheme.dimens.spaceXl),
                )
            }
        }
        IconButton(onClick = actions.onNext) {
            Icon(
                imageVector = nextIcon,
                contentDescription = stringResource(DesignSystemR.string.cd_next),
                modifier = Modifier.size(MaterialTheme.dimens.spaceXl),
            )
        }
        RepeatButton(mode = state.repeatMode, onClick = actions.onCycleRepeatMode)
    }
}

/** Toggle — tint switches muted/accent, same "active state" language as the design tokens. */
@Composable
private fun ShuffleButton(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            imageVector = Icons.Filled.Shuffle,
            contentDescription = stringResource(
                if (enabled) DesignSystemR.string.cd_shuffle_on else DesignSystemR.string.cd_shuffle_off,
            ),
            tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.mozicColors.textTertiary,
        )
    }
}

/**
 * Cycles off → all → one. The design's "one" state adds a small "1" badge to
 * the same glyph; the app's own Material icon set already has a distinct
 * [Icons.Filled.RepeatOne] glyph with that "1" baked in, so this swaps icons
 * instead of hand-drawing an overlay badge — same information, one fewer
 * custom-drawn element.
 */
@Composable
private fun RepeatButton(mode: RepeatMode, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val icon = if (mode == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat
    val contentDescriptionRes = when (mode) {
        RepeatMode.OFF -> DesignSystemR.string.cd_repeat_off
        RepeatMode.ALL -> DesignSystemR.string.cd_repeat_all
        RepeatMode.ONE -> DesignSystemR.string.cd_repeat_one
    }
    val tint = if (mode == RepeatMode.OFF) MaterialTheme.mozicColors.textTertiary else MaterialTheme.colorScheme.primary
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            imageVector = icon,
            contentDescription = stringResource(contentDescriptionRes),
            tint = tint,
        )
    }
}

/** Design handoff: Like + Download moved off the transport row into their own centered row below it. */
@Composable
private fun SecondaryRow(extras: PlayerExtrasUiState, actions: NowPlayingActions, modifier: Modifier = Modifier) {
    val likeTint = if (extras.isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(
            MaterialTheme.dimens.spaceXl + MaterialTheme.dimens.spaceLg,
            Alignment.CenterHorizontally,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = actions.onLikeClick) {
            Icon(
                imageVector = if (extras.isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = stringResource(
                    if (extras.isLiked) DesignSystemR.string.cd_unlike else DesignSystemR.string.cd_like,
                ),
                tint = likeTint,
            )
        }
        DownloadIconButton(
            downloadState = extras.downloadState,
            isPremium = extras.isPremium,
            onDownloadClick = actions.onDownloadClick,
            onRemoveClick = actions.onRemoveDownloadClick,
            onUpgradeRequired = actions.onUpgradeRequired,
        )
    }
}

/**
 * The cover, clipped to a disc and spinning while [isPlaying] — angle is
 * accumulated in a `remember`ed float rather than driven by
 * `rememberInfiniteTransition`, which always restarts from 0 on
 * recomposition; accumulating manually is what lets the disc *stop in place*
 * on pause instead of snapping back. `withFrameNanos` is frame-synced and
 * time-based, so the spin rate is identical at 60Hz and 120Hz.
 *
 * Also the shared-element anchor for the mini-player → full-player
 * transition (A5): paired with [MiniPlayerBar]'s cover via the same
 * [playerCoverSharedElementKey], both only wired up when both an enclosing
 * `SharedTransitionScope` and this destination's own [animatedVisibilityScope]
 * are available (i.e. real navigation, not a preview).
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun RotatingCover(
    songId: String,
    coverUrl: String,
    contentDescription: String?,
    isPlaying: Boolean,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    modifier: Modifier = Modifier,
) {
    var angleDegrees by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isPlaying) {
        if (!isPlaying) return@LaunchedEffect
        var lastFrameNanos = withFrameNanos { it }
        while (true) {
            withFrameNanos { nowNanos ->
                val deltaSeconds = (nowNanos - lastFrameNanos) / 1_000_000_000f
                angleDegrees = (angleDegrees + deltaSeconds * DISC_DEGREES_PER_SECOND) % 360f
                lastFrameNanos = nowNanos
            }
        }
    }

    val sharedTransitionScope = LocalSharedTransitionScope.current
    var coverModifier = modifier
    if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            coverModifier = coverModifier.sharedElement(
                rememberSharedContentState(key = playerCoverSharedElementKey(songId)),
                animatedVisibilityScope = animatedVisibilityScope,
            )
        }
    }

    CoverImage(
        model = coverUrl,
        contentDescription = contentDescription,
        modifier = coverModifier
            .clip(CircleShape)
            .graphicsLayer { rotationZ = angleDegrees },
    )
}

/**
 * While the user drags, the thumb follows the gesture locally and
 * `seekTo` only fires on release (`onValueChangeFinished`) — seeking on every
 * drag frame would fight the controller's own 250ms position-ticking loop and
 * make the thumb stutter.
 */
@Composable
private fun PlayerSeekBar(
    positionMs: Long,
    durationMs: Long,
    onSeekFinished: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragPositionMs by remember { mutableStateOf<Float?>(null) }
    val safeDurationMs = durationMs.coerceAtLeast(1L).toFloat()
    val displayedPositionMs = dragPositionMs ?: positionMs.toFloat().coerceIn(0f, safeDurationMs)

    Column(modifier = modifier) {
        Slider(
            value = displayedPositionMs,
            onValueChange = { dragPositionMs = it },
            onValueChangeFinished = {
                // Read the live state here, not `displayedPositionMs` — a
                // plain tap fires onValueChange then onValueChangeFinished
                // within the same gesture pass, before Compose gets a chance
                // to recompose with the just-set value, so a `val` captured
                // in this closure would still hold the pre-tap position. A
                // drag spans multiple frames, so a recomposition lands in
                // between and happened to mask this for drags only.
                dragPositionMs?.let { onSeekFinished(it.toLong()) }
                dragPositionMs = null
            },
            valueRange = 0f..safeDurationMs,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.outline,
            ),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatTrackTimeMs(displayedPositionMs.toLong()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatTrackTimeMs(durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Always Western digits regardless of locale — timestamps, not translatable text. */
private fun formatTrackTimeMs(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}
