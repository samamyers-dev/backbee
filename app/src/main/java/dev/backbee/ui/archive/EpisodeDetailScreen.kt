package dev.backbee.ui.archive

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.backbee.core.playback.ArchiveProgress
import dev.backbee.playback.PlayerConnection
import dev.backbee.ui.components.BrutalButton
import dev.backbee.ui.components.BrutalDivider
import dev.backbee.ui.components.BrutalOutlineButton
import dev.backbee.ui.components.Glyph
import dev.backbee.ui.components.Label
import dev.backbee.ui.components.Mono
import dev.backbee.ui.components.Readout
import dev.backbee.ui.theme.BackbeeType
import dev.backbee.ui.theme.Dimens
import dev.backbee.ui.theme.Shadow
import dev.backbee.ui.theme.backbeeColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun EpisodeDetailScreen(
    viewModel: EpisodeDetailViewModel,
    player: PlayerConnection,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val playerState by player.state.collectAsStateWithLifecycle()
    val row = state.row
    val colors = backbeeColors
    val scrollState = rememberScrollState()

    // Where the bulk section starts within the scrolling content, so opening it
    // can put it on screen. Its confirm buttons are two thirds of a page down
    // from the button that reveals them, which read as "nothing happened".
    var bulkTop by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(state.bulk.scope) {
        if (state.bulk.scope != null) scrollState.animateScrollTo(bulkTop.toInt())
    }

    if (row == null) {
        Column(modifier.fillMaxSize().background(colors.bgPage)) {}
        return
    }

    val loadedHere = playerState.episodeId == row.id

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bgPage)
            .verticalScroll(scrollState)
            .padding(Dimens.gutter),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Mono(
                text = "← ARCHIVE",
                style = BackbeeType.monoSmall,
                color = colors.textMuted,
                // The hit area is the padding, not the glyphs: eight characters
                // of 12sp mono is nowhere near a thumb.
                modifier = Modifier
                    .clickable(onClick = onBack)
                    .padding(end = Dimens.space3, top = Dimens.space3, bottom = Dimens.space3),
            )
            Spacer(Modifier.weight(1f))
            Mono("[EP ${row.episodeNumber ?: (row.orderIndex + 1)}]", style = BackbeeType.monoSmall, color = colors.textMuted)
        }

        Spacer(Modifier.height(Dimens.space4))

        Text(
            text = (row.episodeNumber ?: (row.orderIndex + 1)).toString(),
            style = BackbeeType.displayMedium,
            color = colors.textAccent,
        )
        Text(text = row.title, style = BackbeeType.title, color = colors.textPrimary)

        Mono(
            text = listOfNotNull(
                row.pubDate?.let { DATE.format(Date(it)).uppercase() },
                row.durationSeconds?.let { ArchiveProgress.formatDuration(it) },
                row.bytesDone?.takeIf { it > 0 }?.let { "${it / (1024 * 1024)} MB" },
                if (row.isDownloaded) "ON DEVICE" else null,
                if (row.isPlayed) "PLAYED" else row.progressFraction?.let { "${(it * 100).toInt()}% IN" },
            ).joinToString("  ·  "),
            style = BackbeeType.monoSmall,
            color = colors.textMuted,
            modifier = Modifier.padding(top = Dimens.space3),
        )

        Spacer(Modifier.height(Dimens.space5))

        // Once the player holds this episode the button is a transport control,
        // not a way in: offering "resume from 1:17" while 1:17 is playing invites
        // a tap that seeks backwards for no reason.
        BrutalButton(
            onClick = { if (loadedHere) player.togglePlayPause() else player.playEpisode(row.id) },
        ) {
            Label(
                when {
                    loadedHere && playerState.isPlaying -> "Pause"
                    loadedHere -> "Resume"
                    (row.positionSeconds ?: 0) > 0 ->
                        "Resume from ${ArchiveProgress.formatClock(row.positionSeconds ?: 0)}"
                    else -> "Play from 00:00"
                },
                color = colors.onAccentPrimary,
            )
        }

        Spacer(Modifier.height(Dimens.space3))

        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.space2)) {
            BrutalOutlineButton(onClick = viewModel::toggleStar, modifier = Modifier.weight(1f)) {
                Mono(
                    if (row.isStarred) "${Glyph.STARRED} STARRED" else "${Glyph.STARRED} STAR",
                    style = BackbeeType.monoSmall,
                    color = if (row.isStarred) colors.textAccent else colors.textPrimary,
                )
            }
            BrutalOutlineButton(
                onClick = { viewModel.setPlayed(!row.isPlayed) },
                modifier = Modifier.weight(1f),
            ) {
                Mono(
                    if (row.isPlayed) "MARK UNPLAYED" else "MARK PLAYED",
                    style = BackbeeType.monoSmall,
                    color = colors.textPrimary,
                )
            }
        }

        Spacer(Modifier.height(Dimens.space5))
        BrutalDivider()
        Spacer(Modifier.height(Dimens.space4))

        // Keep-after-playing: the exemption from the automatic cleanup that
        // otherwise reclaims every episode 24 hours after it is finished.
        Row(
            Modifier
                .fillMaxWidth()
                // toggleable merges the label and its description into one
                // announced control; a bare Switch says only "switch, off".
                .toggleable(
                    value = row.isKept,
                    onValueChange = { viewModel.setKeepAfterPlaying(it) },
                    role = Role.Switch,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Label("Keep after playing", color = colors.textPrimary)
                Mono(
                    "EXEMPT FROM THE AUTOMATIC CLEANUP",
                    style = BackbeeType.monoMicro,
                    color = colors.textMuted,
                )
            }
            Switch(checked = row.isKept, onCheckedChange = null)
        }

        Spacer(Modifier.height(Dimens.space3))

        if (row.isDownloaded) {
            BrutalOutlineButton(onClick = viewModel::removeDownload, shadow = Shadow.sm) {
                Mono(
                    "DELETE DOWNLOAD" + (row.bytesDone?.takeIf { it > 0 }?.let { "  ·  ${it / (1024 * 1024)} MB →" } ?: ""),
                    style = BackbeeType.monoSmall,
                    color = colors.textAlert,
                )
            }
        } else {
            BrutalOutlineButton(onClick = viewModel::downloadNow, shadow = Shadow.sm) {
                Mono("DOWNLOAD NOW", style = BackbeeType.monoSmall, color = colors.textPrimary)
            }
        }

        Spacer(Modifier.height(Dimens.space5))
        BrutalDivider()
        Spacer(Modifier.height(Dimens.space4))

        Column(Modifier.onGloballyPositioned { bulkTop = it.positionInParent().y }) {
            BulkSection(
                bulk = state.bulk,
                onOpen = viewModel::openBulk,
                onApply = viewModel::applyBulk,
                onUndo = viewModel::undoBulk,
                onDismiss = viewModel::dismissBulk,
            )
        }

        Spacer(Modifier.height(Dimens.space5))
        BrutalDivider()
        Spacer(Modifier.height(Dimens.space4))

        Label("Note")
        OutlinedTextField(
            value = state.noteDraft,
            onValueChange = viewModel::setNoteDraft,
            placeholder = { Mono("SOMETHING WORTH REMEMBERING", style = BackbeeType.monoSmall, color = colors.textMuted) },
            textStyle = BackbeeType.body,
            minLines = 3,
            modifier = Modifier.fillMaxWidth().padding(top = Dimens.space2),
        )
        BrutalOutlineButton(
            onClick = viewModel::saveNote,
            shadow = Shadow.sm,
            modifier = Modifier.padding(top = Dimens.space2),
        ) {
            Mono("SAVE NOTE", style = BackbeeType.monoSmall, color = colors.textPrimary)
        }

        state.description?.let { description ->
            Spacer(Modifier.height(Dimens.space5))
            BrutalDivider()
            Spacer(Modifier.height(Dimens.space4))
            Label("Description")
            Text(
                text = stripHtml(description),
                style = BackbeeType.body,
                color = colors.textMuted,
                modifier = Modifier.padding(top = Dimens.space2),
            )
        }

        Spacer(Modifier.height(Dimens.space16))
    }
}

