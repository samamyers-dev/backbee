package dev.backbee.ui.theme

import android.app.Activity
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/**
 * The FREE THEM. design system, as specified in docs/DESIGN.md.
 *
 * Print-brutalist rather than Material: nothing is rounded, shadows are hard
 * offsets with no blur, and borders are always visible. Material3 is still
 * underneath for its components, but almost every visible property is
 * overridden - see [dev.backbee.ui.components.brutal] for the surface treatment
 * that does most of the work.
 */

// -- Raw palette. Components use the semantic aliases below, never these. -----

private val Paper = Color(0xFFF2EDE4)
private val Basalt = Color(0xFF181614)
private val Espresso = Color(0xFF2E2824)
private val Ochre = Color(0xFFC99653)
private val Terracotta = Color(0xFFAD563E)
private val Sage = Color(0xFF5D7B66)
private val Slate = Color(0xFF3E5066)
private val Crimson = Color(0xFF8C382A)

/**
 * The semantic layer. Material's ColorScheme cannot express shadow colour or a
 * dither tint, so the app reads colours from here and uses MaterialTheme only
 * where a stock component demands it.
 */
@Immutable
data class BackbeeColors(
    val bgPage: Color,
    val bgPanel: Color,
    val bgInverse: Color,
    val textPrimary: Color,
    val textInverse: Color,
    val textMuted: Color,
    val borderColor: Color,
    val shadowColor: Color,
    val ditherLine: Color,
    val accentPrimary: Color = Ochre,
    val accentSecondary: Color = Terracotta,
    val accentFunctional: Color = Sage,
    val accentInfo: Color = Slate,
    val accentAlert: Color = Crimson,
    val onAccentPrimary: Color = Espresso,
    val onAccentSecondary: Color = Paper,
    val onAccentAlert: Color = Paper,
    /**
     * Readout text sits on [bgInverse], which flips with the theme, so the flat
     * accents cannot serve both modes - sage on espresso is 3.1:1 and crimson
     * on espresso is 1.88:1, well under the 4.5:1 needed for body text. These
     * are tuned per mode and all clear 6:1.
     */
    val onInverseFunctional: Color = Color(0xFF9CBFA8),
    val onInverseAlert: Color = Color(0xFFE09383),
    /**
     * The same problem one surface out: the accents above are mixed for fills,
     * where only the 3:1 graphical threshold applies, and ochre on paper is
     * 2.26:1 - so the episode numbers, the bar's "EP n" and every other accent
     * *word* failed at both text sizes. Anything set in an accent on [bgPage] or
     * [bgPanel] uses these instead, tuned per mode to clear 4.5:1 while holding
     * the hue. Fills keep using the accents.
     */
    val textAccent: Color = Ochre,
    val textFunctional: Color = Sage,
    val textSecondary: Color = Terracotta,
    val textAlert: Color = Crimson,
)

private val LightColors = BackbeeColors(
    bgPage = Paper,
    bgPanel = Paper,
    bgInverse = Espresso,
    textPrimary = Espresso,
    textInverse = Paper,
    textMuted = Espresso.copy(alpha = 0.70f),
    borderColor = Espresso,
    shadowColor = Espresso,
    ditherLine = Color(0x0D2E2824),
    // Panel is espresso here, so the readout text must be light.
    onInverseFunctional = Color(0xFF9CBFA8),
    onInverseAlert = Color(0xFFE09383),
    // Darkened against paper: 4.6:1, 4.6:1, 4.6:1, 6.6:1.
    textAccent = Color(0xFF8C622C),
    textFunctional = Color(0xFF55715E),
    textSecondary = Color(0xFFA6523B),
    textAlert = Crimson,
)

private val DarkColors = BackbeeColors(
    bgPage = Basalt,
    bgPanel = Basalt,
    bgInverse = Paper,
    textPrimary = Paper,
    textInverse = Basalt,
    textMuted = Paper.copy(alpha = 0.70f),
    borderColor = Paper,
    shadowColor = Paper,
    ditherLine = Color(0x08F2EDE4),
    // Panel is paper here, so the readout text must be dark.
    onInverseFunctional = Color(0xFF3A5442),
    onInverseAlert = Color(0xFF7A2E22),
    // Lifted against basalt: ochre already clears 6.8:1, the rest did not.
    textAccent = Ochre,
    textFunctional = Color(0xFF678971),
    textSecondary = Color(0xFFC06950),
    textAlert = Color(0xFFCB6250),
)

val LocalBackbeeColors = staticCompositionLocalOf { DarkColors }

// -- Form -------------------------------------------------------------------

/**
 * Border weights and shadow offsets. The spec calls for 1/2/3/4px borders and
 * 3/6/9px shadow offsets; dp is close enough on every density that matters and
 * keeps the look consistent across devices.
 */
object Stroke {
    val thin = 1.dp
    val divider = 2.dp
    val heavy = 3.dp
    val thick = 4.dp
}

object Shadow {
    val sm = 3.dp
    val md = 6.dp
    val lg = 9.dp
}

/** Spacing scale from the spec, plus the touch sizes the product principles demand. */
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

    val gutter = 20.dp
    val gap = 12.dp

    val touchTarget = 64.dp
    val giantButton = 140.dp
    val rowMinHeight = 72.dp
    val artworkLarge = 200.dp
    val artworkSmall = 56.dp
    val ditherCell = 24.dp
}

// -- Type -------------------------------------------------------------------

