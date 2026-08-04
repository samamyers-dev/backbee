package dev.backbee.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

private const val EPISODE_COLUMNS = """
    e.id AS id,
    e.show_id AS show_id,
    e.order_index AS order_index,
    e.title AS title,
    e.pub_date AS pub_date,
    e.duration_s AS duration_s,
    e.enclosure_url AS enclosure_url,
    e.episode_number AS episode_number,
    e.enclosure_bytes AS enclosure_bytes,
    p.position_s AS position_s,
    p.played AS played,
    p.played_at AS played_at,
    m.starred AS starred,
    m.note AS note,
    m.keep_after_playing AS keep_after_playing,
    d.state AS download_state,
    d.file_path AS file_path,
    d.bytes_total AS bytes_total,
    d.bytes_done AS bytes_done
"""

private const val EPISODE_JOINS = """
    FROM episodes e
    LEFT JOIN positions p ON p.episode_id = e.id
    LEFT JOIN marks m ON m.episode_id = e.id
    LEFT JOIN downloads d ON d.episode_id = e.id
"""

@Dao
interface ShowDao {

    @Query("SELECT * FROM shows ORDER BY is_active DESC, completed_at IS NOT NULL, added_at ASC")
    fun observeAll(): Flow<List<ShowEntity>>

    @Query("SELECT * FROM shows WHERE is_active = 1 LIMIT 1")
    fun observeActive(): Flow<ShowEntity?>

    @Query("SELECT * FROM shows WHERE is_active = 1 LIMIT 1")
    suspend fun getActive(): ShowEntity?

    @Query("SELECT * FROM shows WHERE id = :id")
    suspend fun getById(id: Long): ShowEntity?

    @Query("SELECT * FROM shows WHERE rss_url = :rssUrl")
    suspend fun getByRssUrl(rssUrl: String): ShowEntity?

    @Query("SELECT * FROM shows")
    suspend fun getAll(): List<ShowEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(show: ShowEntity): Long

    @Update
    suspend fun update(show: ShowEntity)

    @Query("DELETE FROM shows WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE shows SET is_active = 0")
    suspend fun clearActive()

    @Query("UPDATE shows SET is_active = 1 WHERE id = :id")
    suspend fun setActive(id: Long)

    /**
     * "One show active at a time" is a product principle, so it is enforced in a
     * transaction rather than left to callers to remember.
     */
    @Transaction
    suspend fun makeActive(id: Long) {
        clearActive()
        setActive(id)
    }

    @Query("UPDATE shows SET completed_at = :completedAt WHERE id = :id")
    suspend fun setCompletedAt(id: Long, completedAt: Long?)

    @Query("UPDATE shows SET last_refreshed_at = :at WHERE id = :id")
    suspend fun setLastRefreshedAt(id: Long, at: Long)

    @Query(
        """
        SELECT :showId AS show_id,
               COUNT(*) AS total_episodes,
               COALESCE(SUM(CASE WHEN p.played = 1 THEN 1 ELSE 0 END), 0) AS played_episodes,
               COALESCE(SUM(COALESCE(e.duration_s, 0)), 0) AS total_seconds,
               COALESCE(SUM(
                   CASE WHEN p.played = 1 THEN 0
                        ELSE MAX(0, COALESCE(e.duration_s, 0) - COALESCE(p.position_s, 0))
                   END), 0) AS remaining_seconds,
               COALESCE(SUM(CASE WHEN m.starred = 1 THEN 1 ELSE 0 END), 0) AS starred_count
        FROM episodes e
        LEFT JOIN positions p ON p.episode_id = e.id
        LEFT JOIN marks m ON m.episode_id = e.id
        WHERE e.show_id = :showId
        """
    )
    fun observeProgress(showId: Long): Flow<ShowProgress?>

    @Query(
        """
        SELECT COUNT(*) AS episode_count,
               COALESCE(SUM(CASE WHEN p.played = 1 THEN COALESCE(e.duration_s, 0) ELSE 0 END), 0) AS listened_seconds,
               MIN(p.played_at) AS first_played_at,
               MAX(p.played_at) AS last_played_at,
               COALESCE(SUM(CASE WHEN m.starred = 1 THEN 1 ELSE 0 END), 0) AS starred_count
        FROM episodes e
        LEFT JOIN positions p ON p.episode_id = e.id
        LEFT JOIN marks m ON m.episode_id = e.id
        WHERE e.show_id = :showId
        """
    )
    suspend fun listeningTotals(showId: Long): ListeningTotals?
}

