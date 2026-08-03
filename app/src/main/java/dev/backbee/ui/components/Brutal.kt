package dev.backbee.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.backbee.ui.theme.BackbeeType
import dev.backbee.ui.theme.Dimens
import dev.backbee.ui.theme.Shadow
import dev.backbee.ui.theme.Stroke
import dev.backbee.ui.theme.backbeeColors
import androidx.compose.material3.Text as M3Text

/**
 * The FREE THEM. surface: a hard offset shadow with no blur, a solid fill, and a
 * visible border, all with square corners.
 *
 * The shadow is drawn behind the node and extends past its bounds, so callers
 * need to leave [shadow] worth of room on the right and bottom - [BrutalPanel]
 * and friends do that for you.
 */
fun Modifier.brutal(
    background: Color,
    border: Color,
    shadowColor: Color,
    shadow: Dp = Shadow.md,
    borderWidth: Dp = Stroke.divider,
): Modifier = this
    .drawBehind {
        if (shadow > 0.dp) {
            val o = shadow.toPx()
            drawRect(color = shadowColor, topLeft = Offset(o, o), size = size)
        }
    }
    .background(background)
    .border(BorderStroke(borderWidth, border))

/** A bordered, shadowed panel. The workhorse container of every screen. */
@Composable
fun BrutalPanel(
    modifier: Modifier = Modifier,
    shadow: Dp = Shadow.md,
    borderWidth: Dp = Stroke.divider,
    background: Color = backbeeColors.bgPanel,
    border: Color = backbeeColors.borderColor,
    contentPadding: Dp = Dimens.space4,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            // Reserve the shadow's overhang so it cannot be clipped or collide
            // with whatever sits next to it.
            .padding(end = shadow, bottom = shadow)
            .brutal(background, border, backbeeColors.shadowColor, shadow, borderWidth)
            .padding(contentPadding),
        content = content,
    )
}

/**
 * A button that presses *into* its shadow: on touch the surface slides down and
 * right by the shadow offset and the shadow disappears, the way a printed key
 * would sit down onto the page.
 */
@Composable
fun BrutalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    background: Color = backbeeColors.accentPrimary,
    contentColor: Color = backbeeColors.onAccentPrimary,
    shadow: Dp = Shadow.md,
    borderWidth: Dp = Stroke.divider,
    minHeight: Dp = Dimens.touchTarget,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val depth = if (pressed || !enabled) 0.dp else shadow

    Box(modifier.padding(end = shadow, bottom = shadow)) {
        Row(
            modifier = Modifier
                .offset(x = shadow - depth, y = shadow - depth)
                .fillMaxWidth()
                .defaultMinSize(minHeight = minHeight)
                .brutal(
                    background = if (enabled) background else background.copy(alpha = 0.4f),
                    border = backbeeColors.borderColor,
                    shadowColor = backbeeColors.shadowColor,
                    shadow = depth,
                    borderWidth = borderWidth,
                )
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled,
                    onClick = onClick,
                )
                .padding(horizontal = Dimens.space4, vertical = Dimens.space3),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.material3.LocalContentColor provides contentColor,
            ) {
                content()
            }
        }
    }
}

/** Same press behaviour, no fill - for secondary actions. */
@Composable
fun BrutalOutlineButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shadow: Dp = Shadow.sm,
    minHeight: Dp = 48.dp,
    content: @Composable RowScope.() -> Unit,
) = BrutalButton(
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    background = backbeeColors.bgPanel,
    contentColor = backbeeColors.textPrimary,
    shadow = shadow,
    minHeight = minHeight,
    content = content,
)

/** Small caps label with the very wide tracking the system depends on. */
@Composable
fun Label(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = backbeeColors.textMuted,
    style: TextStyle = BackbeeType.label,
) {
    M3Text(text = text.uppercase(), style = style, color = color, modifier = modifier)
}

/** Monospaced text. Every number, timestamp and status string in the app. */
@Composable
fun Mono(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = backbeeColors.textPrimary,
    style: TextStyle = BackbeeType.mono,
    maxLines: Int = Int.MAX_VALUE,
    textAlign: TextAlign? = null,
) {
    M3Text(
        text = text,
        style = style,
        color = color,
        maxLines = maxLines,
        textAlign = textAlign,
        modifier = modifier,
    )
}

/**
 * The terminal readout blocks: `> POS RESTORED 00:26:41`.
 *
 * They exist to make the machinery legible - what the app just did to your
 * position, whether the archive is complete, whether the checkpoint reached the
 * server. Inverted panel, monospace, one fact per line.
 */
@Composable
fun Readout(
    lines: List<String>,
    modifier: Modifier = Modifier,
    tone: Color = backbeeColors.accentFunctional,
) {
    if (lines.isEmpty()) return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(backbeeColors.bgInverse)
            .border(Stroke.thin, backbeeColors.borderColor)
            .padding(Dimens.space3),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        lines.forEach { line ->
            M3Text(
                text = "> $line",
                style = BackbeeType.monoSmall,
                color = tone,
            )
        }
    }
}

/**
 * The per-row state glyphs from the archive spec. Deliberately characters
 * rather than icons: they sit on the mono baseline with the episode number and
 * stay legible at a glance while scrolling past a thousand rows.
 */
object Glyph {
    const val PLAYED = "✓"
    const val PLAYING = "●"
    const val DOWNLOADED = "▼"
    const val STARRED = "★"
    const val UNTOUCHED = "○"
    const val FAILED = "✕"
}

@Composable
fun GlyphText(glyph: String, color: Color, modifier: Modifier = Modifier) {
    M3Text(text = glyph, style = BackbeeType.mono, color = color, modifier = modifier)
}

/** A filled chip for inline status: PLAYING, ON DEVICE, FAILED x3. */
@Composable
fun StatusChip(
    text: String,
    modifier: Modifier = Modifier,
    background: Color = backbeeColors.accentPrimary,
    contentColor: Color = backbeeColors.onAccentPrimary,
) {
    M3Text(
        text = text.uppercase(),
        style = BackbeeType.labelSmall,
        color = contentColor,
        modifier = modifier
            .background(background)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** A square-cornered progress bar, drawn as a filled block inside a bordered track. */
@Composable
fun BrutalProgress(
    fraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
    color: Color = backbeeColors.accentPrimary,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = height)
            .background(backbeeColors.bgPanel)
            .border(Stroke.thin, backbeeColors.borderColor),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .defaultMinSize(minHeight = height)
                .background(color),
        )
    }
}

/** A horizontal rule at divider weight. */
@Composable
fun BrutalDivider(modifier: Modifier = Modifier, thickness: Dp = Stroke.thin) {
    Box(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = thickness)
            .background(backbeeColors.borderColor.copy(alpha = 0.35f)),
    )
}

/**
 * The dither field behind panels: a faint square grid at the spec's 24dp cell.
 * It is what stops the large flat areas reading as empty.
 */
@Composable
fun DitherBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val colors = backbeeColors
    Box(
        modifier = modifier
            .background(colors.bgPage)
            .drawBehind {
                val cell = Dimens.ditherCell.toPx()
                if (cell <= 0f) return@drawBehind
                var x = 0f
                while (x < size.width) {
                    drawRect(colors.ditherLine, Offset(x, 0f), androidx.compose.ui.geometry.Size(1f, size.height))
                    x += cell
                }
                var y = 0f
                while (y < size.height) {
                    drawRect(colors.ditherLine, Offset(0f, y), androidx.compose.ui.geometry.Size(size.width, 1f))
                    y += cell
                }
            },
        content = content,
    )
}
