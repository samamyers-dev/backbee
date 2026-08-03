package dev.backbee.ui.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import dev.backbee.data.db.DownloadState
import dev.backbee.data.db.EpisodeRow
import dev.backbee.ui.components.BrutalButton
import dev.backbee.ui.components.BrutalDivider
import dev.backbee.ui.components.BrutalOutlineButton
import dev.backbee.ui.components.BrutalPanel
import dev.backbee.ui.components.BrutalProgress
import dev.backbee.ui.components.EpisodeRowItem
import dev.backbee.ui.components.Glyph
import dev.backbee.ui.components.GlyphText
import dev.backbee.ui.components.Label
import dev.backbee.ui.components.Mono
import dev.backbee.ui.components.NowPlayingBar
import dev.backbee.ui.components.Readout
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
 * a build that compiles says nothing about whether the shadows land, the
 * borders read, or the type is legible at arm's length. The images are written
 * to build/outputs/roborazzi and published by CI.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel5)
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

    /**
     * [animated] stops the test clock from being driven automatically. The
     * visualiser runs an infinite animation, and with autoAdvance on the tree
     * never reaches idle, so the capture would wait forever.
     */
    private fun shoot(
        name: String,
        dark: Boolean,
        animated: Boolean = false,
        content: @Composable () -> Unit,
    ) {
        if (animated) compose.mainClock.autoAdvance = false
        compose.setContent {
            BackbeeTheme(darkTheme = dark) {
                Column(Modifier.fillMaxWidth().background(backbeeColors.bgPage)) { content() }
            }
        }
        // Far enough into the cycle that the bars are at different heights
        // rather than all at their starting value.
        if (animated) compose.mainClock.advanceTimeBy(420)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
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
        Label("Backbee · FREE THEM.", color = colors.textPrimary)

        Mono("EP 312 OF 1,247 · 25% · ~340 HRS LEFT AT 1.6×", color = colors.textMuted)

        BrutalButton(onClick = {}) {
            Mono("RESUME", color = colors.onAccentPrimary)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.space2)) {
            BrutalOutlineButton(onClick = {}, modifier = Modifier.weight(1f)) {
                Mono("−10s", color = colors.textPrimary)
            }
            BrutalOutlineButton(onClick = {}, modifier = Modifier.weight(1f)) {
                Mono("+30s", color = colors.textPrimary)
            }
            BrutalOutlineButton(onClick = {}, modifier = Modifier.weight(1f)) {
                Mono("1.6×", color = colors.textPrimary)
            }
        }

        BrutalPanel(Modifier.fillMaxWidth()) {
            Label("Panel with a hard offset shadow")
            Mono("BORDERED, SQUARE, NO BLUR", color = colors.textMuted)
        }

        BrutalProgress(fraction = 0.25f, height = 14.dp)

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

        BrutalDivider()

        Readout(
            listOf(
                "FEED OK // 512 ITEMS DECLARED",
                "PAGED FEED: rel=next ABSENT",
                "FULL ARCHIVE AVAILABLE. NO IMPORT NEEDED.",
            )
        )

        Readout(
            listOf("QUEUE STALLED · 3 FAILED", "POSITION WRITES: LOCAL, UNAFFECTED."),
            tone = colors.accentAlert,
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
            onOpen = {}, onTogglePlay = {}, onSkipBack = {},
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
            onOpen = {}, onTogglePlay = {}, onSkipBack = {},
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
            onOpen = {}, onTogglePlay = {}, onSkipBack = {},
        )
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
            BrutalDivider()
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
