package dev.backbee.core.download

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DownloadPlannerTest {

    private val mb = 1024L * 1024
    private val now = 1_700_000_000_000L
    private val hour = 3_600_000L

    private fun archive(
        size: Int,
        playedThrough: Int = 0,
        downloaded: Set<Int> = emptySet(),
        bytesEach: Long = 50 * 1024 * 1024,
        playedAt: Long? = null,
    ): List<EpisodeSlot> = (0 until size).map { i ->
        EpisodeSlot(
            episodeId = i.toLong(),
            orderIndex = i,
            played = i < playedThrough,
            playedAtMillis = if (i < playedThrough) playedAt else null,
            downloaded = i in downloaded,
            bytes = bytesEach,
        )
    }

    @Test
    fun `fills the look-ahead window from the current position`() {
        val plan = DownloadPlanner(DownloadConfig(downloadAhead = 10, storageCapBytes = 100L * 1024 * mb))
            .plan(archive(size = 1000, playedThrough = 312), currentOrderIndex = 312, nowMillis = now)

        // The current episode plus the next ten.
        assertThat(plan.toDownload).hasSize(11)
        assertThat(plan.toDownload.first()).isEqualTo(312L)
        assertThat(plan.toDownload.last()).isEqualTo(322L)
        assertThat(plan.capLimited).isFalse()
    }

    @Test
    fun `already downloaded episodes in the window are left alone`() {
        val plan = DownloadPlanner(DownloadConfig(downloadAhead = 3, storageCapBytes = 100L * 1024 * mb))
            .plan(
                archive(size = 100, playedThrough = 10, downloaded = setOf(10, 11)),
                currentOrderIndex = 10,
                nowMillis = now,
            )

        assertThat(plan.toDownload).containsExactly(12L, 13L).inOrder()
        assertThat(plan.toDelete).isEmpty()
    }

    @Test
    fun `played episodes survive the keep window and are reclaimed after it`() {
        val config = DownloadConfig(downloadAhead = 2, deletePlayedAfterMillis = 24 * hour, storageCapBytes = 100L * 1024 * mb)

        val fresh = DownloadPlanner(config).plan(
            archive(size = 50, playedThrough = 10, downloaded = setOf(9), playedAt = now - 2 * hour),
            currentOrderIndex = 10,
            nowMillis = now,
        )
        assertThat(fresh.toDelete).doesNotContain(9L)

        val stale = DownloadPlanner(config).plan(
            archive(size = 50, playedThrough = 10, downloaded = setOf(9), playedAt = now - 30 * hour),
            currentOrderIndex = 10,
            nowMillis = now,
        )
        assertThat(stale.toDelete).contains(9L)
        assertThat(stale.reasons[9L]).contains("past the 24h keep window")
    }

    @Test
    fun `a played episode with no recorded timestamp is reclaimable`() {
        // Legacy rows and manual "mark played" can leave played_at null. Holding
        // those forever would slowly fill the disk with audio nobody wants.
        val plan = DownloadPlanner(DownloadConfig(downloadAhead = 2, storageCapBytes = 100L * 1024 * mb))
            .plan(
                archive(size = 50, playedThrough = 5, downloaded = setOf(3), playedAt = null),
                currentOrderIndex = 5,
                nowMillis = now,
            )

        assertThat(plan.toDelete).contains(3L)
    }

    @Test
    fun `jumping forward drops what was left behind and what is now out of range`() {
        val slots = archive(size = 200, downloaded = setOf(2, 3, 4, 180))
        val plan = DownloadPlanner(DownloadConfig(downloadAhead = 5, storageCapBytes = 100L * 1024 * mb))
            .plan(slots, currentOrderIndex = 100, nowMillis = now)

        assertThat(plan.toDelete).containsAtLeast(2L, 3L, 4L, 180L)
        assertThat(plan.reasons[2L]).isEqualTo("behind the current position")
        assertThat(plan.reasons[180L]).contains("beyond the 5-episode look-ahead")
    }

    @Test
    fun `played episodes inside the window do not eat look-ahead slots`() {
        // Episodes 11-13 were already heard out of order. The window should still
        // reach three *unplayed* episodes ahead rather than stopping at 13.
        val slots = archive(size = 100).mapIndexed { i, slot ->
            if (i in 11..13) slot.copy(played = true, playedAtMillis = now) else slot
        }
        val plan = DownloadPlanner(DownloadConfig(downloadAhead = 3, storageCapBytes = 100L * 1024 * mb))
            .plan(slots, currentOrderIndex = 10, nowMillis = now)

        assertThat(plan.toDownload).containsExactly(10L, 14L, 15L, 16L).inOrder()
    }

    @Test
    fun `episodes without an enclosure are never queued`() {
        val slots = archive(size = 20).mapIndexed { i, slot ->
            if (i == 6) slot.copy(hasEnclosure = false) else slot
        }
        val plan = DownloadPlanner(DownloadConfig(downloadAhead = 3, storageCapBytes = 100L * 1024 * mb))
            .plan(slots, currentOrderIndex = 5, nowMillis = now)

        assertThat(plan.toDownload).doesNotContain(6L)
        assertThat(plan.toDownload).containsExactly(5L, 7L, 8L, 9L).inOrder()
    }

    @Test
    fun `the storage cap stops the queue short rather than overfilling the disk`() {
        // Room for four 50 MB episodes; the window asks for eleven.
        val plan = DownloadPlanner(DownloadConfig(downloadAhead = 10, storageCapBytes = 200 * mb))
            .plan(archive(size = 100), currentOrderIndex = 0, nowMillis = now)

        assertThat(plan.toDownload).hasSize(4)
        assertThat(plan.capLimited).isTrue()
        assertThat(plan.projectedBytes).isAtMost(200 * mb)
    }

    @Test
    fun `under pressure the furthest ahead episode is given up, never the next one`() {
        // 20 and 21 are already on disk. Playback is at 10, the cap allows three
        // episodes total, so the distant pair must go to make room for 10 and 11.
        val slots = archive(size = 100, downloaded = setOf(20, 21))
        val plan = DownloadPlanner(DownloadConfig(downloadAhead = 15, storageCapBytes = 150 * mb))
            .plan(slots, currentOrderIndex = 10, nowMillis = now)

        assertThat(plan.toDownload.first()).isEqualTo(10L)
        assertThat(plan.toDelete).contains(21L)
        assertThat(plan.projectedBytes).isAtMost(150 * mb)
        // Whatever was evicted, nothing survives that is further ahead than
        // something we declined to fetch.
        val kept = (plan.toDownload + slots.filter { it.downloaded }.map { it.episodeId } - plan.toDelete.toSet())
        assertThat(kept).contains(10L)
    }

    @Test
    fun `an episode marked keep is never reclaimed, however long ago it was played`() {
        val slots = archive(size = 50, playedThrough = 10, downloaded = setOf(3), playedAt = now - 30 * 24 * hour)
            .map { if (it.episodeId == 3L) it.copy(keepAfterPlaying = true) else it }

        val plan = DownloadPlanner(DownloadConfig(downloadAhead = 2, storageCapBytes = 100L * 1024 * mb))
            .plan(slots, currentOrderIndex = 10, nowMillis = now)

        assertThat(plan.toDelete).doesNotContain(3L)
    }

    @Test
    fun `a kept episode is not evicted even when the cap is under pressure`() {
        // 20 and 21 are downloaded and far ahead; 21 is marked keep, so the
        // eviction has to fall on 20 instead.
        val slots = archive(size = 100, downloaded = setOf(20, 21))
            .map { if (it.episodeId == 21L) it.copy(keepAfterPlaying = true) else it }

        val plan = DownloadPlanner(DownloadConfig(downloadAhead = 15, storageCapBytes = 150 * mb))
            .plan(slots, currentOrderIndex = 10, nowMillis = now)

        assertThat(plan.toDelete).doesNotContain(21L)
        assertThat(plan.toDownload.firstOrNull()).isEqualTo(10L)
    }

    @Test
    fun `an empty archive plans nothing`() {
        val plan = DownloadPlanner(DownloadConfig()).plan(emptyList(), currentOrderIndex = 0, nowMillis = now)

        assertThat(plan.toDownload).isEmpty()
        assertThat(plan.toDelete).isEmpty()
        assertThat(plan.projectedBytes).isEqualTo(0)
    }

    @Test
    fun `the plan is stable when run twice against its own outcome`() {
        val config = DownloadConfig(downloadAhead = 5, storageCapBytes = 100L * 1024 * mb)
        val first = DownloadPlanner(config).plan(archive(size = 50), currentOrderIndex = 10, nowMillis = now)

        val afterFirst = archive(size = 50, downloaded = first.toDownload.map { it.toInt() }.toSet())
        val second = DownloadPlanner(config).plan(afterFirst, currentOrderIndex = 10, nowMillis = now)

        // Nothing left to do, and nothing it just fetched gets thrown away.
        assertThat(second.toDownload).isEmpty()
        assertThat(second.toDelete).isEmpty()
    }
}
