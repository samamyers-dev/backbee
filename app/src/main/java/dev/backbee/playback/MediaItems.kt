package dev.backbee.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import dev.backbee.data.db.EpisodeRow
import dev.backbee.data.db.ShowEntity

/**
 * Translation between archive rows and the media objects the player, the
 * notification, the lock screen and Android Auto all read.
 */
object MediaItems {

    private const val EXTRA_ORDER_INDEX = "backbee.orderIndex"
    private const val EXTRA_SHOW_ID = "backbee.showId"

    fun forEpisode(row: EpisodeRow, show: ShowEntity?): MediaItem {
        val extras = Bundle().apply {
            putInt(EXTRA_ORDER_INDEX, row.orderIndex)
            putLong(EXTRA_SHOW_ID, row.showId)
        }

        val metadata = MediaMetadata.Builder()
            .setTitle(row.title)
            .setArtist(show?.title)
            .setAlbumTitle(show?.title)
            // Auto and the lock screen both show this; the episode's position in
            // the archive is the single most useful thing to see at a glance.
            .setSubtitle(subtitleFor(row))
            .setDisplayTitle(row.title)
            .setArtworkUri(show?.artworkUrl?.let(Uri::parse))
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
            .setTrackNumber(row.orderIndex + 1)
            .setExtras(extras)
            .build()

        return MediaItem.Builder()
            .setMediaId(row.id.toString())
            .setUri(row.enclosureUrl)
            .setMediaMetadata(metadata)
            .build()
    }

    fun browsableItem(
        mediaId: String,
        title: String,
        subtitle: String? = null,
        artworkUri: Uri? = null,
    ): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setArtworkUri(artworkUri)
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS)
            .build()

        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setMediaMetadata(metadata)
            .build()
    }

    fun episodeIdOf(item: MediaItem?): Long? = item?.mediaId?.toLongOrNull()

    fun orderIndexOf(item: MediaItem?): Int? =
        item?.mediaMetadata?.extras?.getInt(EXTRA_ORDER_INDEX, -1)?.takeIf { it >= 0 }

    private fun subtitleFor(row: EpisodeRow): String {
        val number = row.episodeNumber ?: (row.orderIndex + 1)
        return "Ep $number"
    }
}
