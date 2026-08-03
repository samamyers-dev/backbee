package dev.backbee.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.backbee.BackbeeApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The quiet daily check.
 *
 * No notifications, by design: an archive listener is 300 episodes behind and
 * does not need to be told a new one arrived. New episodes simply append to the
 * end of the archive, where they will be reached in a year or two.
 */
class FeedRefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val container = (applicationContext as BackbeeApp).container
        val shows = container.database.showDao().getAll()

        var failures = 0
        for (show in shows) {
            // A completed show has nothing to append to that matters; skip the
            // network call entirely unless it is the active one.
            if (show.completedAt != null && !show.isActive) continue
            try {
                val result = container.showRepository.refresh(show.id)
                if (result.added > 0) {
                    Log.i(TAG, "${show.title}: +${result.added} episode(s)")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Refresh failed for ${show.title}", e)
                failures++
            }
        }

        if (failures > 0) Result.retry() else Result.success()
    }

    companion object {
        private const val TAG = "FeedRefresh"
        const val UNIQUE_NAME = "feed-refresh"
    }
}
