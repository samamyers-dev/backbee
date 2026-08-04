package dev.backbee.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * The little bit of player state that outlives the player.
 *
 * The download worker runs on WorkManager's schedule, long after the service
 * that knew what was loaded may have been torn down, so "what is in the player"
 * has to be written down somewhere the worker can read it. Not a user setting,
 * but it shares the DataStore rather than earning a file of its own.
 */
class PlaybackStateStore(private val context: Context) {

    private object Keys {
        val loadedEpisodeId = longPreferencesKey("loaded_episode_id")
    }

    /** The episode the player currently holds, or null when it holds nothing. */
    val loadedEpisodeId: Flow<Long?> =
        context.settingsDataStore.data.map { it[Keys.loadedEpisodeId] }

    suspend fun currentLoadedEpisodeId(): Long? = loadedEpisodeId.first()

    suspend fun setLoadedEpisode(episodeId: Long?) {
        context.settingsDataStore.edit { prefs ->
            if (episodeId == null) prefs.remove(Keys.loadedEpisodeId)
            else prefs[Keys.loadedEpisodeId] = episodeId
        }
    }
}
