package dev.backbee.core.playback

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ArchiveProgressTest {

    @Test
    fun `renders the Now screen strip from the spec`() {
        val progress = ArchiveProgress(
            currentPosition = 312,
            totalEpisodes = 1247,
            // 340 hrs at 1.6x is 340 * 3600 * 1.6 seconds of audio.
            remainingSeconds = (340 * 3600 * 1.6).toLong(),
            speed = 1.6f,
        )

        assertThat(progress.summary()).isEqualTo("Ep 312 of 1,247 · 25% · ~340 hrs left at 1.6×")
    }

    @Test
    fun `percent counts finished episodes, so the first episode reads as zero`() {
        assertThat(ArchiveProgress(1, 100, 0).percentByEpisodes).isEqualTo(0)
        assertThat(ArchiveProgress(51, 100, 0).percentByEpisodes).isEqualTo(50)
        assertThat(ArchiveProgress(101, 100, 0).percentByEpisodes).isEqualTo(100)
    }

    @Test
    fun `empty and out of range archives do not produce nonsense percentages`() {
        assertThat(ArchiveProgress(1, 0, 0).percentByEpisodes).isEqualTo(0)
        assertThat(ArchiveProgress(500, 100, 0).percentByEpisodes).isEqualTo(100)
        assertThat(ArchiveProgress(-4, 100, 0).percentByEpisodes).isEqualTo(0)
    }

    @Test
    fun `time weighted percent is available when durations are known`() {
        val progress = ArchiveProgress(
            currentPosition = 51,
            totalEpisodes = 100,
            remainingSeconds = 1000,
            totalSeconds = 4000,
        )
        assertThat(progress.percentByTime).isEqualTo(75)
        assertThat(progress.percentByEpisodes).isEqualTo(50)
    }

    @Test
    fun `time weighted percent falls back to episodes when durations are unknown`() {
        assertThat(ArchiveProgress(51, 100, 1000, totalSeconds = 0).percentByTime).isEqualTo(50)
    }

    @Test
    fun `hours left shrink as speed rises`() {
        val base = ArchiveProgress(1, 100, remainingSeconds = 3600 * 100, speed = 1.0f)
        assertThat(base.remainingHoursAtSpeed).isWithin(0.01).of(100.0)
        assertThat(base.copy(speed = 2.0f).remainingHoursAtSpeed).isWithin(0.01).of(50.0)
        // A zero or negative speed would divide by zero; treat it as 1x.
        assertThat(base.copy(speed = 0f).remainingHoursAtSpeed).isWithin(0.01).of(100.0)
    }

    @Test
    fun `the strip drops the time clause when nothing is left`() {
        val progress = ArchiveProgress(1247, 1247, remainingSeconds = 0, speed = 1.6f)
        assertThat(progress.summary()).isEqualTo("Ep 1,247 of 1,247 · 100%")
    }

    @Test
    fun `newest first counts position from the end of the archive`() {
        // orderIndex 1246 is the newest episode: the first one a newest-first
        // listener hears, so it has to read "Ep 1", not "Ep 1,247".
        val first = ArchiveProgress(
            currentPosition = 1_247,
            totalEpisodes = 1_247,
            remainingSeconds = 0,
            direction = ListeningDirection.NEWEST_FIRST,
        )
        assertThat(first.listeningPosition).isEqualTo(1)
        assertThat(first.percentByEpisodes).isEqualTo(0)
        assertThat(first.summary()).isEqualTo("Ep 1 of 1,247 · 0%")

        val middle = first.copy(currentPosition = 625)
        assertThat(middle.listeningPosition).isEqualTo(623)
        assertThat(middle.completedEpisodes).isEqualTo(622)

        val last = first.copy(currentPosition = 1)
        assertThat(last.listeningPosition).isEqualTo(1_247)
        assertThat(last.summary()).isEqualTo("Ep 1,247 of 1,247 · 100%")
    }

    @Test
    fun `direction mirrors the position but not the amount of audio left`() {
        val oldest = ArchiveProgress(
            currentPosition = 312,
            totalEpisodes = 1_247,
            remainingSeconds = 12_000,
            direction = ListeningDirection.OLDEST_FIRST,
        )
        val newest = oldest.copy(direction = ListeningDirection.NEWEST_FIRST)

        assertThat(oldest.listeningPosition).isEqualTo(312)
        assertThat(newest.listeningPosition).isEqualTo(936)
        assertThat(newest.percentByEpisodes).isEqualTo(75)
        assertThat(newest.remainingSeconds).isEqualTo(oldest.remainingSeconds)
        assertThat(newest.remainingHoursAtSpeed).isWithin(0.001).of(oldest.remainingHoursAtSpeed)
    }

    @Test
    fun `speed labels stay short`() {
        assertThat(ArchiveProgress.formatSpeed(1.0f)).isEqualTo("1")
        assertThat(ArchiveProgress.formatSpeed(1.5f)).isEqualTo("1.5")
        assertThat(ArchiveProgress.formatSpeed(1.6f)).isEqualTo("1.6")
        assertThat(ArchiveProgress.formatSpeed(1.25f)).isEqualTo("1.25")
        assertThat(ArchiveProgress.formatSpeed(2.0f)).isEqualTo("2")
    }

    @Test
    fun `duration and clock formats`() {
        assertThat(ArchiveProgress.formatDuration(0)).isEqualTo("0s")
        assertThat(ArchiveProgress.formatDuration(48)).isEqualTo("48s")
        assertThat(ArchiveProgress.formatDuration(750)).isEqualTo("12m 30s")
        assertThat(ArchiveProgress.formatDuration(15_120)).isEqualTo("4h 12m")

        assertThat(ArchiveProgress.formatClock(65)).isEqualTo("1:05")
        assertThat(ArchiveProgress.formatClock(3661)).isEqualTo("1:01:01")
        assertThat(ArchiveProgress.formatClock(-5)).isEqualTo("0:00")
    }
}
