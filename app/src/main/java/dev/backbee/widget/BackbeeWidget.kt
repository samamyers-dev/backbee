package dev.backbee.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.glance.color.ColorProvider
import androidx.glance.text.FontFamily
import androidx.glance.layout.Box
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dev.backbee.BackbeeApp
import dev.backbee.ui.theme.BackbeePalette
import dev.backbee.ui.theme.colors

/**
 * Resume/pause and skip forward. Nothing else.
 *
 * The widget exists so that starting a dog walk is one tap from the home screen.
 * Anything more would be a second place to make decisions, which is exactly what
 * the app is trying to avoid.
 */
class BackbeeWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as BackbeeApp).container
        val show = container.showRepository.activeShow()
        val target = show?.let { container.playbackRepository.resumeTarget(it.id, it.direction) }
        val palette = container.settingsStore.current().colorPalette

        val total = show?.let { container.playbackRepository.episodeCount(it.id) } ?: 0
        val number = target?.let { row ->
            row.episodeNumber ?: if (show?.direction?.isNewestFirst == true && total > 0) {
                (total - row.orderIndex).coerceAtLeast(1)
            } else {
                row.orderIndex + 1
            }
        }

        val title = target?.title ?: show?.title ?: "Nothing active"
        val subtitle = number?.let { "Ep $it" } ?: "Add a show"

        provideContent {
            WidgetBody(title = title, subtitle = subtitle, palette = palette)
        }
    }
}

@Composable
private fun WidgetBody(title: String, subtitle: String, palette: BackbeePalette) {
    val day = palette.colors(darkTheme = false)
    val night = palette.colors(darkTheme = true)
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(day = day.bgPanel, night = night.bgPanel))
            .padding(16.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Text(
            text = title,
            maxLines = 2,
            style = TextStyle(
                color = ColorProvider(day = day.textPrimary, night = night.textPrimary),
                fontFamily = FontFamily.SansSerif,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
        Text(
            text = subtitle,
            style = TextStyle(
                color = ColorProvider(day = day.textMuted, night = night.textMuted),
                fontFamily = FontFamily.SansSerif,
                fontSize = 12.sp,
            ),
        )
        Spacer(GlanceModifier.height(8.dp))
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            WidgetButton(
                label = "Play / pause",
                palette = palette,
                modifier = GlanceModifier.clickable(actionRunCallback<TogglePlaybackAction>()),
            )
            Spacer(GlanceModifier.width(8.dp))
            WidgetButton(
                label = "Skip",
                palette = palette,
                modifier = GlanceModifier.clickable(actionRunCallback<SkipForwardAction>()),
            )
        }
    }
}

@Composable
private fun WidgetButton(label: String, palette: BackbeePalette, modifier: GlanceModifier = GlanceModifier) {
    val day = palette.colors(darkTheme = false)
    val night = palette.colors(darkTheme = true)
    Box(
        modifier = modifier
            .height(48.dp)
            .background(ColorProvider(day = day.accentPrimary, night = night.accentPrimary))
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = TextStyle(
                color = ColorProvider(day = day.onAccentPrimary, night = night.onAccentPrimary),
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
            ),
        )
    }
}

class TogglePlaybackAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WidgetPlaybackCommands.toggle(context)
    }
}

class SkipForwardAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WidgetPlaybackCommands.skipForward(context)
    }
}

class BackbeeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BackbeeWidget()
}