/**
 * Marking a stretch of the archive played, and taking it back.
 *
 * Three steps on purpose. Opening only counts the range; the numbers are shown
 * before anything is written, because "mark 312 episodes played" is not a thing
 * to do by accident. After it runs, the exact rows that changed are held so a
 * single tap puts them back - including the ones that were part-way through,
 * which is why the undo restores saved positions rather than just clearing the
 * played flag.
 */
@Composable
private fun BulkSection(
    bulk: BulkUiState,
    onOpen: (BulkScope) -> Unit,
    onApply: (Boolean) -> Unit,
    onUndo: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = backbeeColors

    bulk.done?.let { done ->
        Readout(
            lines = listOf(
                "${done.count} EPISODES MARKED ${if (done.played) "PLAYED" else "UNPLAYED"}",
                "UNDO RESTORES THEM EXACTLY, POSITIONS AND ALL",
            ),
            tone = colors.onInverseFunctional,
        )
        Spacer(Modifier.height(Dimens.space2))
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.space2)) {
            BrutalOutlineButton(
                onClick = onUndo,
                enabled = !bulk.working,
                modifier = Modifier.weight(1f),
            ) {
                Mono("UNDO", style = BackbeeType.monoSmall, color = colors.textAlert)
            }
            BrutalOutlineButton(
                onClick = onDismiss,
                enabled = !bulk.working,
                modifier = Modifier.weight(1f),
            ) {
                Mono("KEEP", style = BackbeeType.monoSmall, color = colors.textPrimary)
            }
        }
        return
    }

    Label("The rest of the archive")
    Spacer(Modifier.height(Dimens.space2))

    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.space2)) {
        BrutalOutlineButton(
            onClick = { onOpen(BulkScope.BEFORE) },
            modifier = Modifier.weight(1f),
        ) {
            Mono("← ALL BEFORE", style = BackbeeType.monoSmall, color = colors.textPrimary)
        }
        BrutalOutlineButton(
            onClick = { onOpen(BulkScope.AFTER) },
            modifier = Modifier.weight(1f),
        ) {
            Mono("ALL AFTER →", style = BackbeeType.monoSmall, color = colors.textPrimary)
        }
    }

    val scope = bulk.scope ?: return
    val summary = bulk.summary ?: return
    val side = if (scope == BulkScope.BEFORE) "BEFORE" else "AFTER"

    Spacer(Modifier.height(Dimens.space3))

    if (summary.isEmpty) {
        Readout(listOf("NOTHING $side THIS EPISODE"))
        Spacer(Modifier.height(Dimens.space2))
        BrutalOutlineButton(onClick = onDismiss) {
            Mono("CLOSE", style = BackbeeType.monoSmall, color = colors.textPrimary)
        }
        return
    }

    Readout(
        lines = listOf(
            "${summary.total} EPISODES $side THIS ONE",
            "${summary.played} PLAYED · ${summary.unplayed} UNPLAYED",
            "THIS EPISODE IS NOT INCLUDED",
        ),
    )
    Spacer(Modifier.height(Dimens.space2))
    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.space2)) {
        BrutalButton(
            onClick = { onApply(true) },
            enabled = !bulk.working && summary.unplayed > 0,
            modifier = Modifier.weight(1f),
        ) {
            // "THEM", not "MARK PLAYED": the single-episode button of that exact
            // name is still on screen a few hundred pixels up.
            Mono("MARK THEM PLAYED", style = BackbeeType.monoSmall, color = colors.onAccentPrimary)
        }
        BrutalOutlineButton(
            onClick = { onApply(false) },
            enabled = !bulk.working && summary.played > 0,
            modifier = Modifier.weight(1f),
        ) {
            Mono("MARK THEM UNPLAYED", style = BackbeeType.monoSmall, color = colors.textPrimary)
        }
    }
    Spacer(Modifier.height(Dimens.space2))
    BrutalOutlineButton(onClick = onDismiss, enabled = !bulk.working) {
        Mono("CANCEL", style = BackbeeType.monoSmall, color = colors.textMuted)
    }
}

private val DATE = SimpleDateFormat("d MMMM yyyy", Locale.US)

/** Feed descriptions are full of markup; strip it rather than render a wall of tags. */
private fun stripHtml(raw: String): String = raw
    .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
    .replace(Regex("</p>", RegexOption.IGNORE_CASE), "\n\n")
    .replace(Regex("<[^>]*>"), "")
    .replace("&nbsp;", " ")
    .replace("&amp;", "&")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
    .trim()
