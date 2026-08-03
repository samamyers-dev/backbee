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
import dev.backbee.ui.components.BrutalButton
import dev.backbee.ui.components.BrutalPanel
import dev.backbee.ui.components.Glyph
import dev.backbee.ui.components.Label
import dev.backbee.ui.components.Mono
import dev.backbee.ui.theme.BackbeeType
import dev.backbee.ui.theme.Dimens
import dev.backbee.ui.theme.Shadow
import dev.backbee.ui.theme.Stroke
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
                Label("[Archive complete]", color = colors.accentPrimary)
                Spacer(Modifier.height(Dimens.space3))
                Text("You finished", style = BackbeeType.displayMedium, color = colors.textPrimary)
                Text("the book.", style = BackbeeType.displayMedium, color = colors.accentPrimary)
                Spacer(Modifier.height(Dimens.space4))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Artwork(state.show?.artworkUrl, state.show?.title, size = 72.dp)
                    Mono(
                        text = "${state.show?.title.orEmpty().uppercase()} · " +
                            "${state.stats?.episodeCount ?: 0} EPISODES",
                        style = BackbeeType.monoSmall,
                        color = colors.textMuted,
                        modifier = Modifier.padding(start = Dimens.space3),
                    )
                }
            }
        }

        state.stats?.let { stats ->
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.space3)) {
                    StatBlock(stats.listenedHours.roundToInt().toString(), "HOURS LISTENED", Modifier.weight(1f))
                    StatBlock(
                        String.format(Locale.US, "%.1f", (stats.elapsedDays ?: 0L) / 365.0),
                        "YEARS OF ARCHIVE",
                        Modifier.weight(1f),
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.space3)) {
                    StatBlock("${ArchiveProgress.formatSpeed(stats.averageSpeed)}×", "AVERAGE SPEED", Modifier.weight(1f))
                    StatBlock(
                        String.format(Locale.US, "%.1f", stats.episodesPerWeek ?: 0.0),
                        "EPS PER WEEK",
                        Modifier.weight(1f),
                    )
                }
            }
            stats.dateRange()?.let { range ->
                item {
                    Mono(range.uppercase(), style = BackbeeType.monoSmall, color = colors.textMuted)
                }
            }
        }

        if (state.starred.isNotEmpty()) {
            item { Label("Starred — ${state.starred.size} episodes") }
            items(state.starred, key = { it.id }) { row ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Mono(Glyph.STARRED, style = BackbeeType.monoSmall, color = colors.accentPrimary)
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
                Mono(
                    "NOTHING ELSE WAITING. ADD A SHOW FROM THE SHELF WHEN YOU ARE READY.",
                    style = BackbeeType.monoSmall,
                    color = colors.textMuted,
                )
            }
        } else {
            items(state.otherShows, key = { it.id }) { show ->
                BrutalPanel(Modifier.fillMaxWidth(), shadow = Shadow.sm, borderWidth = Stroke.thin) {
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
                    BrutalButton(
                        onClick = { viewModel.activateShow(show.id, onActivatedNextShow) },
                        shadow = Shadow.sm,
                        minHeight = 44.dp,
                        modifier = Modifier.padding(top = Dimens.space2),
                    ) {
                        Mono("START THIS", style = BackbeeType.monoSmall, color = colors.onAccentPrimary)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatBlock(value: String, label: String, modifier: Modifier = Modifier) {
    BrutalPanel(modifier, shadow = Shadow.sm) {
        Text(value, style = BackbeeType.displaySmall, color = backbeeColors.accentPrimary)
        Mono(label, style = BackbeeType.monoMicro, color = backbeeColors.textMuted)
    }
}
