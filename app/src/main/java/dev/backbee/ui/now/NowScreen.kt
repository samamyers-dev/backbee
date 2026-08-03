package dev.backbee.ui.now

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.backbee.core.playback.ArchiveProgress
import dev.backbee.data.db.EpisodeRow
import dev.backbee.playback.PlayerConnection
import dev.backbee.playback.PlayerState
import dev.backbee.ui.components.Artwork
import dev.backbee.ui.components.EmptyState
import dev.backbee.ui.components.SectionLabel
import dev.backbee.ui.theme.Dimens

/**
 * The home screen, and the only one that matters while moving.
 *
 * One giant button, one line of context, and a glance at what is coming. Every
 * other decision the app could ask for has already been made somewhere else.
 */
@Composable
fun NowScreen(
    viewModel: NowViewModel,
    player: PlayerConnection,
    onOpenArchive: () -> Unit,
    onOpenShelf: () -> Unit,
    onOpenCompletion: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val playerState by player.state.collectAsStateWithLifecycle()

    when {
        state.loading -> Box(modifier.fillMaxSize())

        !state.hasShow -> EmptyState(
            title = "Nothing on the shelf",
            body = "Add a show by RSS URL or search, and its archive becomes the queue.",
            modifier = modifier,
            action = {
                FilledTonalButton(onClick = onOpenShelf) { Text("Open the shelf") }
            },
        )

        state.archiveComplete -> EmptyState(
            title = "${state.show?.title} is finished",
            body = "Every episode played. Take a look at how it went, then pick the next one off the shelf.",
            modifier = modifier,
            action = {
                FilledTonalButton(onClick = { state.show?.let { onOpenCompletion(it.id) } }) {
                    Text("See the recap")
                }
            },
        )

        else -> NowContent(
            state = state,
            playerState = playerState,
            player = player,
            onOpenArchive = onOpenArchive,
            onSetSpeed = viewModel::setSpeed,
            modifier = modifier,
        )
    }
}

@Composable
private fun NowContent(
    state: NowUiState,
    playerState: PlayerState,
    player: PlayerConnection,
    onOpenArchive: () -> Unit,
    onSetSpeed: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val target = state.resumeTarget
    // While something is playing the transport reflects the player; otherwise it
    // reflects the bookmark, so the screen reads the same either way.
    val playingThisEpisode = playerState.episodeId != null && playerState.episodeId == target?.id

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.gutter),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Artwork(state.show?.artworkUrl, size = Dimens.artworkLarge)

        Text(
            text = state.show?.title.orEmpty(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Dimens.gutter),
        )

        Text(
            text = target?.let { "${it.episodeNumber ?: (it.orderIndex + 1)}. ${it.title}" }.orEmpty(),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )

        state.progress?.let { progress ->
            Text(
                text = progress.summary(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Dimens.gap),
            )
            LinearProgressIndicator(
                progress = { progress.percentByEpisodes / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
            )
        }

        Spacer(Modifier.height(28.dp))

        Transport(
            isPlaying = playerState.isPlaying,
            onSkipBack = player::skipBack,
            onSkipForward = player::skipForward,
            onToggle = {
                when {
                    playerState.isPlaying -> player.pause()
                    playingThisEpisode -> player.resume()
                    target != null -> player.playEpisode(target.id)
                    else -> player.resume()
                }
            },
        )

        if (playingThisEpisode && playerState.durationMs > 0) {
            Text(
                text = "${ArchiveProgress.formatClock(playerState.positionSeconds)} · " +
                    "-${ArchiveProgress.formatClock(playerState.remainingMs / 1000)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Dimens.gap),
            )
        }

        SpeedRow(
            speed = state.show?.speed ?: 1f,
            onSetSpeed = onSetSpeed,
            modifier = Modifier.padding(top = Dimens.gutter),
        )

        Spacer(Modifier.height(32.dp))

        if (state.upNext.isNotEmpty()) {
            SectionLabel(
                text = "Next up",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Dimens.gap),
            )
            state.upNext.forEach { row ->
                UpNextRow(row = row, onClick = { player.playEpisode(row.id) })
                Spacer(Modifier.height(8.dp))
            }
        }

        FilledTonalButton(
            onClick = onOpenArchive,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Dimens.gap)
                .height(Dimens.touchTarget),
        ) {
            Text("Open the archive")
        }
    }
}

@Composable
private fun Transport(
    isPlaying: Boolean,
    onSkipBack: () -> Unit,
    onSkipForward: () -> Unit,
    onToggle: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.gutter),
    ) {
        IconButton(onClick = onSkipBack, modifier = Modifier.size(Dimens.touchTarget)) {
            Icon(
                Icons.Filled.Replay10,
                contentDescription = "Skip back",
                modifier = Modifier.size(40.dp),
            )
        }

        // The one control that has to be hittable without looking.
        Box(
            modifier = Modifier
                .size(Dimens.giantButton)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(76.dp),
            )
        }

        IconButton(onClick = onSkipForward, modifier = Modifier.size(Dimens.touchTarget)) {
            Icon(
                Icons.Filled.Forward30,
                contentDescription = "Skip forward",
                modifier = Modifier.size(40.dp),
            )
        }
    }
}

private val SPEEDS = listOf(1.0f, 1.2f, 1.4f, 1.6f, 1.8f, 2.0f)

@Composable
private fun SpeedRow(speed: Float, onSetSpeed: (Float) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SPEEDS.forEach { option ->
            val selected = kotlin.math.abs(option - speed) < 0.01f
            Surface(
                shape = RoundedCornerShape(50),
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                contentColor = if (selected) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable { onSetSpeed(option) },
            ) {
                Text(
                    text = "${ArchiveProgress.formatSpeed(option)}×",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun UpNextRow(row: EpisodeRow, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = (row.episodeNumber ?: (row.orderIndex + 1)).toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = Dimens.gap),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = row.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    // Download state is the whole reason this strip exists: it
                    // answers "will the next hour work without signal?".
                    text = if (row.isDownloaded) "Downloaded" else "Not downloaded yet",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            row.durationSeconds?.let {
                Text(
                    text = ArchiveProgress.formatDuration(it),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
