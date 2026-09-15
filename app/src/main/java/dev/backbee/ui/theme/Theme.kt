package dev.backbee.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import dev.backbee.R

/** Carbon Gray 10 / Gray 100, adapted for native Compose. See docs/DESIGN.md. */
@Immutable
data class BackbeeColors(
    val bgPage: Color,
    val bgPanel: Color,
    val layer02: Color,
    val bgInverse: Color,
    val textPrimary: Color,
    val textInverse: Color,
    val textMuted: Color,
    val borderColor: Color,
    val borderStrong: Color,
    val field: Color,
    val layerSelected: Color,
    val focus: Color,
    val textAccent: Color,
    val textAccentSelected: Color,
    val textFunctional: Color,
    val textSecondary: Color,
    val textAlert: Color,
    val textDisabled: Color,
    val disabled: Color,
    val onInverseFunctional: Color,
    val onInverseAlert: Color,
    val accentPrimary: Color = Color(0xFF0F62FE),
    val accentSecondary: Color = Color(0xFF393939),
    val accentFunctional: Color = Color(0xFF198038),
    val accentInfo: Color = Color(0xFF0043CE),
    val accentAlert: Color = Color(0xFFDA1E28),
    val onAccentPrimary: Color = Color.White,
    val onAccentSecondary: Color = Color.White,
    val onAccentAlert: Color = Color.White,
    /** The old ochre is decorative, never a replacement for Carbon action/status colors. */
    val brandAccent: Color = Color(0xFFC99653),
    val textOnColor: Color = Color.White,
) {
    val interactive: Color get() = textAccent
    /** Fields and selected layers need the higher-contrast link grade. */
    val textAccentOnField: Color get() = textAccentSelected
}

internal val LightColors = BackbeeColors(
    bgPage = Color(0xFFF4F4F4), bgPanel = Color.White, layer02 = Color(0xFFF4F4F4),
    bgInverse = Color(0xFF393939), textPrimary = Color(0xFF161616),
    textInverse = Color.White, textMuted = Color(0xFF525252),
    borderColor = Color(0xFFE0E0E0), borderStrong = Color(0xFF8D8D8D),
    field = Color(0xFFE0E0E0), layerSelected = Color(0xFFE0E0E0),
    focus = Color(0xFF0F62FE), textAccent = Color(0xFF0F62FE), textAccentSelected = Color(0xFF0043CE),
    textFunctional = Color(0xFF0E6027), textSecondary = Color(0xFF8A3800),
    textAlert = Color(0xFFDA1E28), textDisabled = Color(0xFF8D8D8D), disabled = Color(0xFFC6C6C6),
    onInverseFunctional = Color(0xFFA7F0BA), onInverseAlert = Color(0xFFFFB3B8),
)
internal val DarkColors = BackbeeColors(
    bgPage = Color(0xFF161616), bgPanel = Color(0xFF262626), layer02 = Color(0xFF393939),
    bgInverse = Color(0xFFF4F4F4), textPrimary = Color(0xFFF4F4F4),
    textInverse = Color(0xFF161616), textMuted = Color(0xFFC6C6C6),
    borderColor = Color(0xFF393939), borderStrong = Color(0xFF6F6F6F),
    field = Color(0xFF393939), layerSelected = Color(0xFF525252),
    focus = Color.White, textAccent = Color(0xFF78A9FF), textAccentSelected = Color(0xFFA6C8FF),
    textFunctional = Color(0xFF42BE65), textSecondary = Color(0xFFFFB784),
    textAlert = Color(0xFFFF8389), textDisabled = Color(0xFF6F6F6F), disabled = Color(0xFF393939),
    onInverseFunctional = Color(0xFF0E6027), onInverseAlert = Color(0xFFDA1E28),
)
val LocalBackbeeColors = staticCompositionLocalOf { DarkColors }

object Stroke {
    val thin = 1.dp
    val divider = 1.dp
    val heavy = 2.dp
    val thick = 1.dp
}

/** Carbon spacing scale; interactive controls keep Android's 48dp minimum. */
object Dimens {
    val space1 = 4.dp
    val space2 = 8.dp
    val space3 = 12.dp
    val space4 = 16.dp
    val space5 = 20.dp
    val space6 = 24.dp
    val space8 = 32.dp
    val space10 = 40.dp
    val space12 = 48.dp
    val space16 = 64.dp
    val gutter = 16.dp
    val gap = 8.dp
    val touchTarget = 48.dp
    val giantButton = 80.dp
    val rowMinHeight = 72.dp
    val artworkLarge = 200.dp
    val artworkSmall = 56.dp
}

