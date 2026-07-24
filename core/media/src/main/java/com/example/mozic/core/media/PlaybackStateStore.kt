package com.example.mozic.core.media

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private const val PLAYBACK_STATE_DATASTORE_NAME = "playback_state"

/** Song IDs are backend UUIDs; this never appears in one, unlike a comma. */
private const val QUEUE_ID_SEPARATOR = ""

private val Context.playbackStateDataStore: DataStore<Preferences> by preferencesDataStore(
    name = PLAYBACK_STATE_DATASTORE_NAME,
)

data class PersistedPlaybackState(
    val queueSongIds: List<String>,
    val queueIndex: Int,
    val positionMs: Long,
    val speed: Float,
    val shuffleEnabled: Boolean,
    /** Raw `Player.REPEAT_MODE_*` int — this module already wraps media3, so no domain mapping needed here. */
    val repeatMode: Int,
)

/**
 * Survives an ordinary process death (low-memory kill, swipe-from-recents) — I3
 * (`doc/CLAUDE_PERSON_A.md` §5.7). [Media3PlayerController] writes to this (throttled) while a
 * queue is loaded and reads it once at startup so a fresh process can restore the mini
 * player/Now Playing UI and reseed the actual player at the last position — paused, since a
 * fresh process must never resume audio on its own.
 *
 * Constructed directly from the `Context` [Media3PlayerController] already holds rather than
 * Hilt-injected: it has no interface to fake and no other consumer, and adding it as an 8th
 * constructor parameter there trips the project's `LongParameterList` detekt threshold for no
 * real benefit.
 */
class PlaybackStateStore(context: Context) {
    private val dataStore = context.playbackStateDataStore

    suspend fun save(state: PersistedPlaybackState) {
        dataStore.edit { prefs ->
            prefs[Keys.QUEUE_IDS] = state.queueSongIds.joinToString(QUEUE_ID_SEPARATOR)
            prefs[Keys.QUEUE_INDEX] = state.queueIndex
            prefs[Keys.POSITION_MS] = state.positionMs
            prefs[Keys.SPEED] = state.speed
            prefs[Keys.SHUFFLE_ENABLED] = state.shuffleEnabled
            prefs[Keys.REPEAT_MODE] = state.repeatMode
        }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    suspend fun load(): PersistedPlaybackState? {
        val prefs = dataStore.data.first()
        val ids = prefs[Keys.QUEUE_IDS]?.split(QUEUE_ID_SEPARATOR)?.filter { it.isNotBlank() }
        if (ids.isNullOrEmpty()) return null
        return PersistedPlaybackState(
            queueSongIds = ids,
            queueIndex = prefs[Keys.QUEUE_INDEX] ?: 0,
            positionMs = prefs[Keys.POSITION_MS] ?: 0L,
            speed = prefs[Keys.SPEED] ?: 1f,
            shuffleEnabled = prefs[Keys.SHUFFLE_ENABLED] ?: false,
            repeatMode = prefs[Keys.REPEAT_MODE] ?: 0,
        )
    }

    private object Keys {
        val QUEUE_IDS = stringPreferencesKey("queue_song_ids")
        val QUEUE_INDEX = intPreferencesKey("queue_index")
        val POSITION_MS = longPreferencesKey("position_ms")
        val SPEED = floatPreferencesKey("speed")
        val SHUFFLE_ENABLED = booleanPreferencesKey("shuffle_enabled")
        val REPEAT_MODE = intPreferencesKey("repeat_mode")
    }
}
