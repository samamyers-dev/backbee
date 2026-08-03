package dev.backbee.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "shows",
    indices = [Index(value = ["rss_url"], unique = true)],
)
data class ShowEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "rss_url") val rssUrl: String,
    val title: String,
    @ColumnInfo(name = "artwork_url") val artworkUrl: String? = null,
    /** Exactly one show is active. Enforced in [dev.backbee.data.repo.ShowRepository.makeActive]. */
    @ColumnInfo(name = "is_active") val isActive: Boolean = false,
    val speed: Float = 1.0f,
    @ColumnInfo(name = "skip_intro_s") val skipIntroSeconds: Int = 0,
    @ColumnInfo(name = "skip_outro_s") val skipOutroSeconds: Int = 0,
    @ColumnInfo(name = "added_at") val addedAt: Long,
    @ColumnInfo(name = "completed_at") val completedAt: Long? = null,
    /** Last successful feed refresh, so the daily check can be cheap and quiet. */
    @ColumnInfo(name = "last_refreshed_at") val lastRefreshedAt: Long? = null,
)

@Entity(
    tableName = "episodes",
    foreignKeys = [
        ForeignKey(
            entity = ShowEntity::class,
            parentColumns = ["id"],
            childColumns = ["show_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["show_id", "guid"], unique = true),
        Index(value = ["show_id", "order_index"]),
    ],
)
data class EpisodeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "show_id") val showId: Long,
    val guid: String,
    /** Position in the archive, oldest-first. Dense and 0-based within a show. */
    @ColumnInfo(name = "order_index") val orderIndex: Int,
    val title: String,
    @ColumnInfo(name = "pub_date") val pubDate: Long?,
    @ColumnInfo(name = "duration_s") val durationSeconds: Long?,
    @ColumnInfo(name = "enclosure_url") val enclosureUrl: String?,
    val description: String? = null,
    @ColumnInfo(name = "episode_number") val episodeNumber: Int? = null,
    @ColumnInfo(name = "enclosure_bytes") val enclosureBytes: Long? = null,
)

/**
 * Split from [EpisodeEntity] on purpose: position is written every few seconds
 * and on every route change, and keeping it in its own narrow row means those
 * writes never touch the wide, rarely-changing episode metadata.
 */
@Entity(
    tableName = "positions",
    foreignKeys = [
        ForeignKey(
            entity = EpisodeEntity::class,
            parentColumns = ["id"],
            childColumns = ["episode_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class PositionEntity(
    @PrimaryKey @ColumnInfo(name = "episode_id") val episodeId: Long,
    @ColumnInfo(name = "position_s") val positionSeconds: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val played: Boolean = false,
    @ColumnInfo(name = "played_at") val playedAt: Long? = null,
)

@Entity(
    tableName = "marks",
    foreignKeys = [
        ForeignKey(
            entity = EpisodeEntity::class,
            parentColumns = ["id"],
            childColumns = ["episode_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class MarkEntity(
    @PrimaryKey @ColumnInfo(name = "episode_id") val episodeId: Long,
    val starred: Boolean = false,
    val note: String? = null,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

enum class DownloadState { QUEUED, RUNNING, DONE, FAILED }

@Entity(
    tableName = "downloads",
    foreignKeys = [
        ForeignKey(
            entity = EpisodeEntity::class,
            parentColumns = ["id"],
            childColumns = ["episode_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class DownloadEntity(
    @PrimaryKey @ColumnInfo(name = "episode_id") val episodeId: Long,
    val state: DownloadState,
    @ColumnInfo(name = "file_path") val filePath: String? = null,
    @ColumnInfo(name = "bytes_total") val bytesTotal: Long = 0,
    @ColumnInfo(name = "bytes_done") val bytesDone: Long = 0,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = 0,
    @ColumnInfo(name = "failure_count") val failureCount: Int = 0,
)
