package dev.backbee

import android.app.Application
import android.util.Log
import dev.backbee.di.AppContainer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BackbeeApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        container.applicationScope.launch {
            // Background scheduling must never be able to stop the app starting.
            // Downloads and the nightly checkpoint are conveniences; playing the
            // archive is the product, and it does not depend on any of them.
            runCatching {
                val settings = container.settingsStore.settings.first()

                // Everything below is idempotent - re-registering a unique
                // periodic job just refreshes its constraints, which is exactly
                // what we want after a settings change or an app update.
                container.workScheduler.scheduleDailyRefresh(settings.wifiOnlyDownloads)
                container.workScheduler.scheduleNightlyBackup()
                container.workScheduler.requestDownloadAhead(settings.wifiOnlyDownloads)
            }.onFailure { Log.e(TAG, "Could not schedule background work", it) }
        }
    }

    private companion object {
        const val TAG = "BackbeeApp"
    }
}