@Dao
interface EpisodeDao {

    @Query("SELECT $EPISODE_COLUMNS $EPISODE_JOINS WHERE e.show_id = :showId ORDER BY e.order_index ASC")
    fun observeArchive(showId: Long): Flow<List<EpisodeRow>>

    @Query("SELECT $EPISODE_COLUMNS $EPISODE_JOINS WHERE e.id = :episodeId")
    fun observeRow(episodeId: Long): Flow<EpisodeRow?>

    @Query("SELECT $EPISODE_COLUMNS $EPISODE_JOINS WHERE e.id = :episodeId")
    suspend fun getRow(episodeId: Long): EpisodeRow?

    /**
     * Where "Resume" points: the earliest episode that has not been finished.
     * An episode part-way through is by definition the earliest unplayed one, so
     * this covers both "continue" and "start the next one".
     */
    @Query(
        """
        SELECT $EPISODE_COLUMNS $EPISODE_JOINS
        WHERE e.show_id = :showId
          AND (p.played IS NULL OR p.played = 0)
          AND e.enclosure_url IS NOT NULL
        ORDER BY e.order_index ASC
        LIMIT 1
        """
    )
    suspend fun nextUnplayed(showId: Long): EpisodeRow?

    /**
     * The observable twin of [nextUnplayed]. The Now screen follows this rather
     * than scanning the whole archive flow, which on a 1,500-episode show would
     * mean re-reading every row on every position write.
     */
    @Query(
        """
        SELECT $EPISODE_COLUMNS $EPISODE_JOINS
        WHERE e.show_id = :showId
          AND (p.played IS NULL OR p.played = 0)
          AND e.enclosure_url IS NOT NULL
        ORDER BY e.order_index ASC
        LIMIT 1
        """
    )
    fun observeNextUnplayed(showId: Long): Flow<EpisodeRow?>

    /** The look-ahead strip on the Now screen, and the player's queue window. */
    @Query(
        """
        SELECT $EPISODE_COLUMNS $EPISODE_JOINS
        WHERE e.show_id = :showId
          AND e.order_index >= :fromOrderIndex
          AND e.enclosure_url IS NOT NULL
        ORDER BY e.order_index ASC
        LIMIT :limit
        """
    )
    suspend fun fromOrderIndex(showId: Long, fromOrderIndex: Int, limit: Int): List<EpisodeRow>

    @Query(
        """
        SELECT $EPISODE_COLUMNS $EPISODE_JOINS
        WHERE e.show_id = :showId
          AND e.order_index > :afterOrderIndex
          AND (p.played IS NULL OR p.played = 0)
          AND e.enclosure_url IS NOT NULL
        ORDER BY e.order_index ASC
        LIMIT :limit
        """
    )
    fun observeUpNext(showId: Long, afterOrderIndex: Int, limit: Int): Flow<List<EpisodeRow>>

    @Query(
        """
        SELECT $EPISODE_COLUMNS $EPISODE_JOINS
        WHERE e.show_id = :showId AND m.starred = 1
        ORDER BY e.order_index ASC
        """
    )
    fun observeStarred(showId: Long): Flow<List<EpisodeRow>>

    @Query(
        """
        SELECT $EPISODE_COLUMNS $EPISODE_JOINS
        WHERE e.show_id = :showId AND m.starred = 1
        ORDER BY e.order_index ASC
        LIMIT :limit
        """
    )
    suspend fun starred(showId: Long, limit: Int): List<EpisodeRow>

    @Query(
        """
        SELECT $EPISODE_COLUMNS $EPISODE_JOINS
        WHERE e.show_id = :showId
          AND (e.title LIKE '%' || :query || '%' OR e.description LIKE '%' || :query || '%')
        ORDER BY e.order_index ASC
        LIMIT :limit
        """
    )
    fun search(showId: Long, query: String, limit: Int): Flow<List<EpisodeRow>>

    @Query("SELECT description FROM episodes WHERE id = :episodeId")
    suspend fun description(episodeId: Long): String?

    @Query("SELECT id, guid, order_index FROM episodes WHERE show_id = :showId")
    suspend fun identityMap(showId: Long): List<EpisodeIdentity>

    @Query("SELECT COALESCE(MAX(order_index), -1) FROM episodes WHERE show_id = :showId")
    suspend fun maxOrderIndex(showId: Long): Int

