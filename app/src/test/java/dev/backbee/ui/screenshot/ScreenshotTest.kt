package dev.backbee.ui.screenshot

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import dev.backbee.data.db.DownloadState
import dev.backbee.data.db.EpisodeRow
import dev.backbee.ui.components.CarbonButton
import dev.backbee.ui.components.CarbonDivider
import dev.backbee.ui.components.CarbonOutlineButton
import dev.backbee.ui.components.CarbonPanel
import dev.backbee.ui.components.CarbonProgress
import dev.backbee.ui.components.CarbonDialog
import dev.backbee.ui.components.CarbonTextField
import dev.backbee.ui.components.CarbonToggle
import dev.backbee.ui.components.EpisodeRowItem
import dev.backbee.ui.components.Glyph
import dev.backbee.ui.components.GlyphText
import dev.backbee.ui.components.Label
import dev.backbee.ui.components.Mono
import dev.backbee.ui.components.NowPlayingBar
import dev.backbee.ui.components.Readout
import dev.backbee.ui.components.ScanBar
import dev.backbee.ui.components.StatusChip
import dev.backbee.ui.theme.BackbeeTheme
import dev.backbee.ui.theme.Dimens
import dev.backbee.ui.theme.backbeeColors
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the design system and the archive row to PNG on the JVM.
 *
 * This exists because the look cannot otherwise be checked without an emulator:
 * a build that compiles says nothing about whether the spacing works, the
 * states read, or the type is legible at arm's length. The images are written
 * to build/outputs/roborazzi and published by CI.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = RobolectricDeviceQualifiers.Pixel5)
class ScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `design system dark`() = shoot("design-system-dark", dark = true) { DesignSystemSheet() }

    @Test
    fun `design system light`() = shoot("design-system-light", dark = false) { DesignSystemSheet() }

    @Test
    fun `archive rows dark`() = shoot("archive-rows-dark", dark = true) { ArchiveRowStates() }

    @Test
    fun `archive rows light`() = shoot("archive-rows-light", dark = false) { ArchiveRowStates() }

    @Test
    fun `now playing bar dark`() = shoot("now-playing-bar-dark", dark = true, animated = true) { NowPlayingStates() }

    @Test
    fun `now playing bar light`() = shoot("now-playing-bar-light", dark = false, animated = true) { NowPlayingStates() }

    @Test
    fun `scan bar dark`() = shoot("scan-bar-dark", dark = true) { ScanBarStates() }

    @Test
    fun `scan bar light`() = shoot("scan-bar-light", dark = false) { ScanBarStates() }

    @Test
    fun `forms dark`() = shoot("carbon-forms-dark", dark = true) { FormStates() }

    @Test
    fun `forms light`() = shoot("carbon-forms-light", dark = false) { FormStates() }

    @Test
    fun `dialog dark`() = shootDialog("carbon-dialog-dark", dark = true, disabled = false)

    @Test
    fun `dialog light`() = shootDialog("carbon-dialog-light", dark = false, disabled = false)

    @Test
    fun `dialog pending dark`() = shootDialog("carbon-dialog-pending-dark", dark = true, disabled = true)

    @Test
    fun `dialog pending light`() = shootDialog("carbon-dialog-pending-light", dark = false, disabled = true)

    @Test
    fun `narrow large text playback dark`() = shoot(
        "playback-narrow-large-text-dark", dark = true, animated = true,
        width = 320.dp, fontScale = 2f,
    ) { NowPlayingStates() }

    @Test
    fun `narrow large text playback light`() = shoot(
        "playback-narrow-large-text-light", dark = false, animated = true,
        width = 320.dp, fontScale = 2f,
    ) { NowPlayingStates() }

    @Test
    fun `narrow large text scan dark`() = shoot(
        "scan-narrow-large-text-dark", dark = true, width = 320.dp, fontScale = 2f,
    ) { ScanBarStates() }

    @Test
    fun `narrow large text scan light`() = shoot(
        "scan-narrow-large-text-light", dark = false, width = 320.dp, fontScale = 2f,
    ) { ScanBarStates() }

    private fun shootDialog(name: String, dark: Boolean, disabled: Boolean) {
        compose.setContent {
            BackbeeTheme(darkTheme = dark) {
                CarbonDialog(
                    onDismissRequest = {},
                    title = { Label("Remove downloaded episode?") },
                    text = { Mono("Your listening progress and episode note will stay on this device.") },
                    confirmButton = {
                        CarbonButton(onClick = {}, enabled = !disabled,
                            background = backbeeColors.accentAlert,
                            contentColor = backbeeColors.onAccentAlert) {
                            Label(if (disabled) "Removing…" else "Remove")
                        }
                    },
                    dismissButton = { CarbonOutlineButton(onClick = {}) { Label("Cancel") } },
                )
            }
        }
        compose.onNodeWithText("Remove downloaded episode?").assertIsDisplayed()
        // A Dialog owns a separate window; capture its root rather than the empty host.
        compose.onNode(isDialog()).captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    /**
     * [animated] stops the test clock from being driven automatically. The
     * visualiser runs an infinite animation, and with autoAdvance on the tree
     * never reaches idle, so the capture would wait forever.
     */
    private fun shoot(
        name: String,
        dark: Boolean,
        animated: Boolean = false,
        width: Dp? = null,
        fontScale: Float = 1f,
        content: @Composable () -> Unit,
    ) {
        if (animated) compose.mainClock.autoAdvance = false
        compose.setContent {
            BackbeeTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                    Column((if (width == null) Modifier.fillMaxWidth() else Modifier.width(width))
                        .background(backbeeColors.bgPage).testTag("screenshot-fixture")) { content() }
                }
            }
        }
        // Far enough into the cycle that the bars are at different heights
        // rather than all at their starting value.
        if (animated) compose.mainClock.advanceTimeBy(420)
        compose.onNodeWithTag("screenshot-fixture").assertIsDisplayed()
            .captureRoboImage("build/outputs/roborazzi/$name.png")
    }
}

