package dev.backbee.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
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
        val target = show?.let { container.playbackRepository.resumeTarget(it.id) }

        val title = target?.title ?: show?.title ?: "Nothing active"
        val subtitle = target?.let { "Ep ${it.episodeNumber ?: (it.orderIndex + 1)}" } ?: "Add a show"

        provideContent {
            GlanceTheme {
                WidgetBody(title = title, subtitle = subtitle)
            }
        }
    }
}

@Composable
private fun WidgetBody(title: String, subtitle: String) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .padding(12.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Text(
            text = title,
            maxLines = 2,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
        Text(
            text = subtitle,
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
        )
        Spacer(GlanceModifier.height(8.dp))
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            WidgetButton(
                label = "Play / Pause",
                modifier = GlanceModifier.clickable(actionRunCallback<TogglePlaybackAction>()),
            )
            Spacer(GlanceModifier.width(8.dp))
            WidgetButton(
                label = "Skip",
                modifier = GlanceModifier.clickable(actionRunCallback<SkipForwardAction>()),
            )
        }
    }
}

@Composable
private fun WidgetButton(label: String, modifier: GlanceModifier = GlanceModifier) {
    Text(
        text = label,
        modifier = modifier
            .background(GlanceTheme.colors.primaryContainer)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        style = TextStyle(
            color = GlanceTheme.colors.onPrimaryContainer,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
        ),
    )
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
