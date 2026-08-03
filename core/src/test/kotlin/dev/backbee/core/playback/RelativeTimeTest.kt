package dev.backbee.core.playback

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RelativeTimeTest {

    private val minute = 60_000L
    private val hour = 60 * minute
    private val day = 24 * hour

    @Test
    fun `scales through the units the shelf actually shows`() {
        assertThat(RelativeTime.ago(5_000)).isEqualTo("JUST NOW")
        assertThat(RelativeTime.ago(3 * minute)).isEqualTo("3 MINUTES AGO")
        assertThat(RelativeTime.ago(5 * hour)).isEqualTo("5 HOURS AGO")
        assertThat(RelativeTime.ago(3 * day)).isEqualTo("3 DAYS AGO")
        assertThat(RelativeTime.ago(3 * 7 * day)).isEqualTo("3 WEEKS AGO")
        assertThat(RelativeTime.ago(8 * 30 * day)).isEqualTo("8 MONTHS AGO")
        assertThat(RelativeTime.ago(2 * 365 * day)).isEqualTo("2 YEARS AGO")
    }

    @Test
    fun `singular units do not gain an s`() {
        assertThat(RelativeTime.ago(1 * minute)).isEqualTo("1 MINUTE AGO")
        assertThat(RelativeTime.ago(1 * hour)).isEqualTo("1 HOUR AGO")
        assertThat(RelativeTime.ago(1 * day)).isEqualTo("1 DAY AGO")
        assertThat(RelativeTime.ago(400 * day)).isEqualTo("1 YEAR AGO")
    }

    @Test
    fun `a future or clock-skewed timestamp does not produce a negative age`() {
        assertThat(RelativeTime.ago(-5000)).isEqualTo("JUST NOW")
        assertThat(RelativeTime.since(timestampMillis = 2_000, nowMillis = 1_000)).isEqualTo("JUST NOW")
    }

    @Test
    fun `since subtracts against the supplied clock`() {
        val now = 1_700_000_000_000L
        assertThat(RelativeTime.since(now - 3 * day, now)).isEqualTo("3 DAYS AGO")
    }
}
