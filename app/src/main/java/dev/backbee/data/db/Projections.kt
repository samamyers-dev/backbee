package dev.backbee.data.db

import androidx.room.ColumnInfo

/**
 * One archive row, everything the list needs in a single query.
 *
 * The archive is the spine of the app and can be 1,500 rows long, so it is read
 * as a flat projection rather than as relations Room would have to stitch back
 * together per item.
 */
data class EpisodeRow(
    val id: Long,
    @ColumnInfo(name = "show_id") val showId: Long,
    @ColumnInfo(name = "order_index") val orderIndex: Int,
    val title: String,
    @ColumnInfo(name = "pub_date") val pubDate: Long?,
    @ColumnInfo(name = "duration_s") val durationSeconds: Long?,
    @ColumnInfo(name = "enclosure_url") val enclosureUrl: String?,
    @ColumnInfo(name = "episode_number") val episodeNumber: Int?,
    @ColumnInfo(name = "enclosure_bytes") val enclosureBytes: Long?,
    @ColumnInfo(name = "position_s") val positionSeconds: Long?,
    val played: Boolean?,
    @ColumnInfo(name = "played_at") val playedAt: Long?,
    val starred: Boolean?,
    val note: String?,
    @ColumnInfo(name = "keep_after_playing") val keepAfterPlaying: Boolean?,
    @ColumnInfo(name = "download_state") val downloadState: DownloadState?,
    @ColumnInfo(name = "file_path") val filePath: String?,
    @ColumnInfo(name = "bytes_total") val bytesTotal: Long?,
    @ColumnInfo(name = "bytes_done") val bytesDone: Long?,
) {
    val isPlayed: Boolean get() = played == true
    val isStarred: Boolean get() = starred == true
    val isKept: Boolean get() = keepAfterPlaying == true
    val isDownloaded: Boolean get() = downloadState == DownloadState.DONE && filePath != null

    /** 0f..1f, or null when the episode has not been started or has no known duration. */
    val progressFraction: Float?
        get() {
            val pos = positionSeconds ?: return null
            val duration = durationSeconds ?: return null
            if (duration <= 0 || pos <= 0) return null
            return (pos.toFloat() / duration).coerceIn(0f, 1f)
        }
}

/** Aggregate counters for the Now screen and the Shelf, computed in SQL. */
data class ShowProgress(
    @ColumnInfo(name = "show_id") val showId: Long,
    @ColumnInfo(name = "total_episodes") val totalEpisodes: Int,
    @ColumnInfo(name = "played_episodes") val playedEpisodes: Int,
    @ColumnInfo(name = "total_seconds") val totalSeconds: Long,
    @ColumnInfo(name = "remaining_seconds") val remainingSeconds: Long,
    @ColumnInfo(name = "starred_count") val starredCount: Int,
)

/** What the completion screen needs, in one pass over the show's episodes. */
data class ListeningTotals(
    @ColumnInfo(name = "episode_count") val episodeCount: Int,
    @ColumnInfo(name = "listened_seconds") val listenedSeconds: Long,
    @ColumnInfo(name = "first_played_at") val firstPlayedAt: Long?,
    @ColumnInfo(name = "last_played_at") val lastPlayedAt: Long?,
    @ColumnInfo(name = "starred_count") val starredCount: Int,
)

/** Minimal shape the download planner needs; keeps the planner free of Room types. */
data class DownloadSlotRow(
    @ColumnInfo(name = "episode_id") val episodeId: Long,
    @ColumnInfo(name = "order_index") val orderIndex: Int,
    val played: Boolean?,
    @ColumnInfo(name = "played_at") val playedAt: Long?,
    @ColumnInfo(name = "download_state") val downloadState: DownloadState?,
    @ColumnInfo(name = "keep_after_playing") val keepAfterPlaying: Boolean?,
    @ColumnInfo(name = "bytes_total") val bytesTotal: Long?,
    @ColumnInfo(name = "enclosure_bytes") val enclosureBytes: Long?,
    @ColumnInfo(name = "duration_s") val durationSeconds: Long?,
    @ColumnInfo(name = "enclosure_url") val enclosureUrl: String?,
    @ColumnInfo(name = "file_path") val filePath: String?,
)

/**
 * Where an episode's audio lives, keyed both ways.
 *
 * The player's data-source resolver only ever sees a URI, so the remote URL has
 * to be part of this index for a download that finishes mid-queue to be picked
 * up instead of streamed.
 */
data class LocalFileRow(
    @ColumnInfo(name = "episode_id") val episodeId: Long,
    @ColumnInfo(name = "file_path") val filePath: String,
    @ColumnInfo(name = "enclosure_url") val enclosureUrl: String?,
)

/** A publication year and where in the archive it starts, for the spine and the scrubber. */
data class YearMarkRow(
    val year: Int,
    @ColumnInfo(name = "order_index") val orderIndex: Int,
)
