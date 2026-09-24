package dev.backbee.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.backbee.ui.theme.BackbeeType
import dev.backbee.ui.theme.BackbeeColors
import dev.backbee.ui.theme.Dimens
import dev.backbee.ui.theme.backbeeColors

/** Content colors can be supplied by a control, including its disabled state. */
private val LocalControlColor = staticCompositionLocalOf { Color.Unspecified }

/** Carbon tile: flat contextual layer, without ornamental borders or elevation. */
@Composable
fun CarbonPanel(
    modifier: Modifier = Modifier,
    borderWidth: Dp = 0.dp,
    background: Color = backbeeColors.bgPanel,
    border: Color = backbeeColors.borderColor,
    contentPadding: Dp = Dimens.space4,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.background(background)
        .then(if (borderWidth > 0.dp) Modifier.border(borderWidth, border) else Modifier)
        .padding(contentPadding), content = content)
}

internal enum class CarbonButtonVariant { Filled, Outline, DangerOutline }

internal data class CarbonButtonStateColors(val fill: Color, val ink: Color, val border: Color)

/** Resolve the same colors rendered by the control; disabled wins over every interaction. */
internal fun resolveCarbonButtonColors(
    colors: BackbeeColors,
    background: Color,
    contentColor: Color,
    variant: CarbonButtonVariant,
    enabled: Boolean,
    pressed: Boolean,
    hovered: Boolean,
): CarbonButtonStateColors {
    if (!enabled) return CarbonButtonStateColors(colors.disabled, colors.textDisabled, colors.disabled)
    val activeOutline = variant != CarbonButtonVariant.Filled && (pressed || hovered)
    val fill = when {
        // Fill grades come from the theme so each mode keeps textOnColor at 4.5:1.
        activeOutline && variant == CarbonButtonVariant.DangerOutline ->
            if (pressed) colors.accentAlertActive else colors.accentAlertHover
        activeOutline -> if (pressed) colors.accentPrimaryActive else colors.accentPrimary
        pressed -> Color.Black.copy(alpha = 0.22f).compositeOver(background)
        hovered -> Color.Black.copy(alpha = 0.10f).compositeOver(background)
        else -> background
    }
    // Each accent has its own ink: paper on Press Green, near-black on the
    // lifted green, and the alert fills follow the same rule. One ink for
    // every fill was Carbon's convention, not this palette's.
    val activeInk = if (variant == CarbonButtonVariant.DangerOutline) colors.onAccentAlert else colors.onAccentPrimary
    return CarbonButtonStateColors(
        fill = fill,
        ink = if (activeOutline) activeInk else contentColor,
        border = if (activeOutline) fill else colors.interactive,
    )
}

/** Square Carbon action; feedback changes color, never layout or position. */
@Composable
fun CarbonButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    background: Color = backbeeColors.accentPrimary,
    contentColor: Color = backbeeColors.onAccentPrimary,
    borderWidth: Dp = 0.dp,
    minHeight: Dp = Dimens.touchTarget,
    content: @Composable RowScope.() -> Unit,
) = CarbonButtonControl(onClick, modifier, enabled, background, contentColor, borderWidth,
    minHeight, CarbonButtonVariant.Filled, content)

@Composable
private fun CarbonButtonControl(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    background: Color,
    contentColor: Color,
    borderWidth: Dp,
    minHeight: Dp,
    variant: CarbonButtonVariant,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = backbeeColors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val stateColors = resolveCarbonButtonColors(colors, background, contentColor, variant, enabled, pressed, hovered)
    Row(
        modifier.fillMaxWidth().heightIn(min = minHeight.coerceAtLeast(48.dp))
            .background(stateColors.fill)
            .then(if (borderWidth > 0.dp) Modifier.border(borderWidth, stateColors.border) else Modifier)
            .hoverable(interaction, enabled)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .drawBehind {
                if (focused && enabled) {
                    val line = 2.dp.toPx()
                    drawRect(colors.focus, style = androidx.compose.ui.graphics.drawscope.Stroke(line * 2))
                    drawRect(Color.White, Offset(line, line), Size((size.width - line * 2).coerceAtLeast(0f), (size.height - line * 2).coerceAtLeast(0f)), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
                }
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides stateColors.ink, LocalControlColor provides stateColors.ink) { content() }
    }
}

/** Tertiary action uses Carbon's interactive outline, not a raised paper key. */
@Composable
fun CarbonOutlineButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    minHeight: Dp = 48.dp,
    contentColor: Color = backbeeColors.interactive,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = backbeeColors
    // Preserve existing callers' semantic danger ink. Only this exact theme token
    // opts into danger; arbitrary supplied colors are never classified by hue.
    val variant = if (contentColor == colors.textAlert) CarbonButtonVariant.DangerOutline else CarbonButtonVariant.Outline
    CarbonButtonControl(onClick, modifier, enabled, colors.bgPanel, contentColor,
        borderWidth = 1.dp, minHeight = minHeight, variant = variant, content = content)
}

