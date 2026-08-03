package dev.backbee.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Covers the SQL the app cannot afford to get wrong: the archive projection, the
 * upsert on the position hot path, and the aggregate the Now screen reads.
 */
@RunWith(RobolectricTestRunner::class)
class BackbeeDatabaseTest {

    private lateinit var db: BackbeeDatabase
    private var showId: Long = 0

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            BackbeeDatabase::class.java,
        )
            .addCallback(BackbeeDatabase.ENABLE_FOREIGN_KEYS)
            .allowMainThreadQueries()
            .build()

        showId = db.showDao().insert(
            ShowEntity(rssUrl = "https://example.com/feed", title = "The Long Archive", addedAt = 0)
        )
        db.episodeDao().insertAll(
            (0 until 5).map { index ->
                EpisodeEntity(
                    showId = showId,
                    guid = "guid-$index",
                    orderIndex = index,
                    title = "Episode ${index + 1}",
                    pubDate = 1_000L * index,
                    durationSeconds = 600,
                    enclosureUrl = "https://cdn.example/$index.mp3",
                    episodeNumber = index + 1,
                )
            }
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `archive comes back oldest first with null state for untouched episodes`() = runTest {
        val rows = db.episodeDao().observeArchive(showId).first()

        assertThat(rows).hasSize(5)
        assertThat(rows.map { it.orderIndex }).isInOrder()
        assertThat(rows.first().isPlayed).isFalse()
        assertThat(rows.first().isStarred).isFalse()
        assertThat(rows.first().isDownloaded).isFalse()
        assertThat(rows.first().progressFraction).isNull()
    }

    @Test
    fun `savePosition inserts then updates without disturbing played state`() = runTest {
        val episodeId = db.episodeDao().observeArchive(showId).first()[1].id
        val positions = db.positionDao()

        positions.savePosition(episodeId, positionSeconds = 30, updatedAt = 100)
        assertThat(positions.get(episodeId)?.positionSeconds).isEqualTo(30)

        positions.savePosition(episodeId, positionSeconds = 90, updatedAt = 200)
        val stored = positions.get(episodeId)
        assertThat(stored?.positionSeconds).isEqualTo(90)
        assertThat(stored?.updatedAt).isEqualTo(200)
        assertThat(stored?.played).isFalse()
    }

    @Test
    fun `markPlayed and markUnplayed round trip`() = runTest {
        val episodeId = db.episodeDao().observeArchive(showId).first()[0].id
        val positions = db.positionDao()

        positions.markPlayed(episodeId, positionSeconds = 600, at = 500)
        assertThat(positions.get(episodeId)?.played).isTrue()
        assertThat(positions.get(episodeId)?.playedAt).isEqualTo(500)

        positions.markUnplayed(episodeId, at = 600)
        assertThat(positions.get(episodeId)?.played).isFalse()
        assertThat(positions.get(episodeId)?.playedAt).isNull()
    }

    @Test
    fun `next unplayed skips finished episodes and lands on the bookmark`() = runTest {
        val rows = db.episodeDao().observeArchive(showId).first()
        db.positionDao().markPlayed(rows[0].id, 600, 1)
        db.positionDao().markPlayed(rows[1].id, 600, 2)
        db.positionDao().savePosition(rows[2].id, 120, 3)

        val target = db.episodeDao().nextUnplayed(showId)

        assertThat(target?.id).isEqualTo(rows[2].id)
        assertThat(target?.positionSeconds).isEqualTo(120)
        assertThat(target?.progressFraction).isWithin(0.001f).of(0.2f)
    }

    @Test
    fun `episodes without an enclosure are never offered as the next thing to play`() = runTest {
        val bare = db.showDao().insert(
            ShowEntity(rssUrl = "https://example.com/bare", title = "Bare", addedAt = 0)
        )
        db.episodeDao().insertAll(
            listOf(
                EpisodeEntity(
                    showId = bare, guid = "no-audio", orderIndex = 0, title = "No audio",
                    pubDate = 0, durationSeconds = null, enclosureUrl = null,
                ),
                EpisodeEntity(
                    showId = bare, guid = "has-audio", orderIndex = 1, title = "Has audio",
                    pubDate = 1, durationSeconds = 60, enclosureUrl = "https://cdn.example/a.mp3",
                ),
            )
        )

        assertThat(db.episodeDao().nextUnplayed(bare)?.title).isEqualTo("Has audio")
    }

    @Test
    fun `progress aggregate counts played episodes and time still ahead`() = runTest {
        val rows = db.episodeDao().observeArchive(showId).first()
        db.positionDao().markPlayed(rows[0].id, 600, 1)
        db.positionDao().markPlayed(rows[1].id, 600, 2)
        db.positionDao().savePosition(rows[2].id, 100, 3)
        db.markDao().setStarred(rows[3].id, starred = true, at = 4)

        val progress = db.showDao().observeProgress(showId).first()

        assertThat(progress?.totalEpisodes).isEqualTo(5)
        assertThat(progress?.playedEpisodes).isEqualTo(2)
        assertThat(progress?.totalSeconds).isEqualTo(3000)
        // Three unplayed episodes of 600s, less the 100s already heard of one.
        assertThat(progress?.remainingSeconds).isEqualTo(1700)
        assertThat(progress?.starredCount).isEqualTo(1)
    }

    @Test
    fun `a position past the episode duration cannot push remaining time negative`() = runTest {
        val rows = db.episodeDao().observeArchive(showId).first()
        // Durations from feeds are frequently wrong; the player can legitimately
        // report a position beyond one.
        db.positionDao().savePosition(rows[0].id, positionSeconds = 9_000, updatedAt = 1)

        val progress = db.showDao().observeProgress(showId).first()

        assertThat(progress?.remainingSeconds).isEqualTo(2400)
    }

    @Test
    fun `only one show can be active`() = runTest {
        val second = db.showDao().insert(
            ShowEntity(rssUrl = "https://example.com/other", title = "Other", addedAt = 1)
        )

        db.showDao().makeActive(showId)
        db.showDao().makeActive(second)

        assertThat(db.showDao().getAll().count { it.isActive }).isEqualTo(1)
        assertThat(db.showDao().getActive()?.id).isEqualTo(second)
    }

    @Test
    fun `local file index only reports finished downloads and carries the remote url`() = runTest {
        val rows = db.episodeDao().observeArchive(showId).first()
        db.downloadDao().upsert(
            DownloadEntity(rows[0].id, DownloadState.DONE, "/data/0.mp3", 100, 100, 1)
        )
        db.downloadDao().upsert(
            DownloadEntity(rows[1].id, DownloadState.RUNNING, null, 100, 20, 1)
        )

        val local = db.downloadDao().observeLocalFiles().first()

        assertThat(local).hasSize(1)
        assertThat(local.single().filePath).isEqualTo("/data/0.mp3")
        assertThat(local.single().enclosureUrl).isEqualTo("https://cdn.example/0.mp3")
    }

    @Test
    fun `deleting a show cascades to its episodes and their state`() = runTest {
        val rows = db.episodeDao().observeArchive(showId).first()
        db.positionDao().savePosition(rows[0].id, 10, 1)
        db.markDao().setStarred(rows[0].id, true, 1)

        db.showDao().delete(showId)

        assertThat(db.episodeDao().count(showId)).isEqualTo(0)
        assertThat(db.positionDao().get(rows[0].id)).isNull()
    }
}
