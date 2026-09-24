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

/**
 * The FREE THEM. palette (the "Free Them. — Home" refresh) on the Carbon
 * component structure. Token names are Carbon's so no screen changes; values
 * are the design system's. See docs/DESIGN.md for the mapping and the
 * contrast figures behind the text grades.
 */
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
    val accentPrimary: Color,
    /** Outline buttons fill with the primary accent on hover and this on press. */
    val accentPrimaryActive: Color,
    val accentSecondary: Color,
    val accentFunctional: Color,
    val accentInfo: Color,
    val accentAlert: Color,
    /** Danger outline buttons: hover fill, then press fill. Dark enough for [textOnColor]. */
    val accentAlertHover: Color,
    val accentAlertActive: Color,
    val onAccentPrimary: Color,
    val onAccentSecondary: Color,
    val onAccentAlert: Color,
    /** Fluoro Pink: the readout rule, the visualiser bars, the launcher ribbon. */
    val brandAccent: Color,
    /** Ink on any filled interactive surface. Paper on Press Green; near-black on the lifted green. */
    val textOnColor: Color,
) {
    val interactive: Color get() = textAccent
    /** Fields and selected layers need the higher-contrast link grade. */
    val textAccentOnField: Color get() = textAccentSelected
}

// Raw palette. Components use the semantic tokens below, never these.
private val Paper = Color(0xFFF4F1E8)
private val PaperLifted = Color(0xFFFBF9F3)
private val Field = Color(0xFFEAE6DB)
private val Pressroom = Color(0xFF101A13)
private val Bottle = Color(0xFF1B2E1F)
private val PressGreen = Color(0xFF0F7A4A)
private val PressGreenDeep = Color(0xFF0B5E38)
private val PressGreenDeeper = Color(0xFF094A2C)
private val PressGreenLifted = Color(0xFF2FA96C)
private val PressGreenBright = Color(0xFF45C182)
private val PressGreenBrighter = Color(0xFF6ED49B)
private val VermilionInk = Color(0xFFB8301E)
private val VermilionLifted = Color(0xFFF26B57)
private val NearBlack = Color(0xFF06170E)
private val Fluoro = Color(0xFFFF4B7D)
private val Amber = Color(0xFFF2B705)
private val Vermilion = Color(0xFFE8442E)

internal val LightColors = BackbeeColors(
    bgPage = Paper, bgPanel = PaperLifted, layer02 = Field,
    bgInverse = Bottle, textPrimary = Bottle,
    textInverse = Paper, textMuted = Color(0xFF5C685B),
    borderColor = Color(0xFFD1D2C8), borderStrong = Bottle,
    field = Field, layerSelected = Field,
    // Press Green is the fill; as text on the recessed field layer it is 4.3:1,
    // so the text grades sit one and two steps deeper.
    focus = PressGreen, textAccent = PressGreenDeep, textAccentSelected = PressGreenDeeper,
    textFunctional = PressGreenDeep,
    // Amber ink, pressed one step darker than the design's #8A6A00 to clear 4.5:1 on paper.
    textSecondary = Color(0xFF7A5D00),
    // Vermilion is 3.5:1 against paper, as text or under paper text, so on the
    // light ground the alert colour is its ink grade in both roles.
    textAlert = VermilionInk,
    textDisabled = Color(0xFF889084), disabled = Color(0xFFD3D4CA),
    onInverseFunctional = PressGreenLifted, onInverseAlert = VermilionLifted,
    accentPrimary = PressGreen, accentPrimaryActive = PressGreenDeep,
    accentSecondary = Fluoro, accentFunctional = PressGreen, accentInfo = Fluoro,
    accentAlert = VermilionInk, accentAlertHover = VermilionInk, accentAlertActive = Color(0xFF8F2416),
    onAccentPrimary = Paper, onAccentSecondary = Color(0xFF2B0A16), onAccentAlert = Paper,
    brandAccent = Fluoro, textOnColor = Paper,
)
internal val DarkColors = BackbeeColors(
    bgPage = Pressroom, bgPanel = Bottle, layer02 = Color(0xFF2C3E2F),
    bgInverse = Paper, textPrimary = Paper,
    textInverse = Pressroom, textMuted = Color(0xFFC0C0B8),
    borderColor = Color(0xFF343C35), borderStrong = Color(0xFF8D9088),
    field = Color(0xFF2C3E2F), layerSelected = Color(0xFF3B4A3D),
    // The lifted green is the fill; as text on the raised layer it is 3.9:1, so
    // the text grades sit one and two steps brighter.
    focus = Paper, textAccent = PressGreenBright, textAccentSelected = PressGreenBrighter,
    textFunctional = PressGreenBright, textSecondary = Amber,
    // Vermilion lifted twice for text: the swatch is 4.49:1 on pressroom and
    // 3.7:1 on the raised layer.
    textAlert = Color(0xFFF78D7C),
    textDisabled = Color(0xFF777B73), disabled = Color(0xFF323A33),
    onInverseFunctional = PressGreenDeep, onInverseAlert = VermilionInk,
    accentPrimary = PressGreenLifted, accentPrimaryActive = PressGreenBright,
    accentSecondary = Fluoro, accentFunctional = PressGreenLifted, accentInfo = Fluoro,
    accentAlert = Vermilion, accentAlertHover = Vermilion, accentAlertActive = VermilionLifted,
    // Near-black ink on every dark-mode fill, as the design puts on its lifted green.
    onAccentPrimary = NearBlack, onAccentSecondary = Color(0xFF2B0A16), onAccentAlert = NearBlack,
    brandAccent = Fluoro, textOnColor = NearBlack,
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
