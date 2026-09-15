package dev.backbee.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
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
 * paused. Tapping the bar itself opens the playing episode's page - the full
 * player, with the scan bar and the description.
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
    skipBackSeconds: Int = 10,
    skipForwardSeconds: Int = 30,
    onOpen: () -> Unit,
    onTogglePlay: () -> Unit,
    onSkipBack: () -> Unit,
    onSkipForward: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = backbeeColors
    val fraction = if (durationMs <= 0) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)

    androidx.compose.foundation.layout.BoxWithConstraints(modifier.fillMaxWidth().background(colors.bgPanel)) {
        val stacked = maxWidth < 360.dp || androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f
        Column {
            CarbonProgress(fraction, height = 3.dp)
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clickable(onClickLabel = "Open the playing episode", role = Role.Button, onClick = onOpen)
                    .padding(horizontal = Dimens.space3, vertical = Dimens.space2),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.space3),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Artwork(artworkUrl, title, size = 44.dp)
                    VisualizerBackdrop(Modifier.size(44.dp))
                    Visualizer(playing = isPlaying, modifier = Modifier.size(26.dp))
                }
                Column(Modifier.weight(1f)) {
                    Mono(
                        text = if (isBuffering) "Buffering…" else subtitle.orEmpty(),
                        style = BackbeeType.monoMicro,
                        color = if (isBuffering) colors.textSecondary else colors.textAccent,
                        maxLines = 1,
                    )
                    Text(title, style = BackbeeType.bodySmall, color = colors.textPrimary,
                        maxLines = if (stacked) 2 else 1, overflow = TextOverflow.Ellipsis)
                    Mono(
                        text = ArchiveProgress.formatClock(positionMs.coerceAtLeast(0) / 1000) +
                            if (durationMs > 0) "  −${ArchiveProgress.formatClock((durationMs - positionMs).coerceAtLeast(0) / 1000)}" else "",
                        style = BackbeeType.monoMicro, color = colors.textMuted, maxLines = 1,
                    )
                }
                if (!stacked) TransportKeys(isPlaying, skipBackSeconds, skipForwardSeconds, onTogglePlay, onSkipBack, onSkipForward)
            }
            if (stacked) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically) {
                    TransportKeys(isPlaying, skipBackSeconds, skipForwardSeconds, onTogglePlay, onSkipBack, onSkipForward)
                }
            }
        }
    }
}

@Composable
private fun TransportKeys(
    isPlaying: Boolean, skipBackSeconds: Int, skipForwardSeconds: Int,
    onTogglePlay: () -> Unit, onSkipBack: () -> Unit, onSkipForward: () -> Unit,
) {
    BarKey("−$skipBackSeconds", "Skip back $skipBackSeconds seconds", onSkipBack)
    BarKey(if (isPlaying) "❚❚" else "▶", if (isPlaying) "Pause" else "Play", onTogglePlay,
        background = backbeeColors.accentPrimary, contentColor = backbeeColors.onAccentPrimary)
    BarKey("+$skipForwardSeconds", "Skip forward $skipForwardSeconds seconds", onSkipForward)
}

/** Flat icon key with a full native touch target. */
@Composable
private fun BarKey(
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
    background: Color = backbeeColors.bgPage,
    contentColor: Color = backbeeColors.textPrimary,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(background)
            .clickable(onClickLabel = contentDescription, onClick = onClick)
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Mono(label, style = BackbeeType.monoSmall, color = contentColor, modifier = Modifier.clearAndSetSemantics {})
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
    Canvas(modifier) { drawBars(bars, colors.brandAccent) { levelFor(phase, it) } }
}

/**
 * Bars at rest, not bars switched off. Rendered any lower they read as a row of
 * dots - which looks like something failing to load rather than like audio
 * that is paused.
 */
private const val FLAT_LEVEL = 0.24f

/** Fixed Gray 100 scrim keeps the small ochre brand detail legible over any artwork. */
private val Scrim = Color(0xF0161616)
private val ScrimForeground = Color(0xFFC6C6C6)

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
