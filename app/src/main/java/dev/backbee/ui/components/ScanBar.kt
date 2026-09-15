package dev.backbee.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.*
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.backbee.core.playback.ArchiveProgress
import dev.backbee.ui.theme.BackbeeType
import dev.backbee.ui.theme.Stroke
import dev.backbee.ui.theme.backbeeColors
import kotlin.math.abs

/**
 * The scan-through bar: a classic draggable audio scrubber, in the house style.
 *
 * It is not on every view - it appears only when invoked, because eyes-free is
 * the default posture and a permanently draggable strip next to the transport
 * keys is exactly the mis-tap the giant buttons exist to avoid. Drag to a point
 * (or tap one) and the player seeks there on release.
 */
@Composable
fun ScanBar(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onCollapse: (() -> Unit)? = null,
) {
    val colors = backbeeColors

    // While a finger is down, the bar follows the finger, not the player.
    var dragFraction by remember { mutableStateOf<Float?>(null) }

    // After release, hold the target until the player's position catches up -
    // otherwise the bar snaps back to the stale position for up to a second
    // before jumping forward again, which reads as the seek failing.
    var pendingFraction by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(positionMs) {
        pendingFraction?.let { pending ->
            if (durationMs > 0 && abs(positionMs - (pending * durationMs).toLong()) < SETTLE_TOLERANCE_MS) {
                pendingFraction = null
            }
        }
    }

    val fraction = dragFraction
        ?: pendingFraction
        ?: if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    fun seekToFraction(value: Float) {
        if (durationMs <= 0 || !value.isFinite()) return
        val clamped = value.coerceIn(0f, 1f)
        pendingFraction = clamped
        onSeek((clamped * durationMs).toLong())
    }

    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .semantics {
                    contentDescription = "Scan through the episode"
                    progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                    if (durationMs > 0) setProgress { value ->
                        if (value.isFinite()) { seekToFraction(value); true } else false
                    }
                }
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown || durationMs <= 0) false
                    else when (event.key) {
                        Key.DirectionRight -> { seekToFraction(fraction + 0.05f); true }
                        Key.DirectionLeft -> { seekToFraction(fraction - 0.05f); true }
                        else -> false
                    }
                }
                .focusable()
                .pointerInput(durationMs) {
                    if (durationMs <= 0) return@pointerInput
                    detectTapGestures { offset -> seekToFraction(offset.x / size.width) }
                }
                .pointerInput(durationMs) {
                    if (durationMs <= 0) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                        },
                        onHorizontalDrag = { change, _ ->
                            dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                            change.consume()
                        },
                        onDragEnd = {
                            dragFraction?.let { seekToFraction(it) }
                            dragFraction = null
                        },
                        onDragCancel = { dragFraction = null },
                    )
                }
                .drawBehind {
                    val at = size.width * fraction
                    val track = 4.dp.toPx()
                    val y = (size.height - track) / 2
                    drawRect(colors.borderStrong, Offset(0f, y), Size(size.width, track))
                    drawRect(colors.textPrimary, Offset(0f, y), Size(at, track))
                    val radius = 7.dp.toPx()
                    drawCircle(colors.textPrimary, radius, Offset(at.coerceIn(radius, size.width.coerceAtLeast(radius * 2) - radius), size.height / 2))
                },
        )

        Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
            val shownMs = ((dragFraction ?: pendingFraction)?.let { (it * durationMs).toLong() }) ?: positionMs
            Mono(
                text = (if (dragFraction != null) "→ " else "") + ArchiveProgress.formatClock(shownMs / 1000),
                style = BackbeeType.monoSmall,
                color = if (dragFraction != null) colors.textAccent else colors.textPrimary,
            )
            Spacer(Modifier.weight(1f))
            if (durationMs > 0) {
                Mono(
                    text = "−${ArchiveProgress.formatClock(((durationMs - shownMs).coerceAtLeast(0)) / 1000)}",
                    style = BackbeeType.monoSmall,
                    color = colors.textMuted,
                )
            }
            onCollapse?.let { collapse ->
                Mono(
                    text = "Hide",
                    style = BackbeeType.monoSmall,
                    color = colors.textMuted,
                    modifier = Modifier
                        .clickable(onClickLabel = "Hide the scan bar", onClick = collapse)
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                )
            }
        }
    }
}

/** Close enough for the held target to hand back to the live position. */
private const val SETTLE_TOLERANCE_MS = 2_000L
