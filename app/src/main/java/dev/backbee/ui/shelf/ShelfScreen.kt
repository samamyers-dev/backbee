package dev.backbee.ui.shelf

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.backbee.ui.components.Artwork
import dev.backbee.ui.components.BrutalButton
import dev.backbee.ui.components.BrutalOutlineButton
import dev.backbee.ui.components.BrutalPanel
import dev.backbee.ui.components.Label
import dev.backbee.ui.components.Mono
import dev.backbee.ui.components.Readout
import dev.backbee.ui.components.StatusChip
import dev.backbee.ui.theme.BackbeeType
import dev.backbee.ui.theme.Dimens
import dev.backbee.ui.theme.Shadow
import dev.backbee.ui.theme.Stroke
import dev.backbee.ui.theme.backbeeColors

/**
 * The shelf: the active show on top, everything else waiting with its place
 * frozen exactly where it was left.
 */
@Composable
fun ShelfScreen(
    viewModel: ShelfViewModel,
    onOpenCompletion: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val addState by viewModel.addState.collectAsStateWithLifecycle()
    var pendingRemoval by remember { mutableStateOf<Long?>(null) }
    val colors = backbeeColors

    LazyColumn(
        modifier = modifier.fillMaxSize().background(colors.bgPage),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(Dimens.gutter),
        verticalArrangement = Arrangement.spacedBy(Dimens.space3),
    ) {
        item {
            Row(Modifier.fillMaxWidth()) {
                Label(
                    "[Shelf] ${entries.size} ${if (entries.size == 1) "show" else "shows"}",
                    color = colors.textPrimary,
                )
                Spacer(Modifier.weight(1f))
                Mono(
                    "${entries.count { it.show.isActive }} ACTIVE",
                    style = BackbeeType.monoSmall,
                    color = colors.textAccent,
                )
            }
        }

        item {
            BrutalPanel(Modifier.fillMaxWidth()) {
                Label("Add a show")
                OutlinedTextField(
                    value = addState.query,
                    onValueChange = viewModel::setQuery,
                    placeholder = { Mono("RSS URL OR SEARCH TERM", style = BackbeeType.monoSmall, color = colors.textMuted) },
                    singleLine = true,
                    textStyle = BackbeeType.mono,
                    modifier = Modifier.fillMaxWidth().padding(top = Dimens.space2),
                )
                Row(
                    Modifier.padding(top = Dimens.space2),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
                ) {
                    BrutalButton(
                        onClick = { viewModel.addByUrl(addState.query) },
                        enabled = !addState.adding && addState.query.isNotBlank(),
                        shadow = Shadow.sm,
                        minHeight = 48.dp,
                        modifier = Modifier.weight(1f),
                    ) {
                        Mono(
                            if (addState.adding) "FETCHING…" else "FETCH FEED",
                            style = BackbeeType.monoSmall,
                            color = colors.onAccentPrimary,
                        )
                    }
                    BrutalOutlineButton(
                        onClick = viewModel::search,
                        enabled = !addState.searching && addState.query.isNotBlank(),
                        modifier = Modifier.weight(1f),
                    ) {
                        Mono(
                            if (addState.searching) "SEARCHING…" else "SEARCH INDEX",
                            style = BackbeeType.monoSmall,
                            color = colors.textPrimary,
                        )
                    }
                }
            }
        }

        // Phase 0, surfaced. "This feed only has the last 300 of 1,247" is
        // something to learn now, not 300 episodes in.
        if (addState.probeLines.isNotEmpty()) {
            item {
                Column {
                    Label("Archive completeness check")
                    Spacer(Modifier.height(Dimens.space2))
                    Readout(
                        lines = addState.probeLines,
                        tone = if (addState.probeUsable) colors.onInverseFunctional else colors.onInverseAlert,
                    )
                    TextButton(onClick = viewModel::dismissMessage) {
                        Mono("DISMISS", style = BackbeeType.monoSmall, color = colors.textMuted)
                    }
                }
            }
        }

        if (addState.results.isNotEmpty()) {
            item { Label("Search results") }
            items(addState.results, key = { it.feedUrl }) { result ->
                BrutalPanel(Modifier.fillMaxWidth(), shadow = Shadow.sm, borderWidth = Stroke.thin) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Artwork(result.artworkUrl, result.title)
                        Column(Modifier.weight(1f).padding(horizontal = Dimens.space3)) {
                            Text(
                                result.title,
                                style = BackbeeType.titleSmall,
                                color = colors.textPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Mono(
                                listOfNotNull(result.author, result.episodeCount?.let { "$it EPISODES" })
                                    .joinToString(" · ").uppercase(),
                                style = BackbeeType.monoMicro,
                                color = colors.textMuted,
                            )
                        }
                        BrutalButton(
                            onClick = { viewModel.addByUrl(result.feedUrl) },
                            shadow = Shadow.sm,
                            minHeight = 40.dp,
                            modifier = Modifier.width(84.dp),
                        ) {
                            Mono("ADD", style = BackbeeType.monoSmall, color = colors.onAccentPrimary)
                        }
                    }
                }
            }
        }

        if (entries.isNotEmpty()) {
            item { Label("Shows") }
            items(entries, key = { it.show.id }) { entry ->
                BrutalPanel(
                    Modifier.fillMaxWidth(),
                    borderWidth = if (entry.show.isActive) Stroke.thick else Stroke.divider,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Artwork(entry.show.artworkUrl, entry.show.title)
                        Column(Modifier.weight(1f).padding(start = Dimens.space3)) {
                            when {
                                entry.show.isActive -> StatusChip("Reading")
                                entry.isComplete -> StatusChip(
                                    "Completed",
                                    background = colors.accentFunctional,
                                    contentColor = colors.onAccentSecondary,
                                )
                                else -> StatusChip(
                                    "Bookmarked — frozen",
                                    background = colors.bgInverse,
                                    contentColor = colors.textInverse,
                                )
                            }
                            Text(
                                entry.show.title,
                                style = BackbeeType.titleSmall,
                                color = colors.textPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                            Mono(
                                entry.placeLine(),
                                style = BackbeeType.monoSmall,
                                color = colors.textMuted,
                            )
                        }
                    }

                    Row(
                        Modifier.padding(top = Dimens.space3),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
                    ) {
                        if (!entry.show.isActive) {
                            // The only promotion action there is.
                            BrutalButton(
                                onClick = { viewModel.makeActive(entry.show.id) },
                                shadow = Shadow.sm,
                                minHeight = 44.dp,
                                modifier = Modifier.weight(1f),
                            ) {
                                Mono("ACTIVATE", style = BackbeeType.monoSmall, color = colors.onAccentPrimary)
                            }
                        }
                        if (entry.isComplete) {
                            BrutalOutlineButton(
                                onClick = { onOpenCompletion(entry.show.id) },
                                minHeight = 44.dp,
                                modifier = Modifier.weight(1f),
                            ) {
                                Mono("RECAP", style = BackbeeType.monoSmall, color = colors.textPrimary)
                            }
                        }
                        BrutalOutlineButton(
                            onClick = { pendingRemoval = entry.show.id },
                            minHeight = 44.dp,
                        ) {
                            Mono("REMOVE", style = BackbeeType.monoSmall, color = colors.textAlert)
                        }
                    }
                }
            }
        }
    }

    pendingRemoval?.let { showId ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Label("Remove this show?", color = colors.textPrimary) },
            text = {
                Text(
                    "Its episodes, downloads, position and notes are all deleted. This cannot be undone.",
                    style = BackbeeType.body,
                    color = colors.textMuted,
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.removeShow(showId); pendingRemoval = null }) {
                    Mono("REMOVE", style = BackbeeType.monoSmall, color = colors.textAlert)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoval = null }) {
                    Mono("KEEP", style = BackbeeType.monoSmall, color = colors.textPrimary)
                }
            },
        )
    }
}
