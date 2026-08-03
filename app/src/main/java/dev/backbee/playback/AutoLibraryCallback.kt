package dev.backbee.playback

import androidx.media3.common.MediaItem
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dev.backbee.data.repo.PlaybackRepository
import dev.backbee.data.repo.ShowRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.guava.future

/**
 * What Android Auto shows.
 *
 * Three entries, because a car screen at arm's length is not a place to browse
 * 1,247 episodes: Resume (the bookmark), Next up (the following few), and
 * Starred. Everything else lives on the phone.
 *
 * Note for a sideloaded build: Auto only lists media apps installed from Play by
 * default. Enable Developer settings -> "Unknown sources" in the Android Auto
 * app or none of this will appear.
 */
class AutoLibraryCallback(
    private val scope: CoroutineScope,
    private val shows: ShowRepository,
    private val playback: PlaybackRepository,
    private val coordinator: () -> PlaybackCoordinator?,
) : MediaLibrarySession.Callback {

    override fun onGetLibraryRoot(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<MediaItem>> = Futures.immediateFuture(
        LibraryResult.ofItem(MediaItems.browsableItem(ROOT, "backbee"), params)
    )

    override fun onGetChildren(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future {
        val show = shows.activeShow()
            ?: return@future LibraryResult.ofItemList(ImmutableList.of<MediaItem>(), params)

        val items: List<MediaItem> = when (parentId) {
            ROOT -> listOf(
                MediaItems.browsableItem(RESUME, "Resume", show.title),
                MediaItems.browsableItem(UP_NEXT, "Next up", show.title),
                MediaItems.browsableItem(STARRED, "Starred", show.title),
            )

            RESUME -> listOfNotNull(
                playback.resumeTarget(show.id)?.let { MediaItems.forEpisode(it, show) }
            )

            UP_NEXT -> {
                val from = playback.resumeTarget(show.id)?.orderIndex ?: 0
                playback.queueWindow(show.id, from, AUTO_LIST_LIMIT)
                    .map { MediaItems.forEpisode(it, show) }
            }

            STARRED -> playback.starredEpisodes(show.id, AUTO_LIST_LIMIT)
                .map { MediaItems.forEpisode(it, show) }

            else -> emptyList()
        }

        LibraryResult.ofItemList(ImmutableList.copyOf(items), params)
    }

    override fun onGetItem(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String,
    ): ListenableFuture<LibraryResult<MediaItem>> = scope.future {
        val episodeId = mediaId.toLongOrNull()
            ?: return@future LibraryResult.ofError<MediaItem>(SessionResult.RESULT_ERROR_BAD_VALUE)
        val row = playback.getRow(episodeId)
            ?: return@future LibraryResult.ofError<MediaItem>(SessionResult.RESULT_ERROR_BAD_VALUE)
        val show = shows.getShow(row.showId)
        LibraryResult.ofItem(MediaItems.forEpisode(row, show), null)
    }

    /**
     * Auto hands back a [MediaItem] carrying only the media id it was given, so
     * the URI has to be filled in here before the player can do anything with it.
     */
    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>,
    ): ListenableFuture<MutableList<MediaItem>> = scope.future {
        val resolved = mediaItems.mapNotNull { item ->
            if (item.localConfiguration != null) return@mapNotNull item
            val episodeId = item.mediaId.toLongOrNull() ?: return@mapNotNull null
            val row = playback.getRow(episodeId) ?: return@mapNotNull null
            MediaItems.forEpisode(row, shows.getShow(row.showId))
        }
        resolved.toMutableList()
    }

    /**
     * Every request to start playing lands here: Auto, the widget, and the app's
     * own Play button, which all send a bare media id.
     *
     * The plan is *returned* rather than pushed onto the player, because the
     * session applies whatever this future resolves to - setting the queue here
     * as well would have the session immediately overwrite it. Routing through
     * the coordinator is what keeps smart resume, per-show speed and the rolling
     * window identical in the car and on the phone.
     */
    override fun onSetMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = scope.future {
        val coordinator = coordinator()
        val requestedId = mediaItems.getOrNull(startIndex.coerceAtLeast(0))?.mediaId
            ?: mediaItems.firstOrNull()?.mediaId

        val plan = when {
            coordinator == null -> null
            // RESUME as a media id means "wherever the bookmark is", which is
            // what a browsable "Resume" node resolves to when played directly.
            requestedId == null || requestedId == RESUME -> coordinator.planResume()
            else -> requestedId.toLongOrNull()?.let { coordinator.planEpisode(it) } ?: coordinator.planResume()
        }

        if (plan == null) {
            MediaSession.MediaItemsWithStartPosition(mediaItems, startIndex, startPositionMs)
        } else {
            MediaSession.MediaItemsWithStartPosition(plan.items, plan.startIndex, plan.startPositionMs)
        }
    }

    companion object {
        const val ROOT = "root"
        const val RESUME = "resume"
        const val UP_NEXT = "up_next"
        const val STARRED = "starred"

        private const val AUTO_LIST_LIMIT = 25
    }
}