    @Query("SELECT COUNT(*) FROM episodes WHERE show_id = :showId")
    suspend fun count(showId: Long): Int

    @Query(
        """
        SELECT COUNT(*) FROM episodes
        WHERE show_id = :showId AND order_index BETWEEN :fromOrderIndex AND :toOrderIndex
        """
    )
    suspend fun countInRange(showId: Long, fromOrderIndex: Int, toOrderIndex: Int): Int

    /** Position of an episode within the archive, used to seed the queue window. */
    @Query("SELECT order_index FROM episodes WHERE id = :episodeId")
    suspend fun orderIndexOf(episodeId: Long): Int?

    @Query("SELECT id FROM episodes WHERE show_id = :showId AND episode_number = :number LIMIT 1")
    suspend fun findByEpisodeNumber(showId: Long, number: Int): Long?

    @Query("SELECT id FROM episodes WHERE show_id = :showId AND order_index = :orderIndex LIMIT 1")
    suspend fun findByOrderIndex(showId: Long, orderIndex: Int): Long?

    /** Distinct years present in the archive, for the fast-scroll scrubber. */
    @Query(
        """
        SELECT MIN(order_index) FROM episodes
        WHERE show_id = :showId AND pub_date IS NOT NULL AND pub_date >= :fromMillis
        """
    )
    suspend fun firstOrderIndexOnOrAfter(showId: Long, fromMillis: Long): Int?

    /**
     * One row per publication year with the archive position it starts at.
     * Grouping in SQL keeps this ~10 rows instead of streaming 1,500 dates into
     * memory every time the Now screen recomposes.
     */
    @Query(
        """
        SELECT CAST(strftime('%Y', pub_date / 1000, 'unixepoch') AS INTEGER) AS year,
               MIN(order_index) AS order_index
        FROM episodes
        WHERE show_id = :showId AND pub_date IS NOT NULL
        GROUP BY year
        ORDER BY year ASC
        """
    )
    suspend fun yearMarks(showId: Long): List<YearMarkRow>

    /** Unplayed episodes at or after where you left off that are already on disk. */
    @Query(
        """
        SELECT COUNT(*) FROM episodes e
        LEFT JOIN positions p ON p.episode_id = e.id
        JOIN downloads d ON d.episode_id = e.id
        WHERE e.show_id = :showId
          AND e.order_index >= :fromOrderIndex
          AND (p.played IS NULL OR p.played = 0)
          AND d.state = 'DONE' AND d.file_path IS NOT NULL
        """
    )
    fun observeDownloadedAhead(showId: Long, fromOrderIndex: Int): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(episodes: List<EpisodeEntity>): List<Long>

    @Query(
        """
        UPDATE episodes SET
            title = :title,
            pub_date = :pubDate,
            duration_s = :durationSeconds,
            enclosure_url = :enclosureUrl,
            description = :description,
            episode_number = :episodeNumber,
            enclosure_bytes = :enclosureBytes
        WHERE id = :id
        """
    )
    suspend fun updateMetadata(
        id: Long,
        title: String,
        pubDate: Long?,
        durationSeconds: Long?,
        enclosureUrl: String?,
        description: String?,
        episodeNumber: Int?,
        enclosureBytes: Long?,
    )

    @Query("UPDATE episodes SET order_index = :orderIndex WHERE id = :id")
    suspend fun updateOrderIndex(id: Long, orderIndex: Int)

    @Query("UPDATE episodes SET duration_s = :durationSeconds WHERE id = :id AND duration_s IS NULL")
    suspend fun fillMissingDuration(id: Long, durationSeconds: Long)
}

data class EpisodeIdentity(
    val id: Long,
    val guid: String,
    @androidx.room.ColumnInfo(name = "order_index") val orderIndex: Int,
)

