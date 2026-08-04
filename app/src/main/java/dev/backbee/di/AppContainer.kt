package dev.backbee.di

import android.content.Context
import dev.backbee.BuildConfig
import dev.backbee.core.feed.ArchiveFetcher
import dev.backbee.data.db.BackbeeDatabase
import dev.backbee.data.net.Http
import dev.backbee.data.net.HttpFeedFetcher
import dev.backbee.data.net.PodcastDirectory
import dev.backbee.data.prefs.Diagnostics
import dev.backbee.data.prefs.PlaybackStateStore
import dev.backbee.data.prefs.SettingsStore
import dev.backbee.data.repo.PlaybackRepository
import dev.backbee.data.repo.ShowRepository
import dev.backbee.download.DownloadRepository
import dev.backbee.download.EpisodeFiles
import dev.backbee.playback.LocalFileIndex
import dev.backbee.work.WorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient

/**
 * Hand-rolled wiring.
 *
 * A single-user app with no backend and no build variants does not have enough
 * graph to justify a DI framework; a container built once in [dev.backbee.BackbeeApp]
 * is easier to read and adds no build step.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val httpClient: OkHttpClient by lazy { Http.client() }

    val database: BackbeeDatabase by lazy { BackbeeDatabase.build(appContext) }

    val settingsStore: SettingsStore by lazy { SettingsStore(appContext) }

    val playbackStateStore: PlaybackStateStore by lazy { PlaybackStateStore(appContext) }

    val diagnostics: Diagnostics by lazy { Diagnostics(appContext, applicationScope) }

    val episodeFiles: EpisodeFiles by lazy { EpisodeFiles(appContext) }

    val directory: PodcastDirectory by lazy {
        PodcastDirectory(
            client = httpClient,
            // Optional. Without them the app works from feeds alone; with them,
            // truncated archives can be completed from Podcast Index.
            podcastIndexKey = BuildConfig.PODCAST_INDEX_KEY.takeIf { it.isNotBlank() },
            podcastIndexSecret = BuildConfig.PODCAST_INDEX_SECRET.takeIf { it.isNotBlank() },
        )
    }

    val archiveFetcher: ArchiveFetcher by lazy { ArchiveFetcher(HttpFeedFetcher(httpClient)) }

    val showRepository: ShowRepository by lazy { ShowRepository(database, archiveFetcher, directory) }

    val playbackRepository: PlaybackRepository by lazy { PlaybackRepository(database) }

    val downloadRepository: DownloadRepository by lazy {
        DownloadRepository(database, episodeFiles, settingsStore)
    }

    val localFileIndex: LocalFileIndex by lazy {
        LocalFileIndex(applicationScope, downloadRepository)
    }

    val workScheduler: WorkScheduler by lazy { WorkScheduler(appContext) }
}