/**
 * The spec asks for Impact and JetBrains Mono. Neither ships with Android, and
 * this build deliberately bundles no font binaries and makes no network call to
 * fetch one, so the system families stand in: a black-weight sans for the
 * display numerals, and the platform monospace for every readout.
 *
 * The character the design depends on - heavy numerals, monospaced status
 * lines, very wide tracking on small caps - all survives the substitution. If
 * the exact faces matter later, drop the TTFs into res/font and change only
 * these two values.
 */
private val Display = FontFamily.SansSerif
private val Mono = FontFamily.Monospace

object BackbeeType {
    /** Big numerals: episode number, hours listened, the completion stats. */
    val displayLarge = TextStyle(
        fontFamily = Display, fontSize = 56.sp, lineHeight = 56.sp,
        fontWeight = FontWeight.Black, letterSpacing = (-0.02).em,
    )
    val displayMedium = TextStyle(
        fontFamily = Display, fontSize = 44.sp, lineHeight = 46.sp,
        fontWeight = FontWeight.Black, letterSpacing = (-0.02).em,
    )
    val displaySmall = TextStyle(
        fontFamily = Display, fontSize = 32.sp, lineHeight = 34.sp,
        fontWeight = FontWeight.Black, letterSpacing = (-0.02).em,
    )

    /** Episode titles and screen headings. */
    val title = TextStyle(
        fontFamily = Display, fontSize = 22.sp, lineHeight = 27.sp,
        fontWeight = FontWeight.Bold,
    )
    val titleSmall = TextStyle(
        fontFamily = Display, fontSize = 18.sp, lineHeight = 23.sp,
        fontWeight = FontWeight.Bold,
    )

    val body = TextStyle(fontFamily = Display, fontSize = 16.sp, lineHeight = 24.sp)
    val bodySmall = TextStyle(fontFamily = Display, fontSize = 14.sp, lineHeight = 21.sp)

    /** Every readout, timestamp, episode number and status chip. */
    val mono = TextStyle(fontFamily = Mono, fontSize = 14.sp, lineHeight = 20.sp)
    val monoSmall = TextStyle(fontFamily = Mono, fontSize = 12.sp, lineHeight = 17.sp)
    val monoMicro = TextStyle(fontFamily = Mono, fontSize = 10.sp, lineHeight = 14.sp)

    /** Small caps labels - NEXT UP, ON DEVICE. The tracking is the whole effect. */
    val label = TextStyle(
        fontFamily = Mono, fontSize = 12.sp, lineHeight = 16.sp,
        fontWeight = FontWeight.Bold, letterSpacing = 0.15.em,
    )
    val labelSmall = TextStyle(
        fontFamily = Mono, fontSize = 10.sp, lineHeight = 14.sp,
        fontWeight = FontWeight.Bold, letterSpacing = 0.15.em,
    )
}

/**
 * Zero radius, expressed as a CornerBasedShape because that is what Material's
 * Shapes slots are typed as - RectangleShape is a plain Shape and will not fit.
 */
private val SquareCorners = RoundedCornerShape(0.dp)

// -- Entry point ------------------------------------------------------------

@Composable
fun BackbeeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    // Material still backs the stock components (text fields, sliders,
    // switches), so its scheme is mapped onto the same semantic colours to stop
    // anything Material-shaped from looking like a different app.
    val materialScheme = if (darkTheme) {
        darkColorScheme(
            primary = colors.accentPrimary,
            onPrimary = colors.onAccentPrimary,
            secondary = colors.accentSecondary,
            onSecondary = colors.onAccentSecondary,
            background = colors.bgPage,
            onBackground = colors.textPrimary,
            surface = colors.bgPanel,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.bgPanel,
            onSurfaceVariant = colors.textMuted,
            outline = colors.borderColor,
            error = colors.accentAlert,
            onError = colors.onAccentAlert,
        )
    } else {
        lightColorScheme(
            primary = colors.accentPrimary,
            onPrimary = colors.onAccentPrimary,
            secondary = colors.accentSecondary,
            onSecondary = colors.onAccentSecondary,
            background = colors.bgPage,
            onBackground = colors.textPrimary,
            surface = colors.bgPanel,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.bgPanel,
            onSurfaceVariant = colors.textMuted,
            outline = colors.borderColor,
            error = colors.accentAlert,
            onError = colors.onAccentAlert,
        )
    }

    val typography = Typography(
        displayLarge = BackbeeType.displayLarge,
        displayMedium = BackbeeType.displayMedium,
        displaySmall = BackbeeType.displaySmall,
        headlineMedium = BackbeeType.title,
        titleLarge = BackbeeType.title,
        titleMedium = BackbeeType.titleSmall,
        bodyLarge = BackbeeType.body,
        bodyMedium = BackbeeType.bodySmall,
        labelLarge = BackbeeType.label,
        labelMedium = BackbeeType.monoSmall,
        labelSmall = BackbeeType.labelSmall,
    )

    CompositionLocalProvider(LocalBackbeeColors provides colors) {
        MaterialTheme(
            colorScheme = materialScheme,
            typography = typography,
            // Zero radius everywhere. This is the single most defining property
            // of the system, so it is enforced at the theme rather than left to
            // each component to remember.
            shapes = androidx.compose.material3.Shapes(
                extraSmall = SquareCorners,
                small = SquareCorners,
                medium = SquareCorners,
                large = SquareCorners,
                extraLarge = SquareCorners,
            ),
            content = content,
        )
    }
}

/** Shorthand for the semantic palette inside composables. */
val backbeeColors: BackbeeColors
    @Composable get() = LocalBackbeeColors.current
