package dev.backbee.ui.now

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.backbee.core.playback.ArchiveProgress
import dev.backbee.core.playback.PlaybackSpeeds
import dev.backbee.data.db.EpisodeRow
import dev.backbee.playback.PlayerConnection
import dev.backbee.playback.PlayerState
import dev.backbee.ui.components.Artwork
import dev.backbee.ui.components.CarbonButton
import dev.backbee.ui.components.CarbonOutlineButton
import dev.backbee.ui.components.CarbonPanel
import dev.backbee.ui.components.EmptyState
import dev.backbee.ui.components.Glyph
import dev.backbee.ui.components.GlyphText
import dev.backbee.ui.components.Label
import dev.backbee.ui.components.Readout
import dev.backbee.ui.components.ScanBar
import dev.backbee.ui.theme.BackbeeType
import dev.backbee.ui.theme.Dimens
import dev.backbee.ui.theme.backbeeColors

/**
 * Direction 1A, "Spine" - the recommended default from the screens spec.
 *
 * Artwork, then a book-spine progress rail marked with years, then one enormous
 * RESUME. The spine is the whole idea made visible: your place somewhere in a
 * very long book, with everything read behind it and everything unread ahead.
 */
@Composable
fun NowScreen(
    viewModel: NowViewModel,
    player: PlayerConnection,
    onOpenArchive: () -> Unit,
    onOpenShelf: () -> Unit,
    onOpenCompletion: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val skipSeconds by viewModel.skipSeconds.collectAsStateWithLifecycle()
    val playerState by player.state.collectAsStateWithLifecycle()

    when {
        state.loading -> Box(modifier.fillMaxSize())

        !state.hasShow -> EmptyState(
            title = "The shelf is empty.",
            body = "Add one show. Start at its oldest episode. " +
                "Everything after that happens without you.",
            modifier = modifier,
            readout = listOf(
                "No account required",
                "No sync. no backend.",
                "One show at a time, on purpose.",
            ),
            action = { CarbonButton(onClick = onOpenShelf) { Label("Add a show", color = backbeeColors.onAccentPrimary) } },
        )

        // A finished archive still yields to the player: someone re-listening to
        // an episode should not be shown a recap instead of what they started.
        state.archiveComplete && state.nowPlaying == null -> EmptyState(
            title = "You finished the book.",
            body = "${state.show?.title} is complete. Take a look at how it went, " +
                "then pick the next one off the shelf.",
            modifier = modifier,
            action = {
                CarbonButton(onClick = { state.show?.let { onOpenCompletion(it.id) } }) {
                    Label("See the recap", color = backbeeColors.onAccentPrimary)
                }
            },
        )

        else -> SpineNow(
            state = state,
            playerState = playerState,
            player = player,
            skipSeconds = skipSeconds,
            onOpenArchive = onOpenArchive,
            onOpenCompletion = onOpenCompletion,
            onCycleSpeed = { viewModel.setSpeed(PlaybackSpeeds.next(state.show?.speed ?: 1f)) },
            onMarkPlayedAndNext = viewModel::markCurrentPlayedAndAdvance,
            modifier = modifier,
        )
    }
}

/** How long a player-vs-place mismatch must last before it counts as a detour. */
private const val DETOUR_CONFIRM_MS = 1_500L