@Dao
interface PositionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(position: PositionEntity)

    @Query("SELECT * FROM positions WHERE episode_id = :episodeId")
    suspend fun get(episodeId: Long): PositionEntity?

    @Query("SELECT * FROM positions WHERE episode_id = :episodeId")
    fun observe(episodeId: Long): Flow<PositionEntity?>

    /**
     * The hot path. Writes only the three columns that change during playback so
     * a flush costs as little as possible - this runs every five seconds and on
     * every audio-route change.
     */
    @Query(
        """
        INSERT INTO positions (episode_id, position_s, updated_at, played, played_at)
        VALUES (:episodeId, :positionSeconds, :updatedAt, 0, NULL)
        ON CONFLICT(episode_id) DO UPDATE SET
            position_s = :positionSeconds,
            updated_at = :updatedAt
        """
    )
    suspend fun savePosition(episodeId: Long, positionSeconds: Long, updatedAt: Long)

    @Query(
        """
        INSERT INTO positions (episode_id, position_s, updated_at, played, played_at)
        VALUES (:episodeId, :positionSeconds, :at, 1, :at)
        ON CONFLICT(episode_id) DO UPDATE SET
            position_s = :positionSeconds,
            updated_at = :at,
            played = 1,
            played_at = :at
        """
    )
    suspend fun markPlayed(episodeId: Long, positionSeconds: Long, at: Long)

    @Query(
        """
        INSERT INTO positions (episode_id, position_s, updated_at, played, played_at)
        VALUES (:episodeId, 0, :at, 0, NULL)
        ON CONFLICT(episode_id) DO UPDATE SET
            position_s = 0,
            updated_at = :at,
            played = 0,
            played_at = NULL
        """
    )
    suspend fun markUnplayed(episodeId: Long, at: Long)

    @Query("SELECT COUNT(*) FROM positions p JOIN episodes e ON e.id = p.episode_id WHERE e.show_id = :showId AND p.played = 1")
    suspend fun playedCount(showId: Long): Int

    // -- Bulk marking -------------------------------------------------------
    //
    // Every query below takes an explicit id list rather than a range, because
    // an undo has to put back exactly the rows a bulk mark touched and nothing
    // else. Callers are responsible for chunking those lists - see
    // PlaybackRepository.SQL_VARIABLE_LIMIT.

    /**
     * Ids in an order-index range whose played state differs from the target:
     * exactly the rows a bulk mark will change, and so exactly the rows an undo
     * has to restore.
     */
    @Query(
        """
        SELECT e.id FROM episodes e
        LEFT JOIN positions p ON p.episode_id = e.id
        WHERE e.show_id = :showId
          AND e.order_index BETWEEN :fromOrderIndex AND :toOrderIndex
          AND COALESCE(p.played, 0) != :played
        ORDER BY e.order_index ASC
        """
    )
    suspend fun idsNeedingPlayedChange(
        showId: Long,
        fromOrderIndex: Int,
        toOrderIndex: Int,
        played: Boolean,
    ): List<Long>

    @Query("SELECT * FROM positions WHERE episode_id IN (:episodeIds)")
    suspend fun positionsFor(episodeIds: List<Long>): List<PositionEntity>

    @Query("DELETE FROM positions WHERE episode_id IN (:episodeIds)")
    suspend fun deletePositions(episodeIds: List<Long>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(positions: List<PositionEntity>)

    @Query(
        """
        SELECT COUNT(*) FROM episodes e
        LEFT JOIN positions p ON p.episode_id = e.id
        WHERE e.show_id = :showId
          AND e.order_index BETWEEN :fromOrderIndex AND :toOrderIndex
          AND COALESCE(p.played, 0) = 1
        """
    )
    suspend fun playedCountInRange(showId: Long, fromOrderIndex: Int, toOrderIndex: Int): Int

    /**
     * Written as INSERT OR IGNORE plus UPDATE rather than as an upsert. The
     * hot-path writes above use ON CONFLICT DO UPDATE, which needs SQLite 3.24;
     * there is no reason to add more of that dependency to a path that does not
     * need the speed.
     */
    @Query(
        """
        INSERT OR IGNORE INTO positions (episode_id, position_s, updated_at, played, played_at)
        SELECT e.id, 0, :at, 0, NULL FROM episodes e WHERE e.id IN (:episodeIds)
        """
    )
    suspend fun ensurePositionRows(episodeIds: List<Long>, at: Long)

    /** Played means heard to the end, so the saved position goes to the duration. */
    @Query(
        """
        UPDATE positions SET
            position_s = COALESCE(
                (SELECT e.duration_s FROM episodes e WHERE e.id = positions.episode_id),
                position_s
            ),
            updated_at = :at,
            played = 1,
            played_at = :at
        WHERE episode_id IN (:episodeIds)
        """
    )
    suspend fun setPlayedIn(episodeIds: List<Long>, at: Long)

    @Query(
        """
        UPDATE positions SET position_s = 0, updated_at = :at, played = 0, played_at = NULL
        WHERE episode_id IN (:episodeIds)
        """
    )
    suspend fun setUnplayedIn(episodeIds: List<Long>, at: Long)

    @Transaction
    suspend fun applyPlayedIn(episodeIds: List<Long>, played: Boolean, at: Long) {
        if (episodeIds.isEmpty()) return
        ensurePositionRows(episodeIds, at)
        if (played) setPlayedIn(episodeIds, at) else setUnplayedIn(episodeIds, at)
    }

    /** Restores a chunk to its exact prior state: absent rows go back to absent. */
    @Transaction
    suspend fun restorePositions(episodeIds: List<Long>, previous: List<PositionEntity>) {
        if (episodeIds.isEmpty()) return
        deletePositions(episodeIds)
        if (previous.isNotEmpty()) upsertAll(previous)
    }
}

