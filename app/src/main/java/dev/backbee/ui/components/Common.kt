package dev.backbee.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.backbee.ui.theme.BackbeeType
import dev.backbee.ui.theme.Dimens
import dev.backbee.ui.theme.Stroke
import dev.backbee.ui.theme.backbeeColors

/**
 * Show artwork. Square-cornered and bordered like everything else, with the
 * show's initials as the placeholder rather than a generic glyph - a shelf of
 * three-letter blocks reads faster than a shelf of identical icons.
 */
@Composable
fun Artwork(
    url: String?,
    title: String? = null,
    size: Dp = Dimens.artworkSmall,
    modifier: Modifier = Modifier,
) {
    val colors = backbeeColors
    Box(
        modifier = modifier
            .size(size)
            .background(colors.bgInverse)
            .border(Stroke.divider, colors.borderColor),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNullOrBlank()) {
            Text(
                text = initialsOf(title),
                style = if (size > 120.dp) BackbeeType.displaySmall else BackbeeType.label,
                color = colors.textInverse,
            )
        } else {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** "Hey Riddle Riddle" -> "HRR". */
internal fun initialsOf(title: String?): String {
    val words = title?.split(' ', '-', ':')?.filter { it.isNotBlank() }.orEmpty()
    if (words.isEmpty()) return "??"
    return words.take(3).joinToString("") { it.first().uppercase() }
}

@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    readout: List<String> = emptyList(),
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Dimens.gutter),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            style = BackbeeType.displaySmall,
            color = backbeeColors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = body,
            style = BackbeeType.body,
            color = backbeeColors.textMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Dimens.space3),
        )
        if (action != null) {
            Box(Modifier.padding(top = Dimens.space6).fillMaxWidth()) { action() }
        }
        if (readout.isNotEmpty()) {
            Readout(readout, modifier = Modifier.padding(top = Dimens.space6))
        }
    }
}
