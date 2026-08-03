package dev.backbee.ui.now

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.backbee.core.playback.ArchiveProgress
import dev.backbee.data.db.EpisodeRow
import dev.backbee.playback.PlayerConnection
import dev.backbee.playback.PlayerState
import dev.backbee.ui.components.Artwork
import dev.backbee.ui.components.BrutalButton
import dev.backbee.ui.components.BrutalOutlineButton
import dev.backbee.ui.components.BrutalPanel
import dev.backbee.ui.components.EmptyState
import dev.backbee.ui.components.Glyph
import dev.backbee.ui.components.GlyphText
import dev.backbee.ui.components.Label
import dev.backbee.ui.components.Mono
import dev.backbee.ui.components.Readout
import dev.backbee.ui.theme.BackbeeType
import dev.backbee.ui.theme.Dimens
import dev.backbee.ui.theme.Shadow
import dev.backbee.ui.theme.Stroke
import dev.backbee.ui.theme.backbeeColors

/**
 * Direction 1A, "Spine" - the recommended default from the screens spec.
 *
 * Artwork, then a book-spine progress rail marked with years, then one enormous
 * RESUME. The spine is the whole idea made visible: a bookmark somewhere in a
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
    val playerState by player.state.collectAsStateWithLifecycle()

    when {
        state.loading -> Box(modifier.fillMaxSize())

        !state.hasShow -> EmptyState(
            title = "The shelf is empty.",
            body = "Add one show. Start at its oldest episode. " +
                "Everything after that happens without you.",
            modifier = modifier,
            readout = listOf(
                "NO ACCOUNT REQUIRED",
                "NO SYNC. NO BACKEND.",
                "ONE SHOW AT A TIME, ON PURPOSE.",
            ),
            action = { BrutalButton(onClick = onOpenShelf) { Label("Add a show", color = backbeeColors.onAccentPrimary) } },
        )

        // A finished archive still yields to the player: someone re-listening to
        // an episode should not be shown a recap instead of what they started.
        state.archiveComplete && state.nowPlaying == null -> EmptyState(
            title = "You finished the book.",
            body = "${state.show?.title} is complete. Take a look at how it went, " +
                "then pick the next one off the shelf.",
            modifier = modifier,
            action = {
                BrutalButton(onClick = { state.show?.let { onOpenCompletion(it.id) } }) {
                    Label("See the recap", color = backbeeColors.onAccentPrimary)
                }
            },
        )

        else -> SpineNow(
            state = state,
            playerState = playerState,
            player = player,
            onOpenArchive = onOpenArchive,
            onOpenCompletion = onOpenCompletion,
            onCycleSpeed = { viewModel.setSpeed(nextSpeed(state.show?.speed ?: 1f)) },
            modifier = modifier,
        )
    }
}

/** The speeds worth having on a one-tap key; wraps back to 1×. */
private val SPEEDS = listOf(1.0f, 1.2f, 1.4f, 1.6f, 1.8f, 2.0f)

private fun nextSpeed(current: Float): Float {
    val index = SPEEDS.indexOfFirst { it > current + 0.01f }
    return if (index == -1) SPEEDS.first() else SPEEDS[index]
}

