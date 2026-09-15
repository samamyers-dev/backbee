package dev.backbee.ui.completion

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.backbee.core.playback.ArchiveProgress
import dev.backbee.ui.components.Artwork
import dev.backbee.ui.components.CarbonButton
import dev.backbee.ui.components.CarbonPanel
import dev.backbee.ui.components.Glyph
import dev.backbee.ui.components.Label
import dev.backbee.ui.theme.BackbeeType
import dev.backbee.ui.theme.Dimens
import dev.backbee.ui.theme.backbeeColors
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Finishing a 1,200-episode archive is a year of someone's listening. This is
 * the app saying so, before pointing at the next book on the shelf.
 */
@Composable
fun CompletionScreen(
    viewModel: CompletionViewModel,
    onActivatedNextShow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = backbeeColors

    LazyColumn(
        modifier = modifier.fillMaxSize().background(colors.bgPage),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(Dimens.gutter),
        verticalArrangement = Arrangement.spacedBy(Dimens.space3),
    ) {
        item {
            Column {
                Label("Archive complete", color = colors.textAccent)
                Spacer(Modifier.height(Dimens.space3))
                Text("You finished", style = BackbeeType.displayMedium, color = colors.textPrimary)
                Text("the book.", style = BackbeeType.displayMedium, color = colors.textAccent)
                Spacer(Modifier.height(Dimens.space4))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Artwork(state.show?.artworkUrl, state.show?.title, size = 72.dp)
                    Text(
                        text = "${state.show?.title.orEmpty()} · " +
                            "${state.stats?.episodeCount ?: 0} episodes",
                        style = BackbeeType.bodySmall,
                        color = colors.textMuted,
                        modifier = Modifier.padding(start = Dimens.space3),
                    )
                }
            }
        }

        state.stats?.let { stats ->
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.space3)) {
                    StatBlock(stats.listenedHours.roundToInt().toString(), "Hours listened", Modifier.weight(1f))
                    StatBlock(
                        String.format(Locale.US, "%.1f", (stats.elapsedDays ?: 0L) / 365.0),
                        "Years of archive",
                        Modifier.weight(1f),
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.space3)) {
                    StatBlock("${ArchiveProgress.formatSpeed(stats.averageSpeed)}×", "Average speed", Modifier.weight(1f))
                    StatBlock(
                        String.format(Locale.US, "%.1f", stats.episodesPerWeek ?: 0.0),
                        "Episodes per week",
                        Modifier.weight(1f),
                    )
                }
            }
            stats.dateRange()?.let { range ->
                item {
                    Text(range, style = BackbeeType.bodySmall, color = colors.textMuted)
                }
            }
        }

        if (state.starred.isNotEmpty()) {
            item { Label("Starred — ${state.starred.size} episodes") }
            items(state.starred, key = { it.id }) { row ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(Glyph.STARRED, style = BackbeeType.bodySmall, color = colors.textAccent)
                    Text(
                        " ${row.episodeNumber ?: (row.orderIndex + 1)} · ${row.title}",
                        style = BackbeeType.bodySmall,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        item { Label("Pick the next show") }

        if (state.otherShows.isEmpty()) {
            item {
                Text(
                    "Nothing else waiting. add a show from the shelf when you are ready.",
                    style = BackbeeType.bodySmall,
                    color = colors.textMuted,
                )
            }
        } else {
            items(state.otherShows, key = { it.id }) { show ->
                CarbonPanel(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Artwork(show.artworkUrl, show.title)
                        Text(
                            show.title,
                            style = BackbeeType.titleSmall,
                            color = colors.textPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(horizontal = Dimens.space3),
                        )
                    }
                    CarbonButton(
                        onClick = { viewModel.activateShow(show.id, onActivatedNextShow) },

                        minHeight = 48.dp,
                        modifier = Modifier.padding(top = Dimens.space2),
                    ) {
                        Label("Start this", style = BackbeeType.bodySmall, color = colors.onAccentPrimary)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatBlock(value: String, label: String, modifier: Modifier = Modifier) {
    CarbonPanel(modifier) {
        Text(value, style = BackbeeType.displaySmall, color = backbeeColors.textAccent)
        Text(label, style = BackbeeType.labelSmall, color = backbeeColors.textMuted)
    }
}
