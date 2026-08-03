package dev.backbee.ui.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.backbee.data.db.EpisodeRow
import dev.backbee.ui.components.BrutalOutlineButton
import dev.backbee.ui.components.BrutalPanel
import dev.backbee.ui.components.BrutalProgress
import dev.backbee.ui.components.Label
import dev.backbee.ui.components.Mono
import dev.backbee.ui.components.Readout
import dev.backbee.ui.theme.BackbeeType
import dev.backbee.ui.theme.Dimens
import dev.backbee.ui.theme.Shadow
import dev.backbee.ui.theme.Stroke
import dev.backbee.ui.theme.backbeeColors

@Composable
fun DownloadsScreen(viewModel: DownloadsViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = backbeeColors

    LazyColumn(
        modifier = modifier.fillMaxSize().background(colors.bgPage),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(Dimens.gutter),
        verticalArrangement = Arrangement.spacedBy(Dimens.space3),
    ) {
        item {
            BrutalPanel(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth()) {
                    Label("Storage", color = colors.textPrimary)
                    Spacer(Modifier.weight(1f))
                    Mono(
                        "${mb(state.usedBytes)} / ${gb(state.capBytes)}",
                        style = BackbeeType.mono,
                        color = if (state.capReached) colors.accentAlert else colors.textPrimary,
                    )
                }
                Spacer(Modifier.height(Dimens.space2))
                BrutalProgress(
                    fraction = state.fractionUsed,
                    height = 14.dp,
                    color = if (state.capReached) colors.accentAlert else colors.accentPrimary,
                )
                if (state.capReached) {
                    Spacer(Modifier.height(Dimens.space2))
                    Mono(
                        "CAP REACHED. AHEAD-QUEUE TRIMMED. " +
                            "RAISE THE CAP OR SHORTEN THE DELETE-PLAYED WINDOW.",
                        style = BackbeeType.monoSmall,
                        color = colors.accentAlert,
                    )
                }
                Row(
                    Modifier.padding(top = Dimens.space3),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
                ) {
                    BrutalOutlineButton(onClick = viewModel::raiseCap, modifier = Modifier.weight(1f)) {
                        Mono("RAISE CAP +1GB", style = BackbeeType.monoSmall, color = colors.textPrimary)
                    }
                    BrutalOutlineButton(
                        onClick = viewModel::purgePlayed,
                        enabled = state.playedReclaimableBytes > 0,
                        modifier = Modifier.weight(1f),
                    ) {
                        Mono(
                            "PURGE PLAYED · ${mb(state.playedReclaimableBytes)}",
                            style = BackbeeType.monoSmall,
                            color = colors.textPrimary,
                        )
                    }
                }
            }
        }

        if (state.failed.isNotEmpty()) {
            item {
                Column {
                    Readout(
                        lines = listOf(
                            "QUEUE STALLED · ${state.failed.size} FAILED",
                            "POSITION WRITES: LOCAL, UNAFFECTED.",
                            "RETRY HAPPENS AUTOMATICALLY ON RECONNECT.",
                        ),
                        tone = colors.onInverseAlert,
                    )
                    BrutalOutlineButton(
                        onClick = viewModel::retryFailed,
                        modifier = Modifier.padding(top = Dimens.space2),
                    ) {
                        Mono("RETRY NOW", style = BackbeeType.monoSmall, color = colors.textPrimary)
                    }
                }
            }
            items(state.failed, key = { "f${it.id}" }) { row -> DownloadRow(row, "FAILED", colors.accentAlert) }
        }

        if (state.inProgress.isNotEmpty()) {
            item { Label("In progress") }
            items(state.inProgress, key = { "p${it.id}" }) { row ->
                BrutalPanel(Modifier.fillMaxWidth(), shadow = Shadow.sm, borderWidth = Stroke.thin) {
                    Text(row.title, style = BackbeeType.bodySmall, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(6.dp))
                    val total = row.bytesTotal ?: 0
                    val done = row.bytesDone ?: 0
                    BrutalProgress(if (total > 0) done.toFloat() / total else 0f)
                    Mono(
                        "${mb(done)} / ${mb(total)}",
                        style = BackbeeType.monoMicro,
                        color = colors.textMuted,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }

        if (state.queued.isNotEmpty()) {
            item { Label("Waiting") }
            items(state.queued, key = { "q${it.id}" }) { row -> DownloadRow(row, "WAITING", colors.textMuted) }
        }

        item { Label("On device · ${state.onDevice.size}") }
        items(state.onDevice, key = { "d${it.id}" }) { row ->
            DownloadRow(
                row = row,
                status = if (row.isKept) "KEPT" else mb(row.bytesDone ?: 0),
                color = if (row.isKept) colors.accentPrimary else colors.accentFunctional,
            )
        }
    }
}

@Composable
private fun DownloadRow(row: EpisodeRow, status: String, color: androidx.compose.ui.graphics.Color) {
    val colors = backbeeColors
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Mono(
            (row.episodeNumber ?: (row.orderIndex + 1)).toString(),
            style = BackbeeType.monoSmall,
            color = colors.textMuted,
            modifier = Modifier.padding(end = Dimens.space3),
        )
        Text(
            row.title,
            style = BackbeeType.bodySmall,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Mono(status, style = BackbeeType.monoSmall, color = color)
    }
}

private fun mb(bytes: Long) = "${bytes / (1024 * 1024)} MB"
private fun gb(bytes: Long) = "${bytes / (1024 * 1024 * 1024)} GB"
