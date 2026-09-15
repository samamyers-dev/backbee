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
import dev.backbee.ui.components.CarbonDialog
import dev.backbee.ui.components.CarbonTextField
import androidx.compose.material3.Text
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
import dev.backbee.ui.components.CarbonButton
import dev.backbee.ui.components.CarbonOutlineButton
import dev.backbee.ui.components.CarbonPanel
import dev.backbee.ui.components.Label
import dev.backbee.ui.components.Readout
import dev.backbee.ui.components.StatusChip
import dev.backbee.ui.theme.BackbeeType
import dev.backbee.ui.theme.Dimens
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
                    "Shelf · ${entries.size} ${if (entries.size == 1) "show" else "shows"}",
                    color = colors.textPrimary,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${entries.count { it.show.isActive }} active",
                    style = BackbeeType.bodySmall,
                    color = colors.textAccent,
                )
            }
        }

        item {
            CarbonPanel(Modifier.fillMaxWidth()) {
                Label("Add a show")
                CarbonTextField(
                    value = addState.query,
                    onValueChange = viewModel::setQuery,
                    label = { Label("Feed URL or search term", style = BackbeeType.labelSmall) },
                    accessibleLabel = "Feed URL or search term",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = Dimens.space2),
                )
                Row(
                    Modifier.padding(top = Dimens.space2),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
                ) {
                    CarbonButton(
                        onClick = { viewModel.addByUrl(addState.query) },
                        enabled = !addState.adding && addState.query.isNotBlank(),

                        minHeight = 48.dp,
                        modifier = Modifier.weight(1f),
                    ) {
                        Label(
                            if (addState.adding) "Fetching…" else "Fetch feed",
                            style = BackbeeType.bodySmall,
                            color = colors.onAccentPrimary,
                        )
                    }
                    CarbonOutlineButton(
                        onClick = viewModel::search,
                        enabled = !addState.searching && addState.query.isNotBlank(),
                        modifier = Modifier.weight(1f),
                    ) {
                        Label(
                            if (addState.searching) "Searching…" else "Search index",
                            style = BackbeeType.bodySmall,
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
                    CarbonOutlineButton(onClick = viewModel::dismissMessage) {
                        Label("Dismiss", style = BackbeeType.bodySmall, color = colors.textMuted)
                    }
                }
            }
        }

        if (addState.results.isNotEmpty()) {
            item { Label("Search results") }
            items(addState.results, key = { it.feedUrl }) { result ->
                CarbonPanel(Modifier.fillMaxWidth()) {
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
                            Text(
                                listOfNotNull(result.author, result.episodeCount?.let { "$it episodes" })
                                    .joinToString(" · "),
                                style = BackbeeType.labelSmall,
                                color = colors.textMuted,
                            )
                        }
                        CarbonButton(
                            onClick = { viewModel.addByUrl(result.feedUrl) },

                            minHeight = 48.dp,
                            modifier = Modifier.width(84.dp),
                        ) {
                            Label("Add", style = BackbeeType.bodySmall, color = colors.onAccentPrimary)
                        }
                    }
                }
            }
        }

        if (entries.isNotEmpty()) {
            item { Label("Shows") }
            items(entries, key = { it.show.id }) { entry ->
                CarbonPanel(
                    Modifier.fillMaxWidth(),
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
                            Text(
                                entry.placeLine(),
                                style = BackbeeType.bodySmall,
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
                            CarbonButton(
                                onClick = { viewModel.makeActive(entry.show.id) },

                                minHeight = 48.dp,
                                modifier = Modifier.weight(1f),
                            ) {
                                Label("Activate", style = BackbeeType.bodySmall, color = colors.onAccentPrimary)
                            }
                        }
                        if (entry.isComplete) {
                            CarbonOutlineButton(
                                onClick = { onOpenCompletion(entry.show.id) },
                                minHeight = 48.dp,
                                modifier = Modifier.weight(1f),
                            ) {
                                Label("Recap", style = BackbeeType.bodySmall, color = colors.textPrimary)
                            }
                        }
                        CarbonOutlineButton(
                            onClick = { pendingRemoval = entry.show.id },
                            minHeight = 48.dp,
                            modifier = Modifier.weight(1f),
                            contentColor = colors.textAlert,
                        ) {
                            Label("Remove", style = BackbeeType.bodySmall, color = colors.textAlert)
                        }
                    }
                }
            }
        }
    }

    pendingRemoval?.let { showId ->
        CarbonDialog(
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
                CarbonButton(
                    onClick = { viewModel.removeShow(showId); pendingRemoval = null },
                    background = colors.accentAlert,
                    contentColor = colors.textOnColor,
                ) {
                    Label("Remove", style = BackbeeType.bodySmall, color = colors.textAlert)
                }
            },
            dismissButton = {
                CarbonOutlineButton(onClick = { pendingRemoval = null }) {
                    Label("Keep", style = BackbeeType.bodySmall, color = colors.textPrimary)
                }
            },
        )
    }
}
