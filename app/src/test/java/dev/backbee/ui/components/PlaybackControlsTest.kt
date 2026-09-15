package dev.backbee.ui.components

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.backbee.ui.theme.BackbeeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class PlaybackControlsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun setProgressSeeksInMillisecondsAndHoldsPendingPosition() {
        val seeks = mutableListOf<Long>()
        val position = mutableStateOf(10_000L)
        compose.setContent {
            BackbeeTheme {
                ScanBar(position.value, 100_000L, { seeks += it })
            }
        }
        val seek = compose.onNodeWithContentDescription("Scan through the episode")
        seek.assertProgress(0.1f)
        seek.performSemanticsAction(SemanticsActions.SetProgress) { assertTrue(it(0.75f)) }
        seek.assertProgress(0.75f)
        compose.runOnIdle { assertEquals(listOf(75_000L), seeks) }
        // Hold the requested position until the player acknowledges it.
        compose.runOnIdle { position.value = 11_000L }
        seek.assertProgress(0.75f)
        compose.runOnIdle { position.value = 75_000L }
        seek.assertProgress(0.75f)
        compose.runOnIdle { position.value = 80_000L }
        seek.assertProgress(0.8f)
    }

    @Test fun setProgressClampsBothEndsBeforeCallingPlayer() {
        val seeks = mutableListOf<Long>()
        compose.setContent { BackbeeTheme { ScanBar(50_000L, 100_000L, { seeks += it }) } }
        val seek = compose.onNodeWithContentDescription("Scan through the episode")
        seek.performSemanticsAction(SemanticsActions.SetProgress) { assertTrue(it(-2f)) }
        seek.assertProgress(0f)
        seek.performSemanticsAction(SemanticsActions.SetProgress) { assertTrue(it(3f)) }
        seek.assertProgress(1f)
        compose.runOnIdle { assertEquals(listOf(0L, 100_000L), seeks) }
    }

    @Test fun setProgressRejectsNaNWithoutMovingThePlayer() {
        var calls = 0
        compose.setContent { BackbeeTheme { ScanBar(50_000L, 100_000L, { calls++ }) } }
        val seek = compose.onNodeWithContentDescription("Scan through the episode")
        seek.performSemanticsAction(SemanticsActions.SetProgress) { assertFalse(it(Float.NaN)) }
        seek.assertProgress(0.5f)
        compose.runOnIdle { assertEquals(0, calls) }
    }

    @Test fun unknownDurationExposesZeroProgressWithoutSeekAction() {
        var calls = 0
        compose.setContent { BackbeeTheme { ScanBar(0L, 0L, { calls++ }) } }
        compose.onNodeWithContentDescription("Scan through the episode")
            .assertProgress(0f)
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.SetProgress))
            .performTouchInput { click() }
        compose.runOnIdle { assertEquals(0, calls) }
    }

    @Test fun collapseControlCallsBack() {
        var calls = 0
        compose.setContent {
            BackbeeTheme { ScanBar(0L, 100_000L, {}, onCollapse = { calls++ }) }
        }
        compose.onNodeWithText("Hide").performClick()
        compose.runOnIdle { assertEquals(1, calls) }
    }

    @Test fun narrowLargeTextTransportKeepsSeparateAccessibleCallbacks() {
        var opens = 0
        var plays = 0
        var backs = 0
        var forwards = 0
        compose.setContent {
            BackbeeTheme(darkTheme = true) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                    Column(Modifier.width(320.dp)) {
                        NowPlayingBar(
                            title = "Two Hundred Fifty Riddles, and the Long Subtitle That Comes With Them",
                            subtitle = "Episode 250", artworkUrl = null,
                            positionMs = 10_000L, durationMs = 100_000L,
                            isPlaying = false, isBuffering = false,
                            onOpen = { opens++ }, onTogglePlay = { plays++ },
                            onSkipBack = { backs++ }, onSkipForward = { forwards++ },
                        )
                    }
                }
            }
        }
        listOf("Skip back 10 seconds", "Play", "Skip forward 30 seconds").forEach { label ->
            compose.onNodeWithContentDescription(label)
                .assertIsDisplayed()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
                .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
                .performTouchInput { click() }
        }
        compose.runOnIdle {
            assertEquals(1, plays)
            assertEquals(1, backs)
            assertEquals(1, forwards)
            assertEquals("Transport taps must not open the episode", 0, opens)
        }
        // Select the actual row action, not a merged text match whose center can
        // overlap a transport key at narrow widths. Exercise both accessibility
        // dispatch and the title's real touch target independently.
        val openAction = SemanticsMatcher("opens playing episode") {
            it.config.getOrElseNullable(SemanticsActions.OnClick) { null }?.label == "Open the playing episode"
        }
        compose.onNode(openAction).performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
        compose.runOnIdle { assertEquals(1, opens) }
        compose.onNodeWithText("Two Hundred Fifty Riddles, and the Long Subtitle That Comes With Them",
            useUnmergedTree = true).assertWidthIsAtLeast(160.dp).performTouchInput { click() }
        compose.runOnIdle { assertEquals(2, opens) }
    }

    private fun SemanticsNodeInteraction.assertProgress(expected: Float): SemanticsNodeInteraction =
        assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo,
            ProgressBarRangeInfo(expected, 0f..1f)))
}