@Composable
fun Label(text: String, modifier: Modifier = Modifier, color: Color = backbeeColors.textMuted, style: TextStyle = BackbeeType.label) {
    val control = LocalControlColor.current
    Text(text, modifier, color = if (control != Color.Unspecified) control else color, style = style)
}

@Composable
fun Mono(text: String, modifier: Modifier = Modifier, color: Color = backbeeColors.textPrimary,
    style: TextStyle = BackbeeType.mono, maxLines: Int = Int.MAX_VALUE, textAlign: TextAlign? = null) {
    val control = LocalControlColor.current
    Text(text, modifier, color = if (control != Color.Unspecified) control else color,
        style = style, maxLines = maxLines, textAlign = textAlign)
}

/** Diagnostic detail keeps Plex Mono as a small echo of the previous identity. */
@Composable
fun Readout(lines: List<String>, modifier: Modifier = Modifier, tone: Color = backbeeColors.onInverseFunctional) {
    if (lines.isEmpty()) return
    val colors = backbeeColors
    Column(modifier.fillMaxWidth().background(colors.bgInverse)
        .drawBehind { drawRect(colors.brandAccent, size = Size(3.dp.toPx(), size.height)) }
        .padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        lines.forEach { Text(it, style = BackbeeType.monoSmall, color = tone) }
    }
}

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
    Text(glyph, modifier, style = BackbeeType.mono, color = color)
}

/** Carbon tags are the intentional rounded exception to square tiles/controls. */
@Composable
fun StatusChip(text: String, modifier: Modifier = Modifier,
    background: Color = backbeeColors.accentPrimary, contentColor: Color = backbeeColors.onAccentPrimary) {
    Text(text, modifier.background(background, RoundedCornerShape(12.dp))
        .padding(horizontal = 8.dp, vertical = 4.dp), style = BackbeeType.labelSmall, color = contentColor)
}

@Composable
fun CarbonProgress(fraction: Float, modifier: Modifier = Modifier, height: Dp = 4.dp, color: Color = backbeeColors.accentPrimary) {
    val progress = if (fraction.isFinite()) fraction.coerceIn(0f, 1f) else 0f
    Box(modifier.fillMaxWidth().height(height).background(backbeeColors.borderColor)
        .semantics { progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f) }) {
        Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(color))
    }
}

@Composable
fun CarbonDivider(modifier: Modifier = Modifier, thickness: Dp = 1.dp) {
    Box(modifier.fillMaxWidth().height(thickness).background(backbeeColors.borderColor))
}

/**
 * Persistent label and bottom rule follow Carbon's text-input anatomy.
 * [label] controls visual presentation; supply [accessibleLabel] to name the editable
 * node itself. [errorMessage] is announced only when [isError] is true.
 */
@Composable
fun CarbonTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: (@Composable () -> Unit)? = null,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    textStyle: TextStyle = BackbeeType.bodySmall,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    enabled: Boolean = true,
    isError: Boolean = false,
    accessibleLabel: String? = null,
    errorMessage: String = "Invalid input",
) {
    val colors = backbeeColors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        label?.invoke()
        BasicTextField(value = value, onValueChange = onValueChange,
            enabled = enabled, singleLine = singleLine, minLines = minLines, maxLines = maxLines,
            textStyle = textStyle.copy(color = if (enabled) colors.textPrimary else colors.textDisabled),
            keyboardOptions = keyboardOptions, interactionSource = interaction,
            cursorBrush = SolidColor(colors.textPrimary),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .semantics {
                    accessibleLabel?.let { contentDescription = it }
                    if (isError) error(errorMessage)
                }
                .background(colors.field)
                .drawBehind {
                    if (focused) drawRect(colors.focus, style = androidx.compose.ui.graphics.drawscope.Stroke(4.dp.toPx()))
                    else drawLine(if (isError) colors.textAlert else colors.borderStrong,
                        Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
                }
                .padding(horizontal = 16.dp, vertical = 14.dp),
        )
    }
}

/** A native Carbon toggle with an accessible 48dp hit area and 48x24 track. */
@Composable
fun CarbonToggle(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = backbeeColors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val active = if (onCheckedChange != null) Modifier.toggleable(checked, interaction, null,
        enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange) else Modifier
    Box(modifier.size(48.dp).then(active)
        .then(if (focused) Modifier.border(2.dp, colors.focus) else Modifier), contentAlignment = Alignment.Center) {
        Box(Modifier.size(48.dp, 24.dp).background(
            when { !enabled -> colors.disabled; checked -> colors.accentFunctional; else -> colors.borderStrong }, CircleShape),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart) {
            Box(Modifier.padding(horizontal = 4.dp).size(16.dp).background(Color.White, CircleShape))
        }
    }
}

/** Carbon modal anatomy; scrollable body and full-width action footer. */
@Composable
fun CarbonDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).background(backbeeColors.bgPanel)) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                CompositionLocalProvider(LocalContentColor provides backbeeColors.textPrimary) { title(); text() }
            }
            CarbonDivider()
            Row(Modifier.fillMaxWidth()) {
                dismissButton?.let { Box(Modifier.weight(1f)) { it() } }
                Box(Modifier.weight(1f)) { confirmButton() }
            }
        }
    }
}
