package dev.backbee.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.backbee.core.playback.ArchiveProgress
import dev.backbee.ui.theme.BackbeeType
import dev.backbee.ui.theme.Dimens
import dev.backbee.ui.theme.Stroke
import dev.backbee.ui.theme.backbeeColors
import kotlin.math.PI
import kotlin.math.sin

/**
 * The one piece of the app that is always on screen once something is playing.
 *
 * It lives outside the NavHost, so it survives every tab switch: whatever page
 * you are on, the thing making sound is named, timed, and one tap from being
 * paused. Tapping the bar itself goes back to Now.
 */
@Composable
fun NowPlayingBar(
    title: String,
    subtitle: String?,
    artworkUrl: String?,
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    isBuffering: Boolean,
    onOpen: () -> Unit,
    onTogglePlay: () -> Unit,
    onSkipBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = backbeeColors
    val fraction = if (durationMs <= 0) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)

    Column(modifier.fillMaxWidth().background(colors.bgPanel)) {
        // A hairline rather than a track: at this size a bordered progress bar
        // reads as a control you could drag, and this one is not draggable.
        Box(Modifier.fillMaxWidth().height(3.dp).background(colors.borderColor.copy(alpha = 0.25f))) {
            Box(Modifier.fillMaxWidth(fraction).height(3.dp).background(colors.accentPrimary))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(horizontal = Dimens.space3, vertical = Dimens.space2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.space3),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Artwork(artworkUrl, title, size = 44.dp)
                // Over the artwork, so the motion is where the eye already is,
                // on a scrim so the bars read against whatever the art happens
                // to be.
                VisualizerBackdrop(Modifier.size(44.dp))
                Visualizer(playing = isPlaying, modifier = Modifier.size(26.dp))
            }

            Column(Modifier.weight(1f)) {
                Mono(
                    text = when {
                        isBuffering -> "BUFFERING…"
                        else -> subtitle?.uppercase().orEmpty()
                    },
                    style = BackbeeType.monoMicro,
                    color = if (isBuffering) colors.accentSecondary else colors.accentPrimary,
                    maxLines = 1,
                )
                Text(
                    text = title,
                    style = BackbeeType.bodySmall,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Mono(
                    text = ArchiveProgress.formatClock(positionMs / 1000) +
                        if (durationMs > 0) "  −${ArchiveProgress.formatClock((durationMs - positionMs) / 1000)}" else "",
                    style = BackbeeType.monoMicro,
                    color = colors.textMuted,
                    maxLines = 1,
                )
            }

            BarKey("−10", onClick = onSkipBack)
            BarKey(
                label = if (isPlaying) "❚❚" else "▶",
                onClick = onTogglePlay,
                background = colors.accentPrimary,
                contentColor = colors.onAccentPrimary,
            )
        }
    }
}

/**
 * A square key sized for a thumb without a glance. Not [BrutalButton]: that
 * reserves room for its shadow, which at bar height eats the whole row.
 */
@Composable
private fun BarKey(
    label: String,
    onClick: () -> Unit,
    background: Color = backbeeColors.bgPage,
    contentColor: Color = backbeeColors.textPrimary,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(background)
            .border(Stroke.divider, backbeeColors.borderColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Mono(label, style = BackbeeType.monoSmall, color = contentColor)
    }
}

/**
 * Hard-edged bars that move while audio is moving and sit flat when it is not.
 *
 * It is driven by the clock, not by the signal: reading real amplitude needs
 * RECORD_AUDIO, which is an absurd price for decoration. What it is honest
 * about is the only thing anyone reads it for - whether the app is playing.
 */
@Composable
private fun Visualizer(
    playing: Boolean,
    modifier: Modifier = Modifier,
    bars: Int = 5,
) {
    val colors = backbeeColors
    if (!playing) {
        Canvas(modifier) { drawBars(bars, ScrimForeground) { FLAT_LEVEL } }
        return
    }

    val transition = rememberInfiniteTransition(label = "visualizer")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )
    Canvas(modifier) { drawBars(bars, colors.accentPrimary) { levelFor(phase, it) } }
}

private const val FLAT_LEVEL = 0.14f

/**
 * The chip is a dark lens over the artwork in both themes rather than the
 * theme's inverse surface. The accent is ochre, which sits at 4.6:1 on espresso
 * and 1.9:1 on paper - flipping the backdrop with the theme would make the bars
 * disappear in dark mode exactly as it did to the readout text.
 */
private val Scrim = Color(0xC42E2824)
private val ScrimForeground = Color(0x8CF2EDE4)

private fun DrawScope.drawBars(
    bars: Int,
    color: Color,
    level: (Int) -> Float,
) {
    val slot = size.width / bars
    val width = slot * 0.6f
    repeat(bars) { i ->
        val h = size.height * level(i).coerceIn(0.05f, 1f)
        drawRect(color, Offset(i * slot, size.height - h), Size(width, h))
    }
}

/**
 * Two sines per bar at rates that do not divide into each other, so the row
 * never collapses into a single travelling wave.
 */
private fun levelFor(phase: Float, index: Int): Float {
    val t = phase * 2f * PI.toFloat()
    val a = sin(t * (1f + index * 0.37f) + index)
    val b = sin(t * (2.3f + index * 0.11f) + index * 2f)
    return 0.5f + 0.34f * a + 0.16f * b
}

/** Fills the artwork square behind the bars so they stay legible. */
@Composable
private fun VisualizerBackdrop(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(Scrim))
}