/** Explicit component fixture, not a substitute for a live app destination. */
@Composable
private fun FormStates() {
    Column(Modifier.padding(Dimens.gutter), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Label("Carbon form states")
        var note by remember { mutableStateOf("Remember this episode") }
        CarbonTextField(note, { note = it }, label = { Label("Episode note") })
        CarbonTextField("not a feed URL", {}, label = { Label("Feed URL · invalid") },
            singleLine = true, isError = true)
        CarbonTextField("Saved locally", {}, label = { Label("Disabled field") }, enabled = false)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column { Label("Off"); CarbonToggle(false, {}) }
            Column { Label("On"); CarbonToggle(true, {}) }
            Column { Label("Disabled"); CarbonToggle(true, {}, enabled = false) }
        }
        CarbonButton(onClick = {}) { Label("Save note") }
        CarbonButton(onClick = {}, enabled = false) { Label("Saving…") }
        CarbonOutlineButton(onClick = {}, enabled = false) { Label("Unavailable action") }
    }
}

/** Every component together, so the tokens can be judged as one system. */
@Composable
private fun DesignSystemSheet() {
    val colors = backbeeColors
    Column(
        Modifier.padding(Dimens.gutter),
        verticalArrangement = Arrangement.spacedBy(Dimens.space4),
    ) {
        Label("Backbee · Carbon components", color = colors.textPrimary)

        Mono("EP 312 OF 1,247 · 25% · ~340 HRS LEFT AT 1.6×", color = colors.textMuted)

        CarbonButton(onClick = {}) {
            Mono("RESUME", color = colors.onAccentPrimary)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.space2)) {
            CarbonOutlineButton(onClick = {}, modifier = Modifier.weight(1f)) {
                Mono("−10s", color = colors.textPrimary)
            }
            CarbonOutlineButton(onClick = {}, modifier = Modifier.weight(1f)) {
                Mono("+30s", color = colors.textPrimary)
            }
            CarbonOutlineButton(onClick = {}, modifier = Modifier.weight(1f)) {
                Mono("1.6×", color = colors.textPrimary)
            }
        }

        CarbonPanel(Modifier.fillMaxWidth()) {
            Label("Contextual layer")
            Mono("Flat surfaces · square controls · Plex type", color = colors.textMuted)
        }

        CarbonProgress(fraction = 0.25f, height = 14.dp)

        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.space2)) {
            StatusChip("Playing")
            StatusChip("Reading", background = colors.accentFunctional, contentColor = colors.onAccentSecondary)
            StatusChip("Frozen", background = colors.bgInverse, contentColor = colors.textInverse)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.space4)) {
            GlyphText(Glyph.PLAYED, colors.textMuted)
            GlyphText(Glyph.PLAYING, colors.accentPrimary)
            GlyphText(Glyph.DOWNLOADED, colors.accentFunctional)
            GlyphText(Glyph.STARRED, colors.accentPrimary)
            GlyphText(Glyph.UNTOUCHED, colors.textMuted)
            GlyphText(Glyph.FAILED, colors.accentAlert)
        }

        CarbonDivider()

        Readout(
            listOf(
                "FEED OK // 512 ITEMS DECLARED",
                "PAGED FEED: rel=next ABSENT",
                "FULL ARCHIVE AVAILABLE. NO IMPORT NEEDED.",
            )
        )

        Readout(
            listOf("QUEUE STALLED · 3 FAILED", "POSITION WRITES: LOCAL, UNAFFECTED."),
            tone = colors.onInverseAlert,
        )
    }
}

