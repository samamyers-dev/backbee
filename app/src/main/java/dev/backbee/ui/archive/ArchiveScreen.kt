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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import dev.backbee.ui.components.EmptyState
import dev.backbee.ui.components.EpisodeRowItem
import dev.backbee.ui.theme.Dimens
import kotlinx.coroutines.launch

/**
 * The spine of the app: every episode, oldest first, exactly as it will be
 * played.
 *
 * A thousand-row list needs three ways in - scrub by year, jump to a number,
 * search the text - because scrolling to 2014 by hand is not a thing anyone
 * should do twice.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveScreen(
    viewModel: ArchiveViewModel,
    onBack: () -> Unit,
    onOpenEpisode: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var searchOpen by remember { mutableStateOf(false) }
    var jumpText by remember { mutableStateOf("") }
    var openedAtBookmark by remember { mutableStateOf(false) }

    // Open where the bookmark is rather than at episode one; after 300 episodes
    // the top of the list is not where anybody wants to land.
    LaunchedEffect(state.resumeIndex, state.rows.size) {
        val index = state.resumeIndex
        if (!openedAtBookmark && index != null && state.rows.isNotEmpty()) {
            listState.scrollToItem(index)
            openedAtBookmark = true
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(state.show?.title ?: "Archive", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        searchOpen = !searchOpen
                        if (!searchOpen) viewModel.setQuery("")
                    }) {
                        Icon(
                            imageVector = if (searchOpen) Icons.Filled.Close else Icons.Filled.Search,
                            contentDescription = if (searchOpen) "Close search" else "Search",
                        )
                    }
                    IconButton(onClick = {
                        state.resumeIndex?.let { scope.launch { listState.animateScrollToItem(it) } }
                    }) {
                        Icon(Icons.Filled.MyLocation, contentDescription = "Jump to bookmark")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            if (searchOpen) {
                SearchAndJumpBar(
                    query = state.query,
                    onQueryChange = viewModel::setQuery,
                    jumpText = jumpText,
                    onJumpTextChange = { jumpText = it },
                    onJump = {
                        val number = jumpText.trim().toIntOrNull() ?: return@SearchAndJumpBar
                        viewModel.indexForEpisodeNumber(number)?.let { index ->
                            scope.launch { listState.animateScrollToItem(index) }
                        }
                    },
                )
                HorizontalDivider()
            }

            if (state.rows.isEmpty()) {
                EmptyState(
                    title = if (state.searching) "No matches" else "No episodes yet",
                    body = if (state.searching) "Nothing in this archive matches \"${state.query}\"."
                    else "Pull the feed from the shelf to fill the archive.",
                )
            } else {
                Row(Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f),
                    ) {
                        items(state.rows, key = { it.id }) { row ->
                            EpisodeRowItem(
                                row = row,
                                onClick = { onOpenEpisode(row.id) },
                                highlighted = row.id == state.rows.getOrNull(state.resumeIndex ?: -1)?.id,
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                        }
                    }

                    if (state.years.size > 1) {
                        YearScrubber(
                            years = state.years,
                            onSelect = { marker ->
                                scope.launch { listState.scrollToItem(marker.listIndex) }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchAndJumpBar(
    query: String,
    onQueryChange: (String) -> Unit,
    jumpText: String,
    onJumpTextChange: (String) -> Unit,
    onJump: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.gutter, vertical = Dimens.gap),
        horizontalArrangement = Arrangement.spacedBy(Dimens.gap),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text("Search titles") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = jumpText,
            onValueChange = onJumpTextChange,
            label = { Text("Ep #") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Go,
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onGo = { onJump() }),
            modifier = Modifier.width(110.dp),
        )
    }
}

/**
 * The year rail down the right edge. A decade of episodes is a decade of
 * scrolling otherwise.
 */
@Composable
private fun YearScrubber(years: List<YearMarker>, onSelect: (YearMarker) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .width(52.dp)
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        years.forEach { marker ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(marker) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = marker.year.toString().takeLast(2),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
