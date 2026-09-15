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
import dev.backbee.ui.components.CarbonOutlineButton
import dev.backbee.ui.components.CarbonPanel
import dev.backbee.ui.components.CarbonProgress
import dev.backbee.ui.components.Label
import dev.backbee.ui.components.Readout
import dev.backbee.ui.theme.BackbeeType
import dev.backbee.ui.theme.Dimens
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
            CarbonPanel(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth()) {
                    Label("Storage", color = colors.textPrimary)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${mb(state.usedBytes)} / ${gb(state.capBytes)}",
                        style = BackbeeType.body,
                        color = if (state.capReached) colors.textAlert else colors.textPrimary,
                    )
                }
                Spacer(Modifier.height(Dimens.space2))
                CarbonProgress(
                    fraction = state.fractionUsed,
                    height = 4.dp,
                    color = if (state.capReached) colors.textAlert else colors.textAccent,
                )
                if (state.capReached) {
                    Spacer(Modifier.height(Dimens.space2))
                    Text(
                        "Cap reached. ahead-queue trimmed. " +
                            "Raise the cap or shorten the delete-played window.",
                        style = BackbeeType.bodySmall,
                        color = colors.textAlert,
                    )
                }
                Row(
                    Modifier.padding(top = Dimens.space3),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
                ) {
                    CarbonOutlineButton(onClick = viewModel::raiseCap, modifier = Modifier.weight(1f)) {
                        Label("Raise cap +1GB", style = BackbeeType.bodySmall, color = colors.textPrimary)
                    }
                    CarbonOutlineButton(
                        onClick = viewModel::purgePlayed,
                        enabled = state.playedReclaimableBytes > 0,
                        modifier = Modifier.weight(1f),
                    ) {
                        Label(
                            "Purge played · ${mb(state.playedReclaimableBytes)}",
                            style = BackbeeType.bodySmall,
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
                            "Queue stalled · ${state.failed.size} failed",
                            "Position writes: local, unaffected.",
                            "Retry happens automatically on reconnect.",
                        ),
                        tone = colors.onInverseAlert,
                    )
                    CarbonOutlineButton(
                        onClick = viewModel::retryFailed,
                        modifier = Modifier.padding(top = Dimens.space2),
                    ) {
                        Label("Retry now", style = BackbeeType.bodySmall, color = colors.textPrimary)
                    }
                }
            }
            items(state.failed, key = { "f${it.id}" }) { row -> DownloadRow(row, "Failed", colors.textAlert) }
        }

        if (state.inProgress.isNotEmpty()) {
            item { Label("In progress") }
            items(state.inProgress, key = { "p${it.id}" }) { row ->
                CarbonPanel(Modifier.fillMaxWidth()) {
                    Text(row.title, style = BackbeeType.bodySmall, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(6.dp))
                    val total = row.bytesTotal ?: 0
                    val done = row.bytesDone ?: 0
                    CarbonProgress(if (total > 0) done.toFloat() / total else 0f)
                    Text(
                        "${mb(done)} / ${mb(total)}",
                        style = BackbeeType.labelSmall,
                        color = colors.textMuted,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }

        if (state.queued.isNotEmpty()) {
            item { Label("Waiting") }
            items(state.queued, key = { "q${it.id}" }) { row -> DownloadRow(row, "Waiting", colors.textMuted) }
        }

        item { Label("On device · ${state.onDevice.size}") }
        items(state.onDevice, key = { "d${it.id}" }) { row ->
            DownloadRow(
                row = row,
                status = if (row.isKept) "Kept" else mb(row.bytesDone ?: 0),
                color = if (row.isKept) colors.textAccent else colors.textFunctional,
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
        Text(
            (row.episodeNumber ?: (row.orderIndex + 1)).toString(),
            style = BackbeeType.bodySmall,
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
        Text(status, style = BackbeeType.bodySmall, color = color)
    }
}

private fun mb(bytes: Long) = "${bytes / (1024 * 1024)} MB"
private fun gb(bytes: Long) = "${bytes / (1024 * 1024 * 1024)} GB"
