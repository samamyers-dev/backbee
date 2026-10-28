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

enum class BackbeePalette(val id: String, val label: String, val summary: String) {
    PARCHMENT("parchment", "Parchment", "Warm paper, espresso ink, and terracotta"),
    CARBON("carbon", "Carbon", "Clean graphite and IBM blue"),
    FJORD("fjord", "Fjord", "Cool slate and oceanic teal"),
    PRESSROOM("pressroom", "Pressroom", "Botanical paper and press green");

    companion object {
        val DEFAULT = PARCHMENT
        fun fromId(id: String?): BackbeePalette =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: DEFAULT
    }
}

/**
 * Semantic color system on the Carbon component structure.
 * Four user-selectable palettes (Parchment, Carbon, Fjord, Pressroom) each
 * supply light and dark pairs that clear WCAG 4.5:1 across all surfaces.
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
    /** Decorative accent: the readout rule and the visualiser bars. */
    val brandAccent: Color,
    /** Ink on any filled interactive surface. */
    val textOnColor: Color,
) {
    val interactive: Color get() = textAccent
    /** Fields and selected layers need the higher-contrast link grade. */
    val textAccentOnField: Color get() = textAccentSelected
}

// 1. PARCHMENT — Warm paper & espresso ink, terracotta & ochre
internal val ParchmentLightColors = BackbeeColors(
    bgPage = Color(0xFFF4EFE6), bgPanel = Color(0xFFFAF7F2), layer02 = Color(0xFFEAE3D5),
    bgInverse = Color(0xFF231E1A), textPrimary = Color(0xFF231E1A),
    textInverse = Color(0xFFF4EFE6), textMuted = Color(0xFF5E554B),
    borderColor = Color(0xFFD5CBB8), borderStrong = Color(0xFF7D7265),
    field = Color(0xFFEAE3D5), layerSelected = Color(0xFFE3DAC8),
    focus = Color(0xFF9A4B1A), textAccent = Color(0xFF9A4B1A), textAccentSelected = Color(0xFF7C3A10),
    textFunctional = Color(0xFF1E6641), textSecondary = Color(0xFF6B5138),
    textAlert = Color(0xFFA82A1E),
    textDisabled = Color(0xFF8C8275), disabled = Color(0xFFDED7C8),
    onInverseFunctional = Color(0xFF6FD49E), onInverseAlert = Color(0xFFFF897A),
    accentPrimary = Color(0xFF9A4B1A), accentPrimaryActive = Color(0xFF7C3A10),
    accentSecondary = Color(0xFF1E6641), accentFunctional = Color(0xFF1E6641), accentInfo = Color(0xFF1E6641),
    accentAlert = Color(0xFFA82A1E), accentAlertHover = Color(0xFFA82A1E), accentAlertActive = Color(0xFF821F15),
    onAccentPrimary = Color(0xFFFAF7F2), onAccentSecondary = Color(0xFFFAF7F2), onAccentAlert = Color(0xFFFAF7F2),
    brandAccent = Color(0xFFC99653), textOnColor = Color(0xFFFAF7F2),
)

internal val ParchmentDarkColors = BackbeeColors(
    bgPage = Color(0xFF161311), bgPanel = Color(0xFF211D1A), layer02 = Color(0xFF2E2824),
    bgInverse = Color(0xFFF4EFE6), textPrimary = Color(0xFFF2ECE1),
    textInverse = Color(0xFF161311), textMuted = Color(0xFFB8AEA0),
    borderColor = Color(0xFF3A332D), borderStrong = Color(0xFF8A8073),
    field = Color(0xFF2E2824), layerSelected = Color(0xFF38302A),
    focus = Color(0xFFF2ECE1), textAccent = Color(0xFFE5A95C), textAccentSelected = Color(0xFFF0BE7A),
    textFunctional = Color(0xFF66C992), textSecondary = Color(0xFFC9B39B),
    textAlert = Color(0xFFFF8B7D),
    textDisabled = Color(0xFF736B61), disabled = Color(0xFF2E2824),
    onInverseFunctional = Color(0xFF1E5B3C), onInverseAlert = Color(0xFFA82A1E),
    accentPrimary = Color(0xFFD99B4E), accentPrimaryActive = Color(0xFFEAB26A),
    accentSecondary = Color(0xFF4BB57B), accentFunctional = Color(0xFF4BB57B), accentInfo = Color(0xFF4BB57B),
    accentAlert = Color(0xFFE85D4E), accentAlertHover = Color(0xFFE85D4E), accentAlertActive = Color(0xFFF2796B),
    onAccentPrimary = Color(0xFF161311), onAccentSecondary = Color(0xFF161311), onAccentAlert = Color(0xFF161311),
    brandAccent = Color(0xFFD99B4E), textOnColor = Color(0xFF161311),
)

