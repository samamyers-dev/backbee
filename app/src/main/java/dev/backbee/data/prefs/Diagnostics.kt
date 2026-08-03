package dev.backbee.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

data class DiagnosticsSnapshot(
    val positionFlushesToday: Int = 0,
    val lastCheckpointAtMillis: Long? = null,
    val lastCheckpointResult: String? = null,
    val lastFeedRefreshAtMillis: Long? = null,
)

/**
 * The numbers behind the settings readout: `> POSITION FLUSHES TODAY: 411`.
 *
 * They exist so the machinery is inspectable. "Is my position actually being
 * written?" and "did the checkpoint reach the server?" are the two questions
 * worth being able to answer without a debugger, given what the app is for.
 */
class Diagnostics(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private object Keys {
        val flushCount = intPreferencesKey("diag_flush_count")
        val flushDay = longPreferencesKey("diag_flush_day")
        val lastCheckpointAt = longPreferencesKey("diag_checkpoint_at")
        val lastCheckpointResult = stringPreferencesKey("diag_checkpoint_result")
        val lastFeedRefreshAt = longPreferencesKey("diag_feed_refresh_at")
    }

    // Position flushes every five seconds during playback. Persisting each one
    // would be a DataStore write every five seconds forever, so the count is
    // held in memory and flushed to disk periodically.
    private val pending = AtomicInteger(0)

    val snapshot: Flow<DiagnosticsSnapshot> = context.settingsDataStore.data.map { prefs ->
        val today = dayIndex(System.currentTimeMillis())
        val storedDay = prefs[Keys.flushDay] ?: today
        DiagnosticsSnapshot(
            // A stale day means yesterday's count; report zero rather than a
            // number that silently means something else.
            positionFlushesToday = if (storedDay == today) prefs[Keys.flushCount] ?: 0 else 0,
            lastCheckpointAtMillis = prefs[Keys.lastCheckpointAt],
            lastCheckpointResult = prefs[Keys.lastCheckpointResult],
            lastFeedRefreshAtMillis = prefs[Keys.lastFeedRefreshAt],
        )
    }

    /** Called on every position write. Cheap: usually just an in-memory increment. */
    fun recordPositionFlush() {
        if (pending.incrementAndGet() < PERSIST_EVERY) return
        val toAdd = pending.getAndSet(0)
        if (toAdd <= 0) return
        scope.launch {
            runCatching {
                context.settingsDataStore.edit { prefs ->
                    val today = dayIndex(System.currentTimeMillis())
                    val sameDay = prefs[Keys.flushDay] == today
                    prefs[Keys.flushDay] = today
                    prefs[Keys.flushCount] = (if (sameDay) prefs[Keys.flushCount] ?: 0 else 0) + toAdd
                }
            }
        }
    }

    suspend fun recordCheckpoint(at: Long, result: String) {
        runCatching {
            context.settingsDataStore.edit { prefs ->
                prefs[Keys.lastCheckpointAt] = at
                prefs[Keys.lastCheckpointResult] = result
            }
        }
    }

    suspend fun recordFeedRefresh(at: Long) {
        runCatching {
            context.settingsDataStore.edit { prefs -> prefs[Keys.lastFeedRefreshAt] = at }
        }
    }

    private fun dayIndex(millis: Long): Long = millis / 86_400_000L

    private companion object {
        /** ~1 write per minute during continuous playback. */
        const val PERSIST_EVERY = 12
    }
}
