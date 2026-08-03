package dev.backbee

import android.app.Application
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
            val settings = container.settingsStore.settings.first()

            // Everything below is idempotent - re-registering a unique periodic
            // job just refreshes its constraints, which is exactly what we want
            // after a settings change or an app update.
            container.workScheduler.scheduleDailyRefresh(settings.wifiOnlyDownloads)
            container.workScheduler.scheduleNightlyBackup()
            container.workScheduler.requestDownloadAhead(settings.wifiOnlyDownloads)
        }
    }
}