val PlexSans = FontFamily(
    Font(R.font.ibm_plex_sans_light, FontWeight.Light),
    Font(R.font.ibm_plex_sans_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_semibold, FontWeight.SemiBold),
)
val PlexMono = FontFamily(Font(R.font.ibm_plex_mono_regular, FontWeight.Normal))

object BackbeeType {
    val displayLarge = TextStyle(fontFamily = PlexSans, fontSize = 54.sp, lineHeight = 64.sp, fontWeight = FontWeight.Light)
    val displayMedium = TextStyle(fontFamily = PlexSans, fontSize = 42.sp, lineHeight = 50.sp, fontWeight = FontWeight.Light)
    val displaySmall = TextStyle(fontFamily = PlexSans, fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Normal)
    val title = TextStyle(fontFamily = PlexSans, fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Normal)
    val titleSmall = TextStyle(fontFamily = PlexSans, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)
    val body = TextStyle(fontFamily = PlexSans, fontSize = 16.sp, lineHeight = 24.sp)
    val bodySmall = TextStyle(fontFamily = PlexSans, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.16.sp)
    val mono = TextStyle(fontFamily = PlexMono, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.32.sp)
    val monoSmall = TextStyle(fontFamily = PlexMono, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.32.sp)
    val monoMicro = monoSmall
    val label = TextStyle(fontFamily = PlexSans, fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = 0.16.sp)
    val labelSmall = TextStyle(fontFamily = PlexSans, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.32.sp)
}

private val SquareCorners = RoundedCornerShape(0.dp)
private fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> if (baseContext !== this) baseContext.activity() else null
    else -> null
}

@Composable
fun BackbeeTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            view.context.activity()?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
        }
    }
    // Material is a behavior/accessibility implementation detail, not our visual system.
    val base = if (darkTheme) darkColorScheme() else lightColorScheme()
    val materialScheme = base.copy(
        primary = colors.textAccent, onPrimary = colors.onAccentPrimary,
        primaryContainer = colors.layerSelected, onPrimaryContainer = colors.textPrimary,
        secondary = colors.textAccent, onSecondary = colors.textOnColor,
        secondaryContainer = colors.layerSelected, onSecondaryContainer = colors.textPrimary,
        tertiary = colors.textFunctional, onTertiary = colors.textOnColor,
        tertiaryContainer = colors.layer02, onTertiaryContainer = colors.textPrimary,
        background = colors.bgPage, onBackground = colors.textPrimary,
        surface = colors.bgPanel, onSurface = colors.textPrimary,
        surfaceVariant = colors.layer02, onSurfaceVariant = colors.textMuted,
        surfaceTint = Color.Transparent,
        surfaceBright = colors.layer02, surfaceDim = colors.bgPage,
        surfaceContainer = colors.bgPanel, surfaceContainerLow = colors.bgPage,
        surfaceContainerLowest = colors.bgPage, surfaceContainerHigh = colors.layer02,
        surfaceContainerHighest = colors.layer02,
        inverseSurface = colors.bgInverse, inverseOnSurface = colors.textInverse,
        inversePrimary = if (darkTheme) LightColors.textAccent else DarkColors.textAccent,
        outline = colors.borderStrong, outlineVariant = colors.borderColor,
        error = colors.textAlert, onError = colors.onAccentAlert,
        errorContainer = colors.layer02, onErrorContainer = colors.textAlert,
    )
    val typography = Typography(
        displayLarge = BackbeeType.displayLarge, displayMedium = BackbeeType.displayMedium,
        displaySmall = BackbeeType.displaySmall, headlineLarge = BackbeeType.displaySmall,
        headlineMedium = BackbeeType.title, headlineSmall = BackbeeType.title,
        titleLarge = BackbeeType.title, titleMedium = BackbeeType.titleSmall, titleSmall = BackbeeType.titleSmall,
        bodyLarge = BackbeeType.body, bodyMedium = BackbeeType.bodySmall, bodySmall = BackbeeType.bodySmall,
        labelLarge = BackbeeType.label, labelMedium = BackbeeType.labelSmall, labelSmall = BackbeeType.labelSmall,
    )
    CompositionLocalProvider(LocalBackbeeColors provides colors) {
        MaterialTheme(colorScheme = materialScheme, typography = typography,
            shapes = androidx.compose.material3.Shapes(SquareCorners, SquareCorners, SquareCorners, SquareCorners, SquareCorners),
            content = content)
    }
}

val backbeeColors: BackbeeColors
    @Composable get() = LocalBackbeeColors.current
