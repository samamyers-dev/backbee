package dev.backbee.core.playback

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlaybackSpeedsTest {

    @Test
    fun `cycles upward through the steps and wraps at the top`() {
        assertThat(PlaybackSpeeds.next(1.0f)).isEqualTo(1.2f)
        assertThat(PlaybackSpeeds.next(1.8f)).isEqualTo(2.0f)
        assertThat(PlaybackSpeeds.next(2.0f)).isEqualTo(1.0f)
    }

    @Test
    fun `a speed between steps snaps up to the next real step`() {
        assertThat(PlaybackSpeeds.next(1.5f)).isEqualTo(1.6f)
        assertThat(PlaybackSpeeds.next(0.5f)).isEqualTo(1.0f)
        assertThat(PlaybackSpeeds.next(3.0f)).isEqualTo(1.0f)
    }

    @Test
    fun `float error from the player does not stall the cycle`() {
        assertThat(PlaybackSpeeds.next(1.2000001f)).isEqualTo(1.4f)
        assertThat(PlaybackSpeeds.next(1.1999999f)).isEqualTo(1.4f)
    }
}
