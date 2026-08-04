package dev.backbee.playback

import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import dev.backbee.core.playback.SmartResume
import dev.backbee.data.db.EpisodeRow
import dev.backbee.data.db.ShowEntity
import dev.backbee.data.prefs.Settings
import dev.backbee.data.prefs.SettingsStore
import dev.backbee.data.repo.PlaybackRepository
import dev.backbee.data.repo.ShowRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A queue ready to hand to the player: what to load, where to start. */
data class QueuePlan(
    val items: List<MediaItem>,
    val startIndex: Int,
    val startPositionMs: Long,
)

/**
 * Everything about *what* plays next, as opposed to how it is decoded.
 *
 * The archive order is the queue - there is no queue management in v1 - so this
 * keeps a rolling window of the next few episodes loaded and extends it as
 * playback advances. A window rather than the whole 1,500-episode archive keeps
 * memory flat while still giving ExoPlayer real gapless auto-advance.
 *
 * Building a plan and applying it are separate on purpose. Media3's session
 * callbacks want the queue *returned* so they can apply it themselves; applying
 * it here as well would have the session overwrite what we just set.
 */
class PlaybackCoordinator(
    private val scope: CoroutineScope,
    private val player: SkipForwardingPlayer,
    private val shows: ShowRepository,
    private val playback: PlaybackRepository,
    private val settingsStore: SettingsStore,
    private val onDownloadNudge: () -> Unit,
    private val onArchiveCompleted: (showId: Long) -> Unit,
) : Player.Listener {

    private var outroWatcher: Job? = null

    @Volatile
    private var activeShow: ShowEntity? = null

    init {
        player.addListener(this)
        scope.launch { settingsStore.settings.collect { applySettings(it) } }
    }

    // -- Planning -----------------------------------------------------------

    /**
     * The queue for resuming the active show where it was left off. This is what the
     * giant Play button, the widget, the headset button and Auto's "Resume" all
     * ultimately ask for.
     */
    suspend fun planResume(): QueuePlan? {
        val show = shows.activeShow() ?: return null
        val target = playback.resumeTarget(show.id) ?: run {
            Log.i(TAG, "Nothing left to play in ${show.title}")
            onArchiveCompleted(show.id)
            return null
        }
        return planFor(show, target)
    }

    /** The queue for a specific episode, e.g. tapped in the archive or in Auto. */
    suspend fun planEpisode(episodeId: Long): QueuePlan? {
        val row = playback.getRow(episodeId) ?: return null
        val show = shows.getShow(row.showId) ?: return null
        if (!show.isActive) shows.makeActive(show.id)
        return planFor(show, row)
    }

    private suspend fun planFor(show: ShowEntity, target: EpisodeRow): QueuePlan? {
        activeShow = show
        val settings = settingsStore.current()
        val window = playback.queueWindow(show.id, target.orderIndex, WINDOW_SIZE)
        if (window.isEmpty()) return null

        // Per-show speed and the skip increments are player properties rather
        // than queue contents, so they are safe to apply here either way.
        withContext(Dispatchers.Main) {
            player.setPlaybackSpeed(show.speed)
            player.skipIncrementsFrom(settings)
        }

        return QueuePlan(
            items = window.map { MediaItems.forEpisode(it, show) },
            startIndex = 0,
            startPositionMs = startPositionFor(target, show, settings),
        )
    }

    /**
     * Where to actually start.
     *
     * A fresh episode starts past the intro if the show has one configured. A
     * part-heard episode starts at its saved position, rewound by an amount scaled to
     * how long the pause was.
     */
    private suspend fun startPositionFor(row: EpisodeRow, show: ShowEntity, settings: Settings): Long {
        val saved = row.positionSeconds ?: 0
        if (saved <= 0) return show.skipIntroSeconds.coerceAtLeast(0) * 1000L

        val pausedForSeconds = playback.position(row.id)?.updatedAt
            ?.let { (System.currentTimeMillis() - it) / 1000 }
            ?.coerceAtLeast(0)
            ?: 0

        val resumeAt = SmartResume.resumePositionSeconds(saved, pausedForSeconds, settings.resumeTiers)
        Log.d(TAG, "resume episode=${row.id} saved=${saved}s away=${pausedForSeconds}s -> ${resumeAt}s")
        return resumeAt * 1000
    }

    // -- Applying -----------------------------------------------------------

    /** Loads a plan into the player. Used by callers that own the player directly. */
    suspend fun apply(plan: QueuePlan, startPlaying: Boolean = true) {
        withContext(Dispatchers.Main) {
            player.setMediaItems(plan.items, plan.startIndex, plan.startPositionMs)
            player.prepare()
            if (startPlaying) player.play()
        }
        onDownloadNudge()
    }

    suspend fun resumeActiveShow(startPlaying: Boolean = true) {
        planResume()?.let { apply(it, startPlaying) }
    }

    suspend fun playEpisode(episodeId: Long, startPlaying: Boolean = true) {
        planEpisode(episodeId)?.let { apply(it, startPlaying) }
    }

    // -- Player.Listener ----------------------------------------------------

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        // Auto-advance just moved us along; top the window back up and let the
        // download engine re-plan around the new position.
        scope.launch { extendWindowIfNeeded() }
        onDownloadNudge()
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) startOutroWatcher() else stopOutroWatcher()
    }

    // -- Window management --------------------------------------------------

    private suspend fun extendWindowIfNeeded() {
        val show = activeShow ?: shows.activeShow()?.also { activeShow = it } ?: return

        val (index, count, lastItem) = withContext(Dispatchers.Main) {
            Triple(player.currentMediaItemIndex, player.mediaItemCount, player.lastMediaItem())
        }
        if (count - index > EXTEND_THRESHOLD) return

        val lastOrderIndex = MediaItems.orderIndexOf(lastItem) ?: return
        val more = playback.queueWindow(show.id, lastOrderIndex + 1, WINDOW_SIZE)
        if (more.isEmpty()) return

        val items = more.map { MediaItems.forEpisode(it, show) }
        withContext(Dispatchers.Main) { player.addMediaItems(items) }
        Log.d(TAG, "Extended queue with ${items.size} episode(s)")
    }

    // -- Outro skipping -----------------------------------------------------

    /**
     * Ends the episode early when the show has a known outro length. Advancing a
     * few seconds sooner is better than sitting through the same credits 1,200
     * times.
     */
    private fun startOutroWatcher() {
        if (outroWatcher?.isActive == true) return
        outroWatcher = scope.launch {
            while (isActive) {
                delay(OUTRO_CHECK_INTERVAL_MS)
                val outroSeconds = activeShow?.skipOutroSeconds ?: 0
                if (outroSeconds <= 0) continue

                withContext(Dispatchers.Main) {
                    val duration = player.duration
                    val position = player.currentPosition
                    if (duration > 0 && position > 0 && duration - position <= outroSeconds * 1000L) {
                        if (player.hasNextMediaItem()) player.seekToNextMediaItem() else player.seekTo(duration)
                    }
                }
            }
        }
    }

    private fun stopOutroWatcher() {
        outroWatcher?.cancel()
        outroWatcher = null
    }

    // -- Settings -----------------------------------------------------------

    private suspend fun applySettings(settings: Settings) {
        withContext(Dispatchers.Main) { player.skipIncrementsFrom(settings) }
    }

    /** Called when per-show speed changes while that show is playing. */
    suspend fun applyShowSettings(show: ShowEntity) {
        activeShow = show
        withContext(Dispatchers.Main) { player.setPlaybackSpeed(show.speed) }
    }

    fun release() {
        stopOutroWatcher()
        player.removeListener(this)
    }

    private fun SkipForwardingPlayer.skipIncrementsFrom(settings: Settings) {
        forwardMs = settings.skipForwardSeconds * 1000L
        backMs = settings.skipBackSeconds * 1000L
    }

    private fun Player.lastMediaItem(): MediaItem? =
        if (mediaItemCount == 0) null else getMediaItemAt(mediaItemCount - 1)

    companion object {
        private const val TAG = "PlaybackCoordinator"

        /** Episodes kept loaded in the player at once. */
        private const val WINDOW_SIZE = 25

        /** Top the window up once this few remain. */
        private const val EXTEND_THRESHOLD = 5

        private const val OUTRO_CHECK_INTERVAL_MS = 1_000L
    }
}
