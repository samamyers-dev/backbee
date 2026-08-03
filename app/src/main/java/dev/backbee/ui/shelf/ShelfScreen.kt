package dev.backbee.ui.shelf

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import dev.backbee.ui.components.SectionLabel
import dev.backbee.ui.theme.Dimens

/**
 * The shelf: the active show on top, everything else waiting with its bookmark
 * frozen where it was left.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShelfScreen(
    viewModel: ShelfViewModel,
    onBack: () -> Unit,
    onOpenCompletion: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val addState by viewModel.addState.collectAsStateWithLifecycle()
    var pendingRemoval by remember { mutableStateOf<Long?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Shelf") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(Dimens.gutter),
            verticalArrangement = Arrangement.spacedBy(Dimens.gap),
        ) {
            item {
                AddShowCard(
                    query = addState.query,
                    searching = addState.searching,
                    adding = addState.adding,
                    onQueryChange = viewModel::setQuery,
                    onSearch = viewModel::search,
                    onAddUrl = { viewModel.addByUrl(addState.query) },
                )
            }

            addState.message?.let { message ->
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(message, style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = viewModel::dismissMessage) { Text("Dismiss") }
                        }
                    }
                }
            }

            if (addState.results.isNotEmpty()) {
                item { SectionLabel("Search results") }
                items(addState.results, key = { it.feedUrl }) { result ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Artwork(result.artworkUrl)
                            Column(
                                Modifier
                                    .weight(1f)
                                    .padding(horizontal = Dimens.gap),
                            ) {
                                Text(
                                    result.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = listOfNotNull(
                                        result.author,
                                        result.episodeCount?.let { "$it episodes" },
                                    ).joinToString(" · "),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            FilledTonalButton(onClick = { viewModel.addByUrl(result.feedUrl) }) {
                                Text("Add")
                            }
                        }
                    }
                }
            }

            if (entries.isNotEmpty()) {
                item { SectionLabel("Shows") }
                items(entries, key = { it.show.id }) { entry ->
                    ShelfCard(
                        entry = entry,
                        onMakeActive = { viewModel.makeActive(entry.show.id) },
                        onOpenCompletion = { onOpenCompletion(entry.show.id) },
                        onRemove = { pendingRemoval = entry.show.id },
                    )
                }
            }
        }
    }

    pendingRemoval?.let { showId ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text("Remove this show?") },
            text = {
                Text("Its episodes, downloads, position and notes are all deleted. This cannot be undone.")
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeShow(showId)
                    pendingRemoval = null
                }) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoval = null }) { Text("Keep") }
            },
        )
    }
}

@Composable
private fun AddShowCard(
    query: String,
    searching: Boolean,
    adding: Boolean,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onAddUrl: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            SectionLabel("Add a show")
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                label = { Text("RSS URL or search term") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Dimens.gap),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(Dimens.gap),
                modifier = Modifier.padding(top = Dimens.gap),
            ) {
                // Adding by URL is the direct path; search is the convenience.
                Button(
                    onClick = onAddUrl,
                    enabled = !adding && query.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) {
                    if (adding) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                    Text("Add by URL")
                }
                OutlinedButton(
                    onClick = onSearch,
                    enabled = !searching && query.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (searching) "Searching…" else "Search")
                }
            }
        }
    }
}

@Composable
private fun ShelfCard(
    entry: ShelfEntry,
    onMakeActive: () -> Unit,
    onOpenCompletion: () -> Unit,
    onRemove: () -> Unit,
) {
    val active = entry.show.isActive
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (active) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
        border = if (active) {
            androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Artwork(entry.show.artworkUrl)
                Column(
                    Modifier
                        .weight(1f)
                        .padding(start = Dimens.gap),
                ) {
                    if (active) {
                        Text(
                            text = "ACTIVE",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        text = entry.show.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = entry.bookmarkLine(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(Dimens.gap),
                modifier = Modifier.padding(top = Dimens.gap),
            ) {
                if (!active) {
                    // The only promotion action there is.
                    FilledTonalButton(onClick = onMakeActive, modifier = Modifier.weight(1f)) {
                        Text("Make active")
                    }
                }
                if (entry.isComplete) {
                    OutlinedButton(onClick = onOpenCompletion, modifier = Modifier.weight(1f)) {
                        Text("Recap")
                    }
                }
                OutlinedButton(onClick = onRemove) { Text("Remove") }
            }
        }
    }
}