// 2. CARBON — IBM Carbon Gray 10 / Gray 100, Blue 60 / Blue 40
internal val CarbonLightColors = BackbeeColors(
    bgPage = Color(0xFFF4F4F4), bgPanel = Color(0xFFFFFFFF), layer02 = Color(0xFFE8E8E8),
    bgInverse = Color(0xFF262626), textPrimary = Color(0xFF161616),
    textInverse = Color(0xFFF4F4F4), textMuted = Color(0xFF525252),
    borderColor = Color(0xFFD1D1D1), borderStrong = Color(0xFF6F6F6F),
    field = Color(0xFFE8E8E8), layerSelected = Color(0xFFE0E0E0),
    focus = Color(0xFF0F62FE), textAccent = Color(0xFF0043CE), textAccentSelected = Color(0xFF002D9C),
    textFunctional = Color(0xFF196F31), textSecondary = Color(0xFF4D5358),
    textAlert = Color(0xFFB81922),
    textDisabled = Color(0xFF8D8D8D), disabled = Color(0xFFC6C6C6),
    onInverseFunctional = Color(0xFF6FDC8C), onInverseAlert = Color(0xFFFF8389),
    accentPrimary = Color(0xFF0F62FE), accentPrimaryActive = Color(0xFF0043CE),
    accentSecondary = Color(0xFF196F31), accentFunctional = Color(0xFF196F31), accentInfo = Color(0xFF0043CE),
    accentAlert = Color(0xFFB81922), accentAlertHover = Color(0xFFB81922), accentAlertActive = Color(0xFF8A1219),
    onAccentPrimary = Color(0xFFFFFFFF), onAccentSecondary = Color(0xFFFFFFFF), onAccentAlert = Color(0xFFFFFFFF),
    brandAccent = Color(0xFF0F62FE), textOnColor = Color(0xFFFFFFFF),
)

internal val CarbonDarkColors = BackbeeColors(
    bgPage = Color(0xFF161616), bgPanel = Color(0xFF212121), layer02 = Color(0xFF2E2E2E),
    bgInverse = Color(0xFFF4F4F4), textPrimary = Color(0xFFF4F4F4),
    textInverse = Color(0xFF161616), textMuted = Color(0xFFC6C6C6),
    borderColor = Color(0xFF393939), borderStrong = Color(0xFF8D8D8D),
    field = Color(0xFF2E2E2E), layerSelected = Color(0xFF393939),
    focus = Color(0xFFFFFFFF), textAccent = Color(0xFF78A9FF), textAccentSelected = Color(0xFFA6C8FF),
    textFunctional = Color(0xFF42BE65), textSecondary = Color(0xFFB8C1CC),
    textAlert = Color(0xFFFF8389),
    textDisabled = Color(0xFF6F6F6F), disabled = Color(0xFF393939),
    onInverseFunctional = Color(0xFF0E6027), onInverseAlert = Color(0xFFB81922),
    accentPrimary = Color(0xFF5C97FF), accentPrimaryActive = Color(0xFF78A9FF),
    accentSecondary = Color(0xFF42BE65), accentFunctional = Color(0xFF42BE65), accentInfo = Color(0xFF5C97FF),
    accentAlert = Color(0xFFFF6B73), accentAlertHover = Color(0xFFFF6B73), accentAlertActive = Color(0xFFFF8389),
    onAccentPrimary = Color(0xFF0A101D), onAccentSecondary = Color(0xFF0A101D), onAccentAlert = Color(0xFF0A101D),
    brandAccent = Color(0xFF5C97FF), textOnColor = Color(0xFF0A101D),
)

