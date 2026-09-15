package dev.backbee.ui.archive

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import dev.backbee.ui.components.CarbonTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.backbee.playback.PlayerConnection
import dev.backbee.ui.components.CarbonDivider
import dev.backbee.ui.components.CarbonOutlineButton
import dev.backbee.ui.components.EmptyState
import dev.backbee.ui.components.EpisodeRowItem
import dev.backbee.ui.components.Glyph
import dev.backbee.ui.components.Label
import dev.backbee.ui.components.Readout
import dev.backbee.ui.theme.BackbeeType
import dev.backbee.ui.theme.Dimens
import dev.backbee.ui.theme.backbeeColors
import kotlinx.coroutines.launch

/**
 * The spine of the app: every episode, oldest first, exactly as it will play.
 *
 * A 1,500-row list needs three ways in - scrub by year, jump to a number,
 * search the text - because scrolling to 2014 by hand is not a thing anyone
 * should do twice.
 */
@Composable
fun ArchiveScreen(
    viewModel: ArchiveViewModel,
    player: PlayerConnection,
    onOpenEpisode: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val playerState by player.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var jumpText by remember { mutableStateOf("") }
    var landed by remember { mutableStateOf(false) }

    // Open where you left off, not at episode one. After 300 episodes the top
    // of the list is not where anybody wants to land.
    LaunchedEffect(state.resumeIndex, state.rows.size) {
        val index = state.resumeIndex
        if (!landed && index != null && state.rows.isNotEmpty()) {
            listState.scrollToItem(index)
            landed = true
        }
    }

    val colors = backbeeColors

    Column(modifier.fillMaxSize().background(colors.bgPage)) {
        Column(Modifier.padding(horizontal = Dimens.gutter, vertical = Dimens.space3)) {
            Label(state.show?.title ?: "Archive", color = colors.textPrimary)
            Text("Oldest first", style = BackbeeType.labelSmall, color = colors.textMuted)

            Row(
                Modifier.fillMaxWidth().padding(top = Dimens.space3),
                horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
            ) {
                CarbonTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    label = { Label("Search titles", style = BackbeeType.labelSmall) },
                    accessibleLabel = "Search titles",
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                CarbonTextField(
                    value = jumpText,
                    // Jumps as you type, like the search field beside it. Hiding
                    // the jump behind the keyboard's Go key made the box look
                    // broken: you typed a number and the list sat there.
                    onValueChange = { entered ->
                        jumpText = entered.filter { it.isDigit() }.take(6)
                        jumpText.toIntOrNull()
                            ?.let(viewModel::indexForEpisodeNumber)
                            ?.let { scope.launch { listState.scrollToItem(it) } }
                    },
                    label = { Label("Episode", style = BackbeeType.labelSmall) },
                    accessibleLabel = "Episode",
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    // Native Done dismisses the keyboard; the jump already happened.
                    modifier = Modifier.width(104.dp),
                )
            }

            if (state.searching) {
                Readout(
                    lines = listOf(
                        "Query: \"${state.query}\"",
                        "Scope: active show only",
                        "${state.rows.size} hits // ${state.totalEpisodes} indexed",
                    ),
                    modifier = Modifier.padding(top = Dimens.space3),
                )
            }
        }

        CarbonDivider()

        if (state.rows.isEmpty()) {
            EmptyState(
                title = if (state.searching) "No matches" else "No episodes yet",
                body = if (state.searching) "Nothing in this archive matches \"${state.query}\"."
                else "The feed has not been read yet. Settings → Check feeds now fills the archive.",
            )
            return@Column
        }

        Row(Modifier.weight(1f)) {
            LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                items(state.rows, key = { it.id }) { row ->
                    // Sticky-ish year band: emitted before the first row of each
                    // year so a decade of scrolling always says where it is.
                    state.yearBandFor(row.id)?.let { band ->
                        Text(
                            text = band,
                            style = BackbeeType.labelSmall,
                            color = colors.textSecondary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.layer02)
                                .padding(horizontal = Dimens.gutter, vertical = 6.dp),
                        )
                    }
                    EpisodeRowItem(
                        row = row,
                        onClick = { onOpenEpisode(row.id) },
                        isPlaying = playerState.episodeId == row.id,
                        highlight = state.query.takeIf { state.searching },
                    )
                    CarbonDivider()
                }
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(Dimens.gutter),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.space4),
                    ) {
                        Text("${Glyph.PLAYED} played ${state.playedCount}", style = BackbeeType.labelSmall, color = colors.textMuted)
                        Text("${Glyph.DOWNLOADED} on device ${state.downloadedCount}", style = BackbeeType.labelSmall, color = colors.textMuted)
                        Text("${Glyph.STARRED} starred ${state.starredCount}", style = BackbeeType.labelSmall, color = colors.textMuted)
                    }
                }
            }

            if (state.years.size > 1 && !state.searching) {
                Column(
                    modifier = Modifier
                        .width(48.dp)
                        .fillMaxSize()
                        .background(colors.bgPanel)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    state.years.forEach { marker ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clickable { scope.launch { listState.scrollToItem(marker.listIndex) } }
                                .heightIn(min = 48.dp)
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "'" + marker.year.toString().takeLast(2),
                                style = BackbeeType.labelSmall,
                                color = colors.textMuted,
                            )
                        }
                    }
                }
            }
        }

        state.resumeIndex?.let { index ->
            CarbonOutlineButton(
                onClick = { scope.launch { listState.animateScrollToItem(index) } },
                modifier = Modifier.fillMaxWidth().padding(Dimens.space3),
            ) {
                Label("Jump to where you left off", color = colors.textPrimary)
            }
        }
    }
}