@Composable
private fun SpineNow(
    state: NowUiState,
    playerState: PlayerState,
    player: PlayerConnection,
    /** Skip back and skip forward in seconds, for the key labels. */
    skipSeconds: Pair<Int, Int>,
    onOpenArchive: () -> Unit,
    onOpenCompletion: (Long) -> Unit,
    onCycleSpeed: () -> Unit,
    onMarkPlayedAndNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = backbeeColors
    // Whatever is loaded in the player, falling back to where you left off.
    // Reading your place here instead is the bug that made playing episode 500 leave
    // this screen describing episode 1.
    val target = state.current
    val playingThis = playerState.episodeId != null && playerState.episodeId == target?.id

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.gutter),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Artwork(state.show?.artworkUrl, state.show?.title, size = 88.dp)
            Column(Modifier.padding(start = Dimens.space4)) {
                Label(state.show?.title.orEmpty(), color = colors.textMuted)
                state.downloadedAhead.takeIf { it > 0 }?.let {
                    Text(
                        text = "$it downloaded",
                        style = BackbeeType.bodySmall,
                        color = colors.textFunctional,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(Dimens.space5))

        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = (target?.episodeNumber ?: ((target?.orderIndex ?: 0) + 1)).toString(),
                style = BackbeeType.displayMedium,
                color = colors.textAccent,
            )
            Text(
                text = target?.title.orEmpty(),
                style = BackbeeType.title,
                color = colors.textPrimary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(start = Dimens.space3, top = 4.dp)
                    .weight(1f),
            )
        }

        target?.let { row ->
            Text(
                text = listOfNotNull(
                    row.pubDate?.let { java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.US).format(java.util.Date(it)) },
                    row.durationSeconds?.let { ArchiveProgress.formatDuration(it) },
                ).joinToString("  ·  "),
                style = BackbeeType.bodySmall,
                color = colors.textMuted,
                modifier = Modifier.padding(top = Dimens.space2),
            )
        }

        Spacer(Modifier.height(Dimens.space6))

        state.progress?.let { progress ->
            Label("Archive spine")
            Spacer(Modifier.height(Dimens.space2))
            Spine(
                fraction = progress.percentByEpisodes / 100f,
                yearMarks = state.yearMarks,
                detourFraction = state.detourFraction,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Dimens.space2))
            Text(
                text = progress.summary(),
                style = BackbeeType.bodySmall,
                color = colors.textMuted,
            )
        }

        // Playing out of sequence is allowed, but it must never be silent: your
        // place has not moved, and from here auto-advance walks forward from
        // wherever the player is, not from where you left off.
        //
        // Only once the mismatch has survived a beat, though. At every
        // auto-advance the player moves to the next episode a moment before the
        // played flag commits, and in that gap this looks exactly like a detour.
        // A real detour lasts; the gap does not.
        var confirmedDetour by remember { mutableStateOf(false) }
        LaunchedEffect(state.isDetour) {
            if (state.isDetour) {
                kotlinx.coroutines.delay(DETOUR_CONFIRM_MS)
                confirmedDetour = true
            } else {
                confirmedDetour = false
            }
        }
        if (state.isDetour && confirmedDetour) {
            val leftOff = state.resumeTarget
            Spacer(Modifier.height(Dimens.space5))
            Readout(
                lines = listOf(
                    "Playing out of sequence",
                    "Your place is still ep ${leftOff?.episodeNumber ?: ((leftOff?.orderIndex ?: 0) + 1)}",
                ),
                tone = colors.onInverseAlert,
            )
            Spacer(Modifier.height(Dimens.space2))
            CarbonOutlineButton(onClick = { leftOff?.let { player.playEpisode(it.id) } }) {
                Label("← Back to where you left off", style = BackbeeType.bodySmall, color = colors.textPrimary)
            }
        }

        if (state.archiveComplete) {
            Spacer(Modifier.height(Dimens.space3))
            CarbonOutlineButton(onClick = { state.show?.let { onOpenCompletion(it.id) } }) {
                Label("Archive complete · see the recap", style = BackbeeType.bodySmall, color = colors.textPrimary)
            }
        }

        Spacer(Modifier.height(Dimens.space8))

        CarbonButton(
            onClick = {
                when {
                    playerState.isPlaying -> player.pause()
                    playingThis -> player.resume()
                    target != null -> player.playEpisode(target.id)
                    else -> player.resume()
                }
            },
            minHeight = 64.dp,
        ) {
            Label(
                text = if (playerState.isPlaying) "Pause" else if ((target?.positionSeconds ?: 0) > 0) "Resume" else "Play",
                style = BackbeeType.titleSmall,
                color = colors.onAccentPrimary,
            )
        }

        // The rewind about to be applied, stated before it happens rather than
        // discovered afterwards.
        state.resumeReadout.takeIf { it.isNotEmpty() && !playerState.isPlaying }?.let {
            Text(
                text = it,
                style = BackbeeType.bodySmall,
                color = colors.textSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(Dimens.space4))

        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.gap)) {
            TransportKey("−${skipSeconds.first}s", Modifier.weight(1f)) { player.skipBack() }
            TransportKey("+${skipSeconds.second}s", Modifier.weight(1f)) { player.skipForward() }
            // Labelled with the speed, so it had better change the speed. It
            // used to skip to the next episode, which is a bad surprise on a
            // key you press without looking.
            TransportKey(
                "${ArchiveProgress.formatSpeed(state.show?.speed ?: 1f)}×",
                Modifier.weight(1f),
            ) { onCycleSpeed() }
        }

        // The escape hatch for an episode already heard somewhere else: finish
        // it in one tap and let playback move on, instead of scrubbing to the
        // end or hunting the archive for the next one.
        if (playingThis) {
            Spacer(Modifier.height(Dimens.space2))
            CarbonOutlineButton(onClick = onMarkPlayedAndNext) {
                Label("✓ Mark played · next →", style = BackbeeType.bodySmall, color = colors.textPrimary)
            }
        }

        if (playingThis && playerState.durationMs > 0) {
            // The clock doubles as the way into the scan bar, which stays out
            // of sight until asked for - a permanently draggable strip next to
            // the transport keys is exactly the mis-tap they exist to avoid.
            var scanOpen by remember { mutableStateOf(false) }
            if (scanOpen) {
                ScanBar(
                    positionMs = playerState.positionMs,
                    durationMs = playerState.durationMs,
                    onSeek = player::seekTo,
                    onCollapse = { scanOpen = false },
                    modifier = Modifier.padding(top = Dimens.space3),
                )
            } else {
                Text(
                    text = "${ArchiveProgress.formatClock(playerState.positionSeconds)}   " +
                        "−${ArchiveProgress.formatClock(playerState.remainingMs / 1000)}   ⇄",
                    style = BackbeeType.body,
                    color = colors.textPrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClickLabel = "Scan through the episode") { scanOpen = true }
                        .heightIn(min = 48.dp)
                        .padding(top = Dimens.space3),
                    textAlign = TextAlign.Center,
                )
            }
        }

        Spacer(Modifier.height(Dimens.space8))

        if (state.upNext.isNotEmpty()) {
            Label("Next up")
            Spacer(Modifier.height(Dimens.space2))
            state.upNext.forEach { row ->
                UpNextRow(row) { player.playEpisode(row.id) }
                Spacer(Modifier.height(Dimens.space2))
            }
        }

        Spacer(Modifier.height(Dimens.space4))
        CarbonOutlineButton(
            onClick = onOpenArchive,
        ) {
            Label("Open the archive", color = colors.textPrimary)
        }
        Spacer(Modifier.height(Dimens.space8))
    }
}