@Composable
private fun SpineNow(
    state: NowUiState,
    playerState: PlayerState,
    player: PlayerConnection,
    onOpenArchive: () -> Unit,
    onOpenCompletion: (Long) -> Unit,
    onCycleSpeed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = backbeeColors
    // Whatever is loaded in the player, falling back to the bookmark. Reading
    // the bookmark here instead is the bug that made playing episode 500 leave
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
                    Mono(
                        text = "$it DOWNLOADED",
                        style = BackbeeType.monoSmall,
                        color = colors.accentFunctional,
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
                color = colors.accentPrimary,
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
            Mono(
                text = listOfNotNull(
                    row.pubDate?.let { java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.US).format(java.util.Date(it)).uppercase() },
                    row.durationSeconds?.let { ArchiveProgress.formatDuration(it) },
                ).joinToString("  ·  "),
                style = BackbeeType.monoSmall,
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
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Dimens.space2))
            Mono(
                text = progress.summary().uppercase(),
                style = BackbeeType.monoSmall,
                color = colors.textMuted,
            )
        }

        // Playing out of sequence is allowed, but it must never be silent: your
        // place has not moved, and from here auto-advance walks forward from
        // wherever the player is, not from where you left off.
        if (state.isDetour) {
            val leftOff = state.resumeTarget
            Spacer(Modifier.height(Dimens.space5))
            Readout(
                lines = listOf(
                    "PLAYING OUT OF SEQUENCE",
                    "YOUR PLACE IS STILL EP ${leftOff?.episodeNumber ?: ((leftOff?.orderIndex ?: 0) + 1)}",
                ),
                tone = colors.onInverseAlert,
            )
            Spacer(Modifier.height(Dimens.space2))
            BrutalOutlineButton(onClick = { leftOff?.let { player.playEpisode(it.id) } }) {
                Mono("← BACK TO WHERE YOU LEFT OFF", style = BackbeeType.monoSmall, color = colors.textPrimary)
            }
        }

        if (state.archiveComplete) {
            Spacer(Modifier.height(Dimens.space3))
            BrutalOutlineButton(onClick = { state.show?.let { onOpenCompletion(it.id) } }) {
                Mono("ARCHIVE COMPLETE · SEE THE RECAP", style = BackbeeType.monoSmall, color = colors.textPrimary)
            }
        }

        Spacer(Modifier.height(Dimens.space8))

        BrutalButton(
            onClick = {
                when {
                    playerState.isPlaying -> player.pause()
                    playingThis -> player.resume()
                    target != null -> player.playEpisode(target.id)
                    else -> player.resume()
                }
            },
            minHeight = 96.dp,
        ) {
            Text(
                text = if (playerState.isPlaying) "PAUSE" else if ((target?.positionSeconds ?: 0) > 0) "RESUME" else "PLAY",
                style = BackbeeType.displaySmall,
                color = colors.onAccentPrimary,
            )
        }

        // The rewind about to be applied, stated before it happens rather than
        // discovered afterwards.
        state.resumeReadout.takeIf { it.isNotEmpty() && !playerState.isPlaying }?.let {
            Mono(
                text = it,
                style = BackbeeType.monoSmall,
                color = colors.accentSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(Dimens.space4))

        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.gap)) {
            TransportKey("−10s", Modifier.weight(1f)) { player.skipBack() }
            TransportKey("+30s", Modifier.weight(1f)) { player.skipForward() }
            // Labelled with the speed, so it had better change the speed. It
            // used to skip to the next episode, which is a bad surprise on a
            // key you press without looking.
            TransportKey(
                "${ArchiveProgress.formatSpeed(state.show?.speed ?: 1f)}×",
                Modifier.weight(1f),
            ) { onCycleSpeed() }
        }

        if (playingThis && playerState.durationMs > 0) {
            Mono(
                text = "${ArchiveProgress.formatClock(playerState.positionSeconds)}   " +
                    "−${ArchiveProgress.formatClock(playerState.remainingMs / 1000)}",
                style = BackbeeType.mono,
                color = colors.textPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Dimens.space3),
                textAlign = TextAlign.Center,
            )
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
        BrutalButton(
            onClick = onOpenArchive,
            background = colors.bgPanel,
            contentColor = colors.textPrimary,
            shadow = Shadow.sm,
        ) {
            Label("Open the archive", color = colors.textPrimary)
        }
        Spacer(Modifier.height(Dimens.space8))
    }
}

/**
 * The book spine: a filled block for what has been read, ticks for year
 * boundaries, and a hard marker at the bookmark.
 */
@Composable
private fun Spine(
    fraction: Float,
    yearMarks: List<Pair<Int, Float>>,
    modifier: Modifier = Modifier,
) {
    val colors = backbeeColors
    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(36.dp)
                .background(colors.bgPanel)
                .border(Stroke.divider, colors.borderColor)
                .drawBehind {
                    val read = size.width * fraction.coerceIn(0f, 1f)
                    drawRect(colors.accentPrimary, Offset.Zero, Size(read, size.height))
                    yearMarks.forEach { (_, at) ->
                        val x = size.width * at.coerceIn(0f, 1f)
                        drawRect(colors.borderColor, Offset(x, 0f), Size(1.5f, size.height))
                    }
                    // The bookmark itself, heavier than the year ticks.
                    drawRect(colors.accentSecondary, Offset(read - 2f, 0f), Size(4f, size.height))
                },
        )
        if (yearMarks.size > 1) {
            Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
                Mono(yearMarks.first().first.toString(), style = BackbeeType.monoMicro, color = colors.textMuted)
                Spacer(Modifier.weight(1f))
                Mono(yearMarks.last().first.toString(), style = BackbeeType.monoMicro, color = colors.textMuted)
            }
        }
    }
}

@Composable
private fun TransportKey(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    BrutalButton(
        onClick = onClick,
        modifier = modifier,
        background = backbeeColors.bgPanel,
        contentColor = backbeeColors.textPrimary,
        shadow = Shadow.sm,
        minHeight = Dimens.touchTarget,
    ) {
        Mono(label, style = BackbeeType.mono, color = backbeeColors.textPrimary)
    }
}

@Composable
private fun UpNextRow(row: EpisodeRow, onClick: () -> Unit) {
    val colors = backbeeColors
    BrutalPanel(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shadow = Shadow.sm,
        borderWidth = Stroke.thin,
        contentPadding = Dimens.space3,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Mono(
                text = (row.episodeNumber ?: (row.orderIndex + 1)).toString(),
                style = BackbeeType.mono,
                color = colors.accentPrimary,
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
                Mono(
                    // The one thing worth knowing here: will the next hour work
                    // without signal?
                    text = if (row.isDownloaded) "ON DEVICE" else "NOT DOWNLOADED",
                    style = BackbeeType.monoMicro,
                    color = if (row.isDownloaded) colors.accentFunctional else colors.textMuted,
                )
            }
            if (row.isDownloaded) {
                GlyphText(Glyph.DOWNLOADED, colors.accentFunctional, Modifier.size(20.dp))
            }
        }
    }
}
