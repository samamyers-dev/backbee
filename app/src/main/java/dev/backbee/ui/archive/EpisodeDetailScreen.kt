package dev.backbee.ui.archive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.backbee.core.playback.ArchiveProgress
import dev.backbee.playback.PlayerConnection
import dev.backbee.ui.components.MetaRow
import dev.backbee.ui.components.SectionLabel
import dev.backbee.ui.theme.Dimens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodeDetailScreen(
    viewModel: EpisodeDetailViewModel,
    player: PlayerConnection,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val row = state.row

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Episode", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::toggleStar) {
                        Icon(
                            imageVector = if (row?.isStarred == true) Icons.Filled.Star else Icons.Filled.StarBorder,
                            contentDescription = if (row?.isStarred == true) "Unstar" else "Star",
                            tint = if (row?.isStarred == true) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (row == null) {
            Column(Modifier.padding(padding).fillMaxSize()) {}
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Dimens.gutter),
        ) {
            Text(
                text = "Episode ${row.episodeNumber ?: (row.orderIndex + 1)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = row.title,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
            val progressLabel = row.progressFraction?.let { "${(it * 100).toInt()}% in" }
            MetaRow(
                row.pubDate?.let { DATE_FORMAT.format(Date(it)) },
                row.durationSeconds?.let { ArchiveProgress.formatDuration(it) },
                if (row.isPlayed) "Played" else progressLabel,
                if (row.isDownloaded) "Downloaded" else null,
                modifier = Modifier.padding(top = Dimens.gap),
            )

            Button(
                onClick = { player.playEpisode(row.id) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Dimens.gutter)
                    .height(Dimens.touchTarget),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Text(
                    text = if ((row.positionSeconds ?: 0) > 0) "Resume this episode" else "Play this episode",
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(Dimens.gap),
                modifier = Modifier.padding(top = Dimens.gap),
            ) {
                if (row.isDownloaded) {
                    OutlinedButton(onClick = viewModel::removeDownload, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Delete, contentDescription = null)
                        Text("Remove file", modifier = Modifier.padding(start = 6.dp))
                    }
                } else {
                    OutlinedButton(onClick = viewModel::downloadNow, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Download, contentDescription = null)
                        Text("Download", modifier = Modifier.padding(start = 6.dp))
                    }
                }
                FilledTonalButton(
                    onClick = { viewModel.setPlayed(!row.isPlayed) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (row.isPlayed) "Mark unplayed" else "Mark played")
                }
            }

            HorizontalDivider(Modifier.padding(vertical = Dimens.gutter))

            SectionLabel("Note")
            OutlinedTextField(
                value = state.noteDraft,
                onValueChange = viewModel::setNoteDraft,
                placeholder = { Text("Something worth remembering") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                minLines = 3,
            )
            FilledTonalButton(
                onClick = viewModel::saveNote,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text("Save note")
            }

            state.description?.let { description ->
                HorizontalDivider(Modifier.padding(vertical = Dimens.gutter))
                SectionLabel("Description")
                Text(
                    // Feed descriptions are full of markup; strip it rather than
                    // rendering a wall of tags.
                    text = stripHtml(description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

private val DATE_FORMAT = SimpleDateFormat("d MMMM yyyy", Locale.US)

private fun stripHtml(raw: String): String = raw
    .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
    .replace(Regex("</p>", RegexOption.IGNORE_CASE), "\n\n")
    .replace(Regex("<[^>]*>"), "")
    .replace("&nbsp;", " ")
    .replace("&amp;", "&")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
    .trim()