/**
 * The book spine: a filled block for what has been read, ticks for year
 * boundaries, a hard marker at your place, and - only when the player has
 * wandered off somewhere else - a hollow one showing where it went.
 */
@Composable
private fun Spine(
    fraction: Float,
    yearMarks: List<Pair<Int, Float>>,
    detourFraction: Float? = null,
    modifier: Modifier = Modifier,
) {
    val colors = backbeeColors
    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(colors.layerSelected)
                .semantics { progressBarRangeInfo = ProgressBarRangeInfo(fraction.coerceIn(0f, 1f), 0f..1f) }

                .drawBehind {
                    val read = size.width * fraction.coerceIn(0f, 1f)
                    drawRect(colors.accentPrimary, Offset.Zero, Size(read, size.height))
                    yearMarks.forEach { (_, at) ->
                        val x = size.width * at.coerceIn(0f, 1f)
                        drawRect(colors.borderColor, Offset(x, 0f), Size(1.5f, size.height))
                    }
                    // Where the player is, when that is not where you left off.
                    // Drawn first so your place stays the mark on top.
                    detourFraction?.let { at ->
                        val x = size.width * at.coerceIn(0f, 1f)
                        drawRect(colors.textMuted, Offset(x - 1f, 0f), Size(2f, size.height))
                    }
                    // Your place itself, heavier than the year ticks.
                    drawRect(colors.textPrimary, Offset(read - 2f, 0f), Size(4f, size.height))
                },
        )
        if (yearMarks.size > 1) {
            Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
                Text(yearMarks.first().first.toString(), style = BackbeeType.labelSmall, color = colors.textMuted)
                Spacer(Modifier.weight(1f))
                Text(yearMarks.last().first.toString(), style = BackbeeType.labelSmall, color = colors.textMuted)
            }
        }
    }
}

@Composable
private fun TransportKey(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    CarbonOutlineButton(
        onClick = onClick,
        modifier = modifier,

        minHeight = Dimens.touchTarget,
    ) {
        Label(label, style = BackbeeType.body, color = backbeeColors.textPrimary)
    }
}

@Composable
private fun UpNextRow(row: EpisodeRow, onClick: () -> Unit) {
    val colors = backbeeColors
    CarbonPanel(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 48.dp),

        contentPadding = Dimens.space3,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = (row.episodeNumber ?: (row.orderIndex + 1)).toString(),
                style = BackbeeType.body,
                color = colors.textAccent,
                modifier = Modifier.padding(end = Dimens.space3),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = row.title,
                    style = BackbeeType.bodySmall,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    // The one thing worth knowing here: will the next hour work
                    // without signal?
                    text = if (row.isDownloaded) "On device" else "Not downloaded",
                    style = BackbeeType.labelSmall,
                    color = if (row.isDownloaded) colors.textFunctional else colors.textMuted,
                )
            }
            if (row.isDownloaded) {
                GlyphText(Glyph.DOWNLOADED, colors.textFunctional, Modifier.size(20.dp))
            }
        }
    }
}
