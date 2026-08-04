package dev.backbee.playback

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import dev.backbee.BackbeeApp
import dev.backbee.data.net.Http
import dev.backbee.ui.MainActivity
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The service that actually plays things, and the app's real daily surface: the
 * media notification, the lock screen, and Android Auto all talk to this.
 *
 * A MediaLibraryService rather than a plain MediaSessionService because the
 * library callbacks are what Auto browses.
 */
class PlaybackService : MediaLibraryService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var exoPlayer: ExoPlayer
    private lateinit var player: SkipForwardingPlayer
    private lateinit var positionWriter: PositionWriter
    private lateinit var coordinator: PlaybackCoordinator
    private lateinit var routeMonitor: AudioRouteMonitor
    private var session: MediaLibrarySession? = null

    override fun onCreate() {
        super.onCreate()
        val container = (application as BackbeeApp).container

        exoPlayer = buildPlayer(container)
        player = SkipForwardingPlayer(exoPlayer)

        positionWriter = PositionWriter(
            scope = scope,
            repository = container.playbackRepository,
            onEpisodeFinished = { container.workScheduler.requestDownloadAhead(container.settingsStore.current().wifiOnlyDownloads) },
            onFlushed = { container.diagnostics.recordPositionFlush() },
            onLoadedEpisodeChanged = { container.playbackStateStore.setLoadedEpisode(it) },
        ).also { it.attach(player) }

        coordinator = PlaybackCoordinator(
            scope = scope,
            player = player,
            shows = container.showRepository,
            playback = container.playbackRepository,
            settingsStore = container.settingsStore,
            onDownloadNudge = {
                scope.launch {
                    container.workScheduler.requestDownloadAhead(container.settingsStore.current().wifiOnlyDownloads)
                }
            },
            onArchiveCompleted = { showId -> Log.i(TAG, "Archive $showId complete") },
        )

        session = MediaLibrarySession.Builder(
            this,
            player,
            AutoLibraryCallback(
                scope = scope,
                shows = container.showRepository,
                playback = container.playbackRepository,
                coordinator = { coordinator },
            ),
        )
            .setSessionActivity(openAppIntent())
            .build()

        routeMonitor = AudioRouteMonitor(
            context = this,
            // The single most common way to lose a position: the car turns off,
            // Bluetooth drops, and the process dies before any pause callback
            // would have run.
            onRouteLost = { positionWriter.flush("audio route lost") },
            onBluetoothConnected = {
                scope.launch {
                    if (container.settingsStore.current().autoPlayOnBluetooth && !player.isPlaying) {
                        coordinator.resumeActiveShow()
                    }
                }
            },
        ).also { it.start() }
    }

    private fun buildPlayer(container: dev.backbee.di.AppContainer): ExoPlayer {
        val httpFactory = OkHttpDataSource.Factory(container.httpClient)
            .setUserAgent(Http.USER_AGENT)

        // Swaps in the downloaded file when there is one. Doing it at open time
        // rather than when the queue is built means an episode that finished
        // downloading while an earlier one played is still played from disk.
        val resolvingFactory = ResolvingDataSource.Factory(
            DefaultDataSource.Factory(this, httpFactory),
            ResolvingDataSource.Resolver { dataSpec ->
                val local = container.localFileIndex.pathForUrl(dataSpec.uri.toString())
                if (local != null) dataSpec.withUri(android.net.Uri.fromFile(File(local))) else dataSpec
            },
        )

        return ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(resolvingFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            // Podcast audio is long and often streamed over patchy mobile data;
            // a generous buffer is worth the memory in a car.
            .setSeekForwardIncrementMs(30_000)
            .setSeekBackIncrementMs(10_000)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Swiping the app away should not silently drop the position, and should
        // not leave a paused service lingering either.
        positionWriter.flush("task removed")
        if (!player.isPlaying) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        positionWriter.flush("service destroyed")
        routeMonitor.stop()
        coordinator.release()
        positionWriter.detach()
        session?.release()
        session = null
        player.release()
        scope.cancel()
        super.onDestroy()
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        private const val TAG = "PlaybackService"
    }
}
