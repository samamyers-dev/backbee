package dev.backbee.core.playback

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SmartResumeTest {

    @Test
    fun `rewind scales with how long you were away`() {
        assertThat(SmartResume.rewindSeconds(0)).isEqualTo(0)
        assertThat(SmartResume.rewindSeconds(9 * 60)).isEqualTo(0)
        assertThat(SmartResume.rewindSeconds(10 * 60)).isEqualTo(0)
        assertThat(SmartResume.rewindSeconds(10 * 60 + 1)).isEqualTo(10)
        assertThat(SmartResume.rewindSeconds(60 * 60)).isEqualTo(10)
        assertThat(SmartResume.rewindSeconds(60 * 60 + 1)).isEqualTo(30)
        assertThat(SmartResume.rewindSeconds(24 * 60 * 60)).isEqualTo(30)
        assertThat(SmartResume.rewindSeconds(24 * 60 * 60 + 1)).isEqualTo(60)
        assertThat(SmartResume.rewindSeconds(365L * 24 * 60 * 60)).isEqualTo(60)
    }

    @Test
    fun `a negative elapsed time from a clock change does not rewind further`() {
        assertThat(SmartResume.rewindSeconds(-5000)).isEqualTo(0)
    }

    @Test
    fun `resume never seeks before the start of the episode`() {
        assertThat(SmartResume.resumePositionSeconds(savedPositionSeconds = 5, pausedForSeconds = 7 * 24 * 3600))
            .isEqualTo(0)
        assertThat(SmartResume.resumePositionSeconds(savedPositionSeconds = 0, pausedForSeconds = 0)).isEqualTo(0)
        assertThat(SmartResume.resumePositionSeconds(savedPositionSeconds = -12, pausedForSeconds = 0)).isEqualTo(0)
    }

    @Test
    fun `resume subtracts the tier rewind from the saved position`() {
        assertThat(SmartResume.resumePositionSeconds(savedPositionSeconds = 1800, pausedForSeconds = 120))
            .isEqualTo(1800)
        assertThat(SmartResume.resumePositionSeconds(savedPositionSeconds = 1800, pausedForSeconds = 2 * 3600))
            .isEqualTo(1770)
        assertThat(SmartResume.resumePositionSeconds(savedPositionSeconds = 1800, pausedForSeconds = 3 * 24 * 3600))
            .isEqualTo(1740)
    }

    @Test
    fun `custom tiers are honoured and need not be given in order`() {
        val tiers = listOf(
            ResumeTier(Long.MAX_VALUE, 120),
            ResumeTier(30, 0),
            ResumeTier(300, 5),
        )
        assertThat(SmartResume.rewindSeconds(10, tiers)).isEqualTo(0)
        assertThat(SmartResume.rewindSeconds(100, tiers)).isEqualTo(5)
        assertThat(SmartResume.rewindSeconds(10_000, tiers)).isEqualTo(120)
    }

    @Test
    fun `tiers without a catch-all still resolve for very long pauses`() {
        val tiers = listOf(ResumeTier(60, 0), ResumeTier(600, 15))
        assertThat(SmartResume.rewindSeconds(99_999, tiers)).isEqualTo(15)
    }

    @Test
    fun `empty tier list means no rewind rather than a crash`() {
        assertThat(SmartResume.rewindSeconds(99_999, emptyList())).isEqualTo(0)
    }

    @Test
    fun `tiers round-trip through their stored form`() {
        val encoded = SmartResume.encodeTiers(SmartResume.DEFAULT_TIERS)
        assertThat(encoded).isEqualTo("600:0,3600:10,86400:30,*:60")
        assertThat(SmartResume.decodeTiers(encoded)).isEqualTo(SmartResume.DEFAULT_TIERS)
    }

    @Test
    fun `a corrupt stored value falls back to the defaults instead of breaking resume`() {
        assertThat(SmartResume.decodeTiers(null)).isEqualTo(SmartResume.DEFAULT_TIERS)
        assertThat(SmartResume.decodeTiers("")).isEqualTo(SmartResume.DEFAULT_TIERS)
        assertThat(SmartResume.decodeTiers("nonsense")).isEqualTo(SmartResume.DEFAULT_TIERS)
        assertThat(SmartResume.decodeTiers("600:0,broken")).isEqualTo(listOf(ResumeTier(600, 0)))
    }
}
