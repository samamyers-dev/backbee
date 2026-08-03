package dev.backbee.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** What the UI needs to draw a player, refreshed as the session changes. */
data class PlayerState(
    val connected: Boolean = false,
    val isPlaying: Boolean = false,
    val episodeId: Long? = null,
    val title: String? = null,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val speed: Float = 1f,
) {
    val positionSeconds: Long get() = positionMs / 1000
    val durationSeconds: Long get() = durationMs / 1000
    val remainingMs: Long get() = (durationMs - positionMs).coerceAtLeast(0)
}

/**
 * The UI's handle on the playback service.
 *
 * The service is the source of truth and can outlive any Activity, so the UI
 * connects to it as a client rather than holding a player of its own. Commands
 * go out as ordinary media-session calls, which means the notification, the
 * widget, Auto and the app screens all drive exactly the same code path.
 */
class PlayerConnection(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var controller: MediaController? = null
    private var ticker: kotlinx.coroutines.Job? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publish()
    }

    fun connect() {
        if (controller != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            {
                controller = runCatching { future.get() }.getOrNull()?.also { it.addListener(listener) }
                publish()
                startTicker()
            },
            MoreExecutors.directExecutor(),
        )
    }

    fun release() {
        ticker?.cancel()
        ticker = null
        controller?.removeListener(listener)
        controller?.release()
        controller = null
        _state.value = PlayerState()
    }

    // -- Commands -----------------------------------------------------------

    /**
     * Start or continue the archive. Sending a bare media id lets the service's
     * session callback build the real queue, so smart resume and per-show speed
     * apply no matter which surface asked.
     */
    fun playEpisode(episodeId: Long) {
        val controller = controller ?: return
        controller.setMediaItem(MediaItem.Builder().setMediaId(episodeId.toString()).build())
        controller.prepare()
        controller.play()
    }

    fun resume() {
        val controller = controller ?: return
        if (controller.mediaItemCount > 0) {
            controller.play()
        } else {
            controller.setMediaItem(MediaItem.Builder().setMediaId(AutoLibraryCallback.RESUME).build())
            controller.prepare()
            controller.play()
        }
    }

    fun pause() {
        controller?.pause()
    }

    fun togglePlayPause() {
        val controller = controller ?: return
        if (controller.isPlaying) controller.pause() else resume()
    }

    fun skipForward() {
        controller?.seekForward()
    }

    fun skipBack() {
        controller?.seekBack()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs.coerceAtLeast(0))
    }

    fun setSpeed(speed: Float) {
        controller?.setPlaybackSpeed(speed.coerceIn(0.5f, 4f))
    }

    fun next() {
        controller?.seekToNextMediaItem()
    }

    // -- State --------------------------------------------------------------

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                publish()
                // A second is enough for a progress bar and cheap enough to run
                // while the screen is on. Position durability is the service's
                // job, not this one's.
                kotlinx.coroutines.delay(1_000)
            }
        }
    }

    private fun publish() {
        val controller = controller
        if (controller == null) {
            _state.value = PlayerState()
            return
        }
        _state.value = PlayerState(
            connected = true,
            isPlaying = controller.isPlaying,
            episodeId = MediaItems.episodeIdOf(controller.currentMediaItem),
            title = controller.mediaMetadata.title?.toString(),
            positionMs = controller.currentPosition.coerceAtLeast(0),
            durationMs = controller.duration.takeIf { it > 0 } ?: 0,
            speed = controller.playbackParameters.speed,
        )
    }
}
