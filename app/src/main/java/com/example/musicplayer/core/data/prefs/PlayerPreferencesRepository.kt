package com.example.musicplayer.core.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.playerPreferencesDataStore by preferencesDataStore(name = "player_preferences")

/**
 * The queue is stored as song ids rather than URIs: the ids are the MediaStore
 * ids we already key the Room cache on, so a restore can re-read current
 * metadata instead of resurrecting a stale copy of it.
 */
data class SavedPlaybackState(
    val songIds: List<Long>,
    val index: Int,
    val positionMs: Long,
    val shuffleEnabled: Boolean,
    val repeatMode: Int,
)

@Singleton
class PlayerPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val playbackSpeed: Flow<Float> = context.playerPreferencesDataStore.data.map { prefs ->
        prefs[Keys.PLAYBACK_SPEED] ?: 1f
    }

    val lastScanAt: Flow<Long?> = context.playerPreferencesDataStore.data.map { prefs ->
        prefs[Keys.LAST_SCAN_AT]
    }

    val dynamicColor: Flow<Boolean> = context.playerPreferencesDataStore.data.map { prefs ->
        prefs[Keys.DYNAMIC_COLOR] ?: false
    }

    suspend fun setPlaybackSpeed(speed: Float) {
        context.playerPreferencesDataStore.edit { prefs ->
            prefs[Keys.PLAYBACK_SPEED] = speed
        }
    }

    suspend fun setLastScanAt(timestamp: Long) {
        context.playerPreferencesDataStore.edit { prefs ->
            prefs[Keys.LAST_SCAN_AT] = timestamp
        }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.playerPreferencesDataStore.edit { prefs ->
            prefs[Keys.DYNAMIC_COLOR] = enabled
        }
    }

    suspend fun savePlaybackState(state: SavedPlaybackState) {
        context.playerPreferencesDataStore.edit { prefs ->
            prefs[Keys.QUEUE_SONG_IDS] = state.songIds.joinToString(",")
            prefs[Keys.QUEUE_INDEX] = state.index
            prefs[Keys.QUEUE_POSITION_MS] = state.positionMs
            prefs[Keys.SHUFFLE_ENABLED] = state.shuffleEnabled
            prefs[Keys.REPEAT_MODE] = state.repeatMode
        }
    }

    suspend fun clearPlaybackState() {
        context.playerPreferencesDataStore.edit { prefs ->
            prefs.remove(Keys.QUEUE_SONG_IDS)
            prefs.remove(Keys.QUEUE_INDEX)
            prefs.remove(Keys.QUEUE_POSITION_MS)
        }
    }

    suspend fun readPlaybackState(): SavedPlaybackState? {
        val prefs = context.playerPreferencesDataStore.data.first()
        val songIds = prefs[Keys.QUEUE_SONG_IDS]
            ?.split(',')
            ?.mapNotNull(String::toLongOrNull)
            .orEmpty()
        if (songIds.isEmpty()) return null

        return SavedPlaybackState(
            songIds = songIds,
            index = (prefs[Keys.QUEUE_INDEX] ?: 0).coerceIn(0, songIds.lastIndex),
            positionMs = (prefs[Keys.QUEUE_POSITION_MS] ?: 0L).coerceAtLeast(0L),
            shuffleEnabled = prefs[Keys.SHUFFLE_ENABLED] ?: false,
            repeatMode = prefs[Keys.REPEAT_MODE] ?: 0,
        )
    }

    private object Keys {
        val PLAYBACK_SPEED: Preferences.Key<Float> = floatPreferencesKey("playback_speed")
        val LAST_SCAN_AT: Preferences.Key<Long> = longPreferencesKey("last_scan_at")
        val DYNAMIC_COLOR: Preferences.Key<Boolean> = booleanPreferencesKey("dynamic_color")
        val QUEUE_SONG_IDS: Preferences.Key<String> = stringPreferencesKey("queue_song_ids")
        val QUEUE_INDEX: Preferences.Key<Int> = intPreferencesKey("queue_index")
        val QUEUE_POSITION_MS: Preferences.Key<Long> = longPreferencesKey("queue_position_ms")
        val SHUFFLE_ENABLED: Preferences.Key<Boolean> = booleanPreferencesKey("shuffle_enabled")
        val REPEAT_MODE: Preferences.Key<Int> = intPreferencesKey("repeat_mode")
    }
}