// 3. FJORD — Cool slate & oceanic teal
internal val FjordLightColors = BackbeeColors(
    bgPage = Color(0xFFEEF2F6), bgPanel = Color(0xFFF8FAFC), layer02 = Color(0xFFE2E8F0),
    bgInverse = Color(0xFF131C26), textPrimary = Color(0xFF131C26),
    textInverse = Color(0xFFEEF2F6), textMuted = Color(0xFF475569),
    borderColor = Color(0xFFCBD5E1), borderStrong = Color(0xFF64748B),
    field = Color(0xFFE2E8F0), layerSelected = Color(0xFFD8E1EC),
    focus = Color(0xFF0E5A7A), textAccent = Color(0xFF0E5A7A), textAccentSelected = Color(0xFF08445E),
    textFunctional = Color(0xFF166544), textSecondary = Color(0xFF3E526A),
    textAlert = Color(0xFFB4232C),
    textDisabled = Color(0xFF7E8C9F), disabled = Color(0xFFCBD5E1),
    onInverseFunctional = Color(0xFF5EEAD4), onInverseAlert = Color(0xFFFDA4AF),
    accentPrimary = Color(0xFF0E5A7A), accentPrimaryActive = Color(0xFF08445E),
    accentSecondary = Color(0xFF166544), accentFunctional = Color(0xFF166544), accentInfo = Color(0xFF0E5A7A),
    accentAlert = Color(0xFFB4232C), accentAlertHover = Color(0xFFB4232C), accentAlertActive = Color(0xFF881920),
    onAccentPrimary = Color(0xFFF8FAFC), onAccentSecondary = Color(0xFFF8FAFC), onAccentAlert = Color(0xFFF8FAFC),
    brandAccent = Color(0xFF0284C7), textOnColor = Color(0xFFF8FAFC),
)

internal val FjordDarkColors = BackbeeColors(
    bgPage = Color(0xFF0F1720), bgPanel = Color(0xFF18222F), layer02 = Color(0xFF223042),
    bgInverse = Color(0xFFEEF2F6), textPrimary = Color(0xFFE6EDF5),
    textInverse = Color(0xFF0F1720), textMuted = Color(0xFFA6B5C7),
    borderColor = Color(0xFF2C3B4E), borderStrong = Color(0xFF77889D),
    field = Color(0xFF223042), layerSelected = Color(0xFF2A3B50),
    focus = Color(0xFFE6EDF5), textAccent = Color(0xFF5CC4EA), textAccentSelected = Color(0xFF84D5F2),
    textFunctional = Color(0xFF56D4A0), textSecondary = Color(0xFFB3C3D6),
    textAlert = Color(0xFFFF8791),
    textDisabled = Color(0xFF64748B), disabled = Color(0xFF243242),
    onInverseFunctional = Color(0xFF115E3E), onInverseAlert = Color(0xFFB4232C),
    accentPrimary = Color(0xFF48B5DE), accentPrimaryActive = Color(0xFF6AC6E8),
    accentSecondary = Color(0xFF48C792), accentFunctional = Color(0xFF48C792), accentInfo = Color(0xFF48B5DE),
    accentAlert = Color(0xFFF86572), accentAlertHover = Color(0xFFF86572), accentAlertActive = Color(0xFFFF808B),
    onAccentPrimary = Color(0xFF09131C), onAccentSecondary = Color(0xFF09131C), onAccentAlert = Color(0xFF09131C),
    brandAccent = Color(0xFF48B5DE), textOnColor = Color(0xFF09131C),
)

