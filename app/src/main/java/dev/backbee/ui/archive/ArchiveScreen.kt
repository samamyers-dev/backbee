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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
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
import dev.backbee.ui.components.BrutalDivider
import dev.backbee.ui.components.BrutalOutlineButton
import dev.backbee.ui.components.EmptyState
import dev.backbee.ui.components.EpisodeRowItem
import dev.backbee.ui.components.Glyph
import dev.backbee.ui.components.Label
import dev.backbee.ui.components.Mono
import dev.backbee.ui.components.Readout
import dev.backbee.ui.theme.BackbeeType
import dev.backbee.ui.theme.Dimens
import dev.backbee.ui.theme.Stroke
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

    // Open at the bookmark, not at episode one. After 300 episodes the top of
    // the list is not where anybody wants to land.
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
            Mono("OLDEST FIRST", style = BackbeeType.monoMicro, color = colors.textMuted)

            Row(
                Modifier.fillMaxWidth().padding(top = Dimens.space3),
                horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
            ) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    placeholder = { Mono("SEARCH TITLES…", style = BackbeeType.monoSmall, color = colors.textMuted) },
                    singleLine = true,
                    textStyle = BackbeeType.mono,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = jumpText,
                    onValueChange = { jumpText = it },
                    placeholder = { Mono("EP #", style = BackbeeType.monoSmall, color = colors.textMuted) },
                    singleLine = true,
                    textStyle = BackbeeType.mono,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = {
                        jumpText.trim().toIntOrNull()
                            ?.let(viewModel::indexForEpisodeNumber)
                            ?.let { scope.launch { listState.animateScrollToItem(it) } }
                    }),
                    modifier = Modifier.width(104.dp),
                )
            }

            if (state.searching) {
                Readout(
                    lines = listOf(
                        "QUERY: \"${state.query}\"",
                        "SCOPE: ACTIVE SHOW ONLY",
                        "${state.rows.size} HITS // ${state.totalEpisodes} INDEXED",
                    ),
                    modifier = Modifier.padding(top = Dimens.space3),
                )
            }
        }

        BrutalDivider(thickness = Stroke.divider)

        if (state.rows.isEmpty()) {
            EmptyState(
                title = if (state.searching) "No matches" else "No episodes yet",
                body = if (state.searching) "Nothing in this archive matches \"${state.query}\"."
                else "Pull the feed from the shelf to fill the archive.",
            )
            return@Column
        }

        Row(Modifier.weight(1f)) {
            LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                items(state.rows, key = { it.id }) { row ->
                    // Sticky-ish year band: emitted before the first row of each
                    // year so a decade of scrolling always says where it is.
                    state.yearBandFor(row.id)?.let { band ->
                        Mono(
                            text = band,
                            style = BackbeeType.labelSmall,
                            color = colors.textInverse,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.bgInverse)
                                .padding(horizontal = Dimens.gutter, vertical = 6.dp),
                        )
                    }
                    EpisodeRowItem(
                        row = row,
                        onClick = { onOpenEpisode(row.id) },
                        isPlaying = playerState.episodeId == row.id,
                        highlight = state.query.takeIf { state.searching },
                    )
                    BrutalDivider()
                }
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(Dimens.gutter),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.space4),
                    ) {
                        Mono("${Glyph.PLAYED} PLAYED ${state.playedCount}", style = BackbeeType.monoMicro, color = colors.textMuted)
                        Mono("${Glyph.DOWNLOADED} ON DEVICE ${state.downloadedCount}", style = BackbeeType.monoMicro, color = colors.textMuted)
                        Mono("${Glyph.STARRED} STARRED ${state.starredCount}", style = BackbeeType.monoMicro, color = colors.textMuted)
                    }
                }
            }

            if (state.years.size > 1 && !state.searching) {
                Column(
                    modifier = Modifier
                        .width(44.dp)
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
                                .padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Mono(
                                "'" + marker.year.toString().takeLast(2),
                                style = BackbeeType.monoMicro,
                                color = colors.textMuted,
                            )
                        }
                    }
                }
            }
        }

        state.resumeIndex?.let { index ->
            BrutalOutlineButton(
                onClick = { scope.launch { listState.animateScrollToItem(index) } },
                modifier = Modifier.fillMaxWidth().padding(Dimens.space3),
            ) {
                Label("Jump to the bookmark", color = colors.textPrimary)
            }
        }
    }
}
