package dev.backbee.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dev.backbee.download.DownloadAheadWorker
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * All the background scheduling in one place.
 *
 * Everything here is meant to be invisible. Nothing notifies, nothing prompts,
 * and nothing runs while the user would notice it competing with playback.
 */
class WorkScheduler(context: Context) {

    private val workManager = WorkManager.getInstance(context.applicationContext)

    /**
     * Nudge the download engine. Called after playback advances, after settings
     * change, and on app start - it is cheap and idempotent, and REPLACE means a
     * newer picture of "where we are" always wins over an in-flight older one.
     */
    fun requestDownloadAhead(wifiOnly: Boolean) {
        val request = OneTimeWorkRequestBuilder<DownloadAheadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                    .setRequiresStorageNotLow(true)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
            .build()

        workManager.enqueueUniqueWork(
            DownloadAheadWorker.UNIQUE_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun scheduleDailyRefresh(wifiOnly: Boolean) {
        val request = PeriodicWorkRequestBuilder<FeedRefreshWorker>(1, TimeUnit.DAYS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .setInitialDelay(hoursUntil(4), TimeUnit.HOURS)
            .build()

        workManager.enqueueUniquePeriodicWork(
            FeedRefreshWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun scheduleNightlyBackup() {
        val request = PeriodicWorkRequestBuilder<BackupWorker>(1, TimeUnit.DAYS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .setRequiresStorageNotLow(true)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS)
            .setInitialDelay(hoursUntil(3), TimeUnit.HOURS)
            .build()

        workManager.enqueueUniquePeriodicWork(
            BackupWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun backupNow() {
        workManager.enqueueUniqueWork(
            "${BackupWorker.UNIQUE_NAME}-now",
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<BackupWorker>().build(),
        )
    }

    fun refreshNow() {
        workManager.enqueueUniqueWork(
            "${FeedRefreshWorker.UNIQUE_NAME}-now",
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<FeedRefreshWorker>().build(),
        )
    }

    /** Hours from now until the next occurrence of [targetHour] local time. */
    private fun hoursUntil(targetHour: Int): Long {
        val now = Calendar.getInstance()
        val currentHour = now.get(Calendar.HOUR_OF_DAY)
        val delta = targetHour - currentHour
        return if (delta > 0) delta.toLong() else (delta + 24).toLong()
    }
}