// 4. PRESSROOM — Botanical paper & press green (with balanced sage secondary)
internal val PressroomLightColors = BackbeeColors(
    bgPage = Color(0xFFF4F1E8), bgPanel = Color(0xFFFBF9F3), layer02 = Color(0xFFEAE6DB),
    bgInverse = Color(0xFF1B2E1F), textPrimary = Color(0xFF1B2E1F),
    textInverse = Color(0xFFF4F1E8), textMuted = Color(0xFF526053),
    borderColor = Color(0xFFD1D2C8), borderStrong = Color(0xFF69776A),
    field = Color(0xFFEAE6DB), layerSelected = Color(0xFFE1DDD0),
    focus = Color(0xFF0F7A4A), textAccent = Color(0xFF0B5E38), textAccentSelected = Color(0xFF094A2C),
    textFunctional = Color(0xFF0B5E38), textSecondary = Color(0xFF4A594B),
    textAlert = Color(0xFFB8301E),
    textDisabled = Color(0xFF889084), disabled = Color(0xFFD3D4CA),
    onInverseFunctional = Color(0xFF45C182), onInverseAlert = Color(0xFFF78D7C),
    accentPrimary = Color(0xFF0F7A4A), accentPrimaryActive = Color(0xFF0B5E38),
    accentSecondary = Color(0xFF0F7A4A), accentFunctional = Color(0xFF0F7A4A), accentInfo = Color(0xFF0F7A4A),
    accentAlert = Color(0xFFB8301E), accentAlertHover = Color(0xFFB8301E), accentAlertActive = Color(0xFF8F2416),
    onAccentPrimary = Color(0xFFF4F1E8), onAccentSecondary = Color(0xFFF4F1E8), onAccentAlert = Color(0xFFF4F1E8),
    brandAccent = Color(0xFF2FA96C), textOnColor = Color(0xFFF4F1E8),
)

internal val PressroomDarkColors = BackbeeColors(
    bgPage = Color(0xFF101A13), bgPanel = Color(0xFF1B2E1F), layer02 = Color(0xFF283B2C),
    bgInverse = Color(0xFFF4F1E8), textPrimary = Color(0xFFF4F1E8),
    textInverse = Color(0xFF101A13), textMuted = Color(0xFFB8C2B8),
    borderColor = Color(0xFF344437), borderStrong = Color(0xFF849185),
    field = Color(0xFF283B2C), layerSelected = Color(0xFF334737),
    focus = Color(0xFFF4F1E8), textAccent = Color(0xFF4BD18D), textAccentSelected = Color(0xFF74DFAA),
    textFunctional = Color(0xFF4BD18D), textSecondary = Color(0xFFC4CFC4),
    textAlert = Color(0xFFF78D7C),
    textDisabled = Color(0xFF727D73), disabled = Color(0xFF2C3A2E),
    onInverseFunctional = Color(0xFF0B5E38), onInverseAlert = Color(0xFFB8301E),
    accentPrimary = Color(0xFF2FA96C), accentPrimaryActive = Color(0xFF45C182),
    accentSecondary = Color(0xFF2FA96C), accentFunctional = Color(0xFF2FA96C), accentInfo = Color(0xFF2FA96C),
    accentAlert = Color(0xFFE8543E), accentAlertHover = Color(0xFFE8543E), accentAlertActive = Color(0xFFF26B57),
    onAccentPrimary = Color(0xFF06170E), onAccentSecondary = Color(0xFF06170E), onAccentAlert = Color(0xFF06170E),
    brandAccent = Color(0xFF45C182), textOnColor = Color(0xFF06170E),
)

internal fun BackbeePalette.colors(darkTheme: Boolean): BackbeeColors = when (this) {
    BackbeePalette.PARCHMENT -> if (darkTheme) ParchmentDarkColors else ParchmentLightColors
    BackbeePalette.CARBON -> if (darkTheme) CarbonDarkColors else CarbonLightColors
    BackbeePalette.FJORD -> if (darkTheme) FjordDarkColors else FjordLightColors
    BackbeePalette.PRESSROOM -> if (darkTheme) PressroomDarkColors else PressroomLightColors
}

internal val LightColors: BackbeeColors = ParchmentLightColors
internal val DarkColors: BackbeeColors = ParchmentDarkColors
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
fun BackbeeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    palette: BackbeePalette = BackbeePalette.DEFAULT,
    content: @Composable () -> Unit,
) {
    val colors = palette.colors(darkTheme)
    val inverseColors = palette.colors(!darkTheme)
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
        inversePrimary = inverseColors.textAccent,
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