@Dao
interface MarkDao {

    @Query("SELECT * FROM marks WHERE episode_id = :episodeId")
    fun observe(episodeId: Long): Flow<MarkEntity?>

    @Query(
        """
        INSERT INTO marks (episode_id, starred, note, keep_after_playing, updated_at)
        VALUES (:episodeId, :starred, NULL, 0, :at)
        ON CONFLICT(episode_id) DO UPDATE SET starred = :starred, updated_at = :at
        """
    )
    suspend fun setStarred(episodeId: Long, starred: Boolean, at: Long)

    @Query(
        """
        INSERT INTO marks (episode_id, starred, note, keep_after_playing, updated_at)
        VALUES (:episodeId, 0, :note, 0, :at)
        ON CONFLICT(episode_id) DO UPDATE SET note = :note, updated_at = :at
        """
    )
    suspend fun setNote(episodeId: Long, note: String?, at: Long)

    @Query(
        """
        INSERT INTO marks (episode_id, starred, note, keep_after_playing, updated_at)
        VALUES (:episodeId, 0, NULL, :keep, :at)
        ON CONFLICT(episode_id) DO UPDATE SET keep_after_playing = :keep, updated_at = :at
        """
    )
    suspend fun setKeepAfterPlaying(episodeId: Long, keep: Boolean, at: Long)
}

@Dao
interface DownloadDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(download: DownloadEntity)

    @Query("SELECT * FROM downloads WHERE episode_id = :episodeId")
    suspend fun get(episodeId: Long): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE state = 'QUEUED' OR state = 'RUNNING'")
    suspend fun pending(): List<DownloadEntity>

    @Query("DELETE FROM downloads WHERE episode_id = :episodeId")
    suspend fun delete(episodeId: Long)

    @Query("UPDATE downloads SET state = :state, updated_at = :at WHERE episode_id = :episodeId")
    suspend fun setState(episodeId: Long, state: DownloadState, at: Long)

    @Query("UPDATE downloads SET bytes_done = :bytesDone, bytes_total = :bytesTotal, updated_at = :at WHERE episode_id = :episodeId")
    suspend fun setProgress(episodeId: Long, bytesDone: Long, bytesTotal: Long, at: Long)

    @Query(
        """
        SELECT d.episode_id AS episode_id,
               d.file_path AS file_path,
               e.enclosure_url AS enclosure_url
        FROM downloads d
        JOIN episodes e ON e.id = d.episode_id
        WHERE d.state = 'DONE' AND d.file_path IS NOT NULL
        """
    )
    fun observeLocalFiles(): Flow<List<LocalFileRow>>

    @Query("SELECT COALESCE(SUM(bytes_done), 0) FROM downloads WHERE state = 'DONE'")
    fun observeBytesOnDisk(): Flow<Long>

    @Query("SELECT file_path FROM downloads WHERE file_path IS NOT NULL")
    suspend fun allFilePaths(): List<String>

    @Query(
        """
        SELECT e.id AS episode_id,
               e.order_index AS order_index,
               p.played AS played,
               p.played_at AS played_at,
               d.state AS download_state,
               m.keep_after_playing AS keep_after_playing,
               d.bytes_total AS bytes_total,
               e.enclosure_bytes AS enclosure_bytes,
               e.duration_s AS duration_s,
               e.enclosure_url AS enclosure_url,
               d.file_path AS file_path
        FROM episodes e
        LEFT JOIN positions p ON p.episode_id = e.id
        LEFT JOIN downloads d ON d.episode_id = e.id
        LEFT JOIN marks m ON m.episode_id = e.id
        WHERE e.show_id = :showId
        ORDER BY e.order_index ASC
        """
    )
    suspend fun planningRows(showId: Long): List<DownloadSlotRow>
}
