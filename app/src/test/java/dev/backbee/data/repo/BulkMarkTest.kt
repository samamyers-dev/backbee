package dev.backbee.data.repo

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import dev.backbee.data.db.BackbeeDatabase
import dev.backbee.data.db.EpisodeEntity
import dev.backbee.data.db.ShowEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Bulk marking, and getting it back.
 *
 * The undo is the part worth testing: a bulk mark rewrites hundreds of rows, and
 * an undo that only clears the played flag would silently destroy saved
 * positions and re-open episodes that were already finished before it ran.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class BulkMarkTest {

    private lateinit var db: BackbeeDatabase
    private lateinit var repo: PlaybackRepository
    private var showId: Long = 0
    private lateinit var episodeIds: List<Long>

    private companion object {
        const val EPISODES = 10
        const val ANCHOR = 5
    }

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            BackbeeDatabase::class.java,
        )
            .addCallback(BackbeeDatabase.ENABLE_FOREIGN_KEYS)
            .allowMainThreadQueries()
            .build()
        repo = PlaybackRepository(db)

        showId = db.showDao().insert(
            ShowEntity(rssUrl = "https://example.com/feed", title = "The Long Archive", addedAt = 0)
        )
        db.episodeDao().insertAll(
            (0 until EPISODES).map { index ->
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
        episodeIds = db.episodeDao().observeArchive(showId).first().map { it.id }
    }

    @After
    fun tearDown() {
        if (::db.isInitialized) db.close()
    }

    private suspend fun rows() = db.episodeDao().observeArchive(showId).first()

    @Test
    fun `marking everything before leaves the anchor and everything after alone`() = runTest {
        val change = repo.markRange(showId, 0, ANCHOR - 1, played = true)

        assertThat(change).isNotNull()
        assertThat(change!!.count).isEqualTo(ANCHOR)

        val rows = rows()
        assertThat(rows.take(ANCHOR).map { it.isPlayed }).doesNotContain(false)
        assertThat(rows.drop(ANCHOR).map { it.isPlayed }).doesNotContain(true)
    }

    @Test
    fun `played episodes carry a position at the full duration`() = runTest {
        repo.markRange(showId, 0, 2, played = true)

        assertThat(rows().take(3).map { it.positionSeconds }).containsExactly(600L, 600L, 600L)
    }

    @Test
    fun `an already played episode is not touched and undo does not reopen it`() = runTest {
        // Episode 1 was finished properly, long before the bulk mark.
        repo.markPlayed(episodeIds[0], positionSeconds = 600, at = 111L)

        val change = repo.markRange(showId, 0, ANCHOR - 1, played = true, at = 999L)

        // Four rows changed, not five: the finished one was already there.
        assertThat(change!!.count).isEqualTo(ANCHOR - 1)
        assertThat(change.episodeIds).doesNotContain(episodeIds[0])
        assertThat(db.positionDao().get(episodeIds[0])!!.playedAt).isEqualTo(111L)

        repo.undoBulk(change)

        assertThat(rows()[0].isPlayed).isTrue()
        assertThat(rows().drop(1).take(ANCHOR - 1).map { it.isPlayed }).doesNotContain(true)
    }

    @Test
    fun `undo restores a part heard episode to its exact saved position`() = runTest {
        repo.savePosition(episodeIds[2], positionSeconds = 137, at = 222L)

        val change = repo.markRange(showId, 0, ANCHOR - 1, played = true)!!
        assertThat(rows()[2].positionSeconds).isEqualTo(600L)

        repo.undoBulk(change)

        val restored = db.positionDao().get(episodeIds[2])!!
        assertThat(restored.positionSeconds).isEqualTo(137L)
        assertThat(restored.played).isFalse()
        assertThat(restored.updatedAt).isEqualTo(222L)
    }

    @Test
    fun `undo removes position rows that did not exist before`() = runTest {
        val change = repo.markRange(showId, 0, ANCHOR - 1, played = true)!!

        repo.undoBulk(change)

        // Not merely played = 0: an untouched episode had no row at all, and
        // leaving an empty one behind would make it look started.
        assertThat(db.positionDao().get(episodeIds[0])).isNull()
    }

    @Test
    fun `marking a range that is already in the target state changes nothing`() = runTest {
        repo.markRange(showId, 0, ANCHOR - 1, played = true)

        assertThat(repo.markRange(showId, 0, ANCHOR - 1, played = true)).isNull()
    }

    @Test
    fun `marking unplayed clears the whole range`() = runTest {
        repo.markRange(showId, 0, EPISODES - 1, played = true)

        val change = repo.markRange(showId, 0, EPISODES - 1, played = false)

        assertThat(change!!.count).isEqualTo(EPISODES)
        assertThat(rows().map { it.isPlayed }).doesNotContain(true)
    }

    @Test
    fun `finishing the archive in bulk completes the show and undo re-opens it`() = runTest {
        val change = repo.markRange(showId, 0, EPISODES - 1, played = true, at = 4_242L)!!

        assertThat(db.showDao().getById(showId)!!.completedAt).isEqualTo(4_242L)

        repo.undoBulk(change)

        assertThat(db.showDao().getById(showId)!!.completedAt).isNull()
    }

    @Test
    fun `a partial bulk mark does not complete the show`() = runTest {
        repo.markRange(showId, 0, EPISODES - 2, played = true)

        assertThat(db.showDao().getById(showId)!!.completedAt).isNull()
    }

    @Test
    fun `the summary counts the range as it stands`() = runTest {
        repo.markRange(showId, 0, 1, played = true)

        val summary = repo.summarizeRange(showId, 0, ANCHOR - 1)

        assertThat(summary.total).isEqualTo(ANCHOR)
        assertThat(summary.played).isEqualTo(2)
        assertThat(summary.unplayed).isEqualTo(ANCHOR - 2)
    }

    @Test
    fun `an inverted range is empty rather than an error`() = runTest {
        assertThat(repo.summarizeRange(showId, 3, 2).isEmpty).isTrue()
        assertThat(repo.markRange(showId, 3, 2, played = true)).isNull()
    }

    @Test
    fun `the last order index bounds everything after`() = runTest {
        assertThat(repo.lastOrderIndex(showId)).isEqualTo(EPISODES - 1)
    }
}
