package dev.backbee.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.backbee.core.playback.ArchiveProgress
import dev.backbee.data.db.DownloadState
import dev.backbee.data.db.EpisodeRow
import dev.backbee.ui.theme.Dimens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * One line of the archive.
 *
 * Everything the spec asks a row to convey - played, in progress, downloaded,
 * starred - has to be legible in a glance while scrolling past a thousand
 * siblings, so state is carried by a small fixed set of icons in fixed places
 * rather than by colour alone.
 */
@Composable
fun EpisodeRowItem(
    row: EpisodeRow,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
) {
    val dim = row.isPlayed && !highlighted

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = Dimens.rowHeight)
            .padding(horizontal = Dimens.gutter, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = (row.episodeNumber ?: (row.orderIndex + 1)).toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.defaultMinSize(minWidth = 44.dp),
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Medium,
                    color = if (dim) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                MetaRow(
                    row.pubDate?.let(::formatDate),
                    row.durationSeconds?.let { ArchiveProgress.formatDuration(it) },
                    progressLabel(row),
                    modifier = Modifier.padding(top = 3.dp),
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = Dimens.gap),
            ) {
                if (row.isStarred) {
                    StatusIcon(Icons.Filled.Star, "Starred", MaterialTheme.colorScheme.primary)
                }
                when {
                    row.isDownloaded -> StatusIcon(Icons.Filled.DownloadDone, "Downloaded")
                    row.downloadState == DownloadState.QUEUED ||
                        row.downloadState == DownloadState.RUNNING ->
                        StatusIcon(Icons.Filled.CloudDownload, "Downloading")
                }
                if (row.isPlayed) {
                    StatusIcon(Icons.Filled.CheckCircle, "Played")
                } else {
                    // Keeps the icon column from reflowing as rows change state.
                    Box(Modifier.size(24.dp))
                }
            }
        }

        row.progressFraction?.takeIf { !row.isPlayed }?.let { fraction ->
            ProgressStripe(fraction, modifier = Modifier.padding(top = 10.dp, start = 44.dp))
        }
    }
}

@Composable
private fun StatusIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Icon(
        imageVector = icon,
        contentDescription = description,
        tint = tint,
        modifier = Modifier.size(24.dp),
    )
}

private fun progressLabel(row: EpisodeRow): String? {
    if (row.isPlayed) return null
    val fraction = row.progressFraction ?: return null
    return "${(fraction * 100).toInt()}%"
}

private val dateFormat = SimpleDateFormat("d MMM yyyy", Locale.US)

private fun formatDate(millis: Long): String = dateFormat.format(Date(millis))
