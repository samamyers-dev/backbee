package dev.backbee.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.backbee.core.playback.ArchiveProgress
import dev.backbee.data.db.DownloadState
import dev.backbee.data.db.EpisodeRow
import dev.backbee.ui.theme.BackbeeType
import dev.backbee.ui.theme.Dimens
import dev.backbee.ui.theme.backbeeColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * One line of the archive.
 *
 * The number is monospaced and left-aligned so a thousand rows form a straight
 * column you can scan; state lives in a fixed glyph slot on the right so it can
 * be read without stopping. Colour is never the only carrier of meaning.
 */
@Composable
fun EpisodeRowItem(
    row: EpisodeRow,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    /** When set, occurrences are highlighted in the title - used by search. */
    highlight: String? = null,
) {
    val colors = backbeeColors
    val dim = row.isPlayed && !isPlaying

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .then(if (isPlaying) Modifier.background(colors.accentPrimary.copy(alpha = 0.14f)) else Modifier)
            .defaultMinSize(minHeight = Dimens.rowMinHeight)
            .padding(horizontal = Dimens.gutter, vertical = Dimens.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Mono(
            text = (row.episodeNumber ?: (row.orderIndex + 1)).toString(),
            color = if (dim) colors.textMuted else colors.textAccent,
            style = BackbeeType.mono,
            modifier = Modifier.width(52.dp),
        )

        Column(Modifier.weight(1f)) {
            Text(
                text = highlightedTitle(row.title, highlight, colors.textAccent),
                style = BackbeeType.titleSmall,
                color = if (dim) colors.textMuted else colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = metaLine(row),
                style = BackbeeType.monoSmall,
                color = colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            if (isPlaying) {
                StatusChip("Playing", modifier = Modifier.padding(top = 6.dp))
            }
        }

        // Fixed-width, end-aligned glyph column so the right edge stays straight
        // no matter how many glyphs a row carries.
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(start = Dimens.space2)
                .width(56.dp),
        ) {
            if (row.isStarred) GlyphText(Glyph.STARRED, colors.textAccent)

            // Exactly one state glyph. "Downloaded" and "untouched" are not two
            // facts to stack up - a downloaded episode is by definition one you
            // have not played yet, and showing both just adds noise to a list
            // meant to be read while scrolling past a thousand rows.
            when {
                row.isPlayed -> GlyphText(Glyph.PLAYED, colors.textMuted)
                isPlaying -> GlyphText(Glyph.PLAYING, colors.textAccent)
                row.downloadState == DownloadState.FAILED ->
                    GlyphText(Glyph.FAILED, colors.textAlert)
                row.progressFraction != null -> GlyphText(Glyph.PLAYING, colors.textSecondary)
                row.isDownloaded -> GlyphText(Glyph.DOWNLOADED, colors.textFunctional)
                row.downloadState == DownloadState.RUNNING ||
                    row.downloadState == DownloadState.QUEUED ->
                    GlyphText(Glyph.DOWNLOADED, colors.textMuted)
                else -> GlyphText(Glyph.UNTOUCHED, colors.textMuted)
            }
        }
    }
}

/** `14 MAR 2021 · 26:41 / 1H 18M` - date, then position within duration. */
private fun metaLine(row: EpisodeRow): String {
    val parts = mutableListOf<String>()
    if (row.isKept) parts += "KEPT"
    row.pubDate?.let { parts += DATE.format(Date(it)).uppercase() }

    val duration = row.durationSeconds
    val position = row.positionSeconds ?: 0
    when {
        duration == null -> Unit
        !row.isPlayed && position > 0 ->
            parts += "${ArchiveProgress.formatClock(position)} / ${ArchiveProgress.formatDuration(duration)}"
        else -> parts += ArchiveProgress.formatDuration(duration)
    }
    return parts.joinToString("  ·  ")
}

private fun highlightedTitle(title: String, needle: String?, accent: androidx.compose.ui.graphics.Color): AnnotatedString {
    val query = needle?.trim().orEmpty()
    if (query.isEmpty()) return AnnotatedString(title)

    return buildAnnotatedString {
        var index = 0
        while (true) {
            val hit = title.indexOf(query, index, ignoreCase = true)
            if (hit < 0) {
                append(title.substring(index))
                break
            }
            append(title.substring(index, hit))
            withStyle(SpanStyle(color = accent, fontWeight = FontWeight.Black)) {
                append(title.substring(hit, hit + query.length))
            }
            index = hit + query.length
        }
    }
}

private val DATE = SimpleDateFormat("d MMM yyyy", Locale.US)
