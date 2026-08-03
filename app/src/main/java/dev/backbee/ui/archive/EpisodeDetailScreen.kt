package dev.backbee.ui.archive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    val row = state.row
    val colors = backbeeColors

    if (row == null) {
        Column(modifier.fillMaxSize().background(colors.bgPage)) {}
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bgPage)
            .verticalScroll(rememberScrollState())
            .padding(Dimens.gutter),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Mono("← ARCHIVE", style = BackbeeType.monoSmall, color = colors.textMuted,
                modifier = Modifier.padding(end = Dimens.space3))
            Spacer(Modifier.weight(1f))
            Mono("[EP ${row.episodeNumber ?: (row.orderIndex + 1)}]", style = BackbeeType.monoSmall, color = colors.textMuted)
        }

        Spacer(Modifier.height(Dimens.space4))

        Text(
            text = (row.episodeNumber ?: (row.orderIndex + 1)).toString(),
            style = BackbeeType.displayMedium,
            color = colors.accentPrimary,
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

        BrutalButton(onClick = { player.playEpisode(row.id) }) {
            Label(
                if ((row.positionSeconds ?: 0) > 0) "Resume from ${ArchiveProgress.formatClock(row.positionSeconds ?: 0)}"
                else "Play from 00:00",
                color = colors.onAccentPrimary,
            )
        }

        Spacer(Modifier.height(Dimens.space3))

        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.space2)) {
            BrutalOutlineButton(onClick = viewModel::toggleStar, modifier = Modifier.weight(1f)) {
                Mono(
                    if (row.isStarred) "${Glyph.STARRED} STARRED" else "${Glyph.STARRED} STAR",
                    style = BackbeeType.monoSmall,
                    color = if (row.isStarred) colors.accentPrimary else colors.textPrimary,
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
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Label("Keep after playing", color = colors.textPrimary)
                Mono(
                    "EXEMPT FROM THE AUTOMATIC CLEANUP",
                    style = BackbeeType.monoMicro,
                    color = colors.textMuted,
                )
            }
            Switch(
                checked = row.isKept,
                onCheckedChange = { viewModel.setKeepAfterPlaying(it) },
            )
        }

        Spacer(Modifier.height(Dimens.space3))

        if (row.isDownloaded) {
            BrutalOutlineButton(onClick = viewModel::removeDownload, shadow = Shadow.sm) {
                Mono(
                    "DELETE DOWNLOAD" + (row.bytesDone?.takeIf { it > 0 }?.let { "  ·  ${it / (1024 * 1024)} MB →" } ?: ""),
                    style = BackbeeType.monoSmall,
                    color = colors.accentAlert,
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
