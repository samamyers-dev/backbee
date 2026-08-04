package dev.backbee.widget

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dev.backbee.playback.AutoLibraryCallback
import dev.backbee.playback.PlaybackService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Widget taps, expressed as ordinary media-session commands.
 *
 * Connecting a short-lived controller keeps the widget on exactly the same path
 * as the notification and Android Auto - there is no second way to start
 * playback that could drift out of step with smart resume.
 */
object WidgetPlaybackCommands {

    suspend fun toggle(context: Context) = withController(context) { controller ->
        when {
            controller.isPlaying -> controller.pause()
            controller.mediaItemCount > 0 -> controller.play()
            else -> {
                // Cold start from the home screen: ask for where you left off and let
                // the service work out which episode that is.
                controller.setMediaItem(
                    MediaItem.Builder().setMediaId(AutoLibraryCallback.RESUME).build()
                )
                controller.prepare()
                controller.play()
            }
        }
    }

    suspend fun skipForward(context: Context) = withController(context) { it.seekForward() }

    // Glance runs actions off the main thread, but MediaController must be built
    // and driven on it.
    private suspend fun withController(context: Context, block: (MediaController) -> Unit) =
        withContext(Dispatchers.Main) {
            val token = SessionToken(
                context.applicationContext,
                ComponentName(context.applicationContext, PlaybackService::class.java),
            )
            val controller = withTimeoutOrNull(CONNECT_TIMEOUT_MS) {
                runCatching {
                    MediaController.Builder(context.applicationContext, token).buildAsync().await()
                }.getOrNull()
            } ?: return@withContext

            try {
                block(controller)
            } finally {
                // The widget has no lifecycle to hang a connection on, so it
                // always hands the controller straight back.
                controller.release()
            }
        }

    private const val CONNECT_TIMEOUT_MS = 5_000L
}