/**
 * The persistent bar in each state it can be in. It sits at the bottom of every
 * screen, so it has to survive a long title and read at a glance while walking.
 */
@Composable
private fun NowPlayingStates() {
    Column(
        Modifier.padding(vertical = Dimens.space4),
        verticalArrangement = Arrangement.spacedBy(Dimens.space4),
    ) {
        Label("Playing", modifier = Modifier.padding(horizontal = Dimens.gutter))
        NowPlayingBar(
            title = "A Very Normal Barn",
            subtitle = "EP 247",
            artworkUrl = null,
            positionMs = 1_601_000,
            durationMs = 4_680_000,
            isPlaying = true,
            isBuffering = false,
            onOpen = {}, onTogglePlay = {}, onSkipBack = {}, onSkipForward = {},
        )

        Label("Paused", modifier = Modifier.padding(horizontal = Dimens.gutter))
        NowPlayingBar(
            title = "Two Hundred Fifty Riddles, and the Long Subtitle That Comes With Them",
            subtitle = "EP 250",
            artworkUrl = null,
            positionMs = 240_000,
            durationMs = 4_680_000,
            isPlaying = false,
            isBuffering = false,
            onOpen = {}, onTogglePlay = {}, onSkipBack = {}, onSkipForward = {},
        )

        Label("Buffering", modifier = Modifier.padding(horizontal = Dimens.gutter))
        NowPlayingBar(
            title = "Sandwich Court",
            subtitle = "EP 251",
            artworkUrl = null,
            positionMs = 0,
            durationMs = 0,
            isPlaying = false,
            isBuffering = true,
            onOpen = {}, onTogglePlay = {}, onSkipBack = {}, onSkipForward = {},
        )
    }
}

/** The scan-through bar: shown only when invoked, so both trims are here. */
@Composable
private fun ScanBarStates() {
    Column(
        Modifier.padding(Dimens.gutter),
        verticalArrangement = Arrangement.spacedBy(Dimens.space4),
    ) {
        Label("Scan-through, mid-episode")
        ScanBar(positionMs = 1_601_000, durationMs = 4_680_000, onSeek = {})

        Label("Scan-through, with the collapse affordance")
        ScanBar(positionMs = 240_000, durationMs = 4_680_000, onSeek = {}, onCollapse = {})
    }
}

/** The archive row in each state it can actually be in. */
@Composable
private fun ArchiveRowStates() {
    Column {
        listOf(
            row(244, "Cousin Vibes", played = true),
            row(245, "The Haunted Escalator", played = true, starred = true),
            row(247, "A Very Normal Barn", position = 1601, playing = true),
            row(248, "Puzzle Boy Returns", downloaded = true),
            row(250, "Two Hundred Fifty Riddles", downloaded = true, starred = true, kept = true),
            row(251, "Sandwich Court", failed = true),
            row(252, "Gary's Third Divorce"),
        ).forEach { (data, playing) ->
            EpisodeRowItem(row = data, onClick = {}, isPlaying = playing)
            CarbonDivider()
        }
    }
}

private fun row(
    number: Int,
    title: String,
    played: Boolean = false,
    starred: Boolean = false,
    downloaded: Boolean = false,
    kept: Boolean = false,
    failed: Boolean = false,
    playing: Boolean = false,
    position: Long? = null,
): Pair<EpisodeRow, Boolean> = EpisodeRow(
    id = number.toLong(),
    showId = 1,
    orderIndex = number - 1,
    title = title,
    pubDate = 1_615_680_000_000L,
    durationSeconds = 4680,
    enclosureUrl = "https://cdn.example/$number.mp3",
    episodeNumber = number,
    enclosureBytes = 137_000_000,
    positionSeconds = position,
    played = played,
    playedAt = if (played) 1_615_680_000_000L else null,
    starred = starred,
    note = null,
    keepAfterPlaying = kept,
    downloadState = when {
        failed -> DownloadState.FAILED
        downloaded -> DownloadState.DONE
        else -> null
    },
    filePath = if (downloaded) "/data/$number.mp3" else null,
    bytesTotal = 137_000_000,
    bytesDone = if (downloaded) 137_000_000 else 0,
) to playing
