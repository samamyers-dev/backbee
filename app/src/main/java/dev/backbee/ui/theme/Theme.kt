package dev.backbee.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/**
 * Dark-first, high contrast, oversized.
 *
 * The screen matters for about three seconds at the start of a session and then
 * again on the couch. Both cases want the same thing: large type, few colours,
 * and enough contrast to read at arm's length on a car mount in daylight.
 */
private val Amber = Color(0xFFFFB74D)
private val AmberDeep = Color(0xFFE8891A)
private val Ink = Color(0xFF101012)
private val InkRaised = Color(0xFF1B1B1F)
private val InkHigh = Color(0xFF26262B)
private val Parchment = Color(0xFFF4EFE7)
private val Muted = Color(0xFFA0A0AA)
private val Danger = Color(0xFFE5534B)

private val DarkColors = darkColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF241600),
    primaryContainer = AmberDeep,
    onPrimaryContainer = Color(0xFF1A1000),
    secondary = Parchment,
    onSecondary = Ink,
    background = Ink,
    onBackground = Parchment,
    surface = InkRaised,
    onSurface = Parchment,
    surfaceVariant = InkHigh,
    onSurfaceVariant = Muted,
    outline = Color(0xFF3A3A42),
    error = Danger,
    onError = Color(0xFF2A0000),
)

private val LightColors = lightColorScheme(
    primary = AmberDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE0B2),
    onPrimaryContainer = Color(0xFF2B1700),
    secondary = Color(0xFF3D3D46),
    background = Color(0xFFFBF8F3),
    onBackground = Color(0xFF17171A),
    surface = Color.White,
    onSurface = Color(0xFF17171A),
    surfaceVariant = Color(0xFFEDE8E0),
    onSurfaceVariant = Color(0xFF55555E),
    outline = Color(0xFFC9C4BB),
    error = Color(0xFFB3261E),
)

/**
 * Type is a step or two larger than Material's defaults throughout. Everything
 * on these screens is read quickly, at a distance, often while moving.
 */
private val BackbeeTypography = Typography(
    displayLarge = TextStyle(fontSize = 44.sp, lineHeight = 50.sp, fontWeight = FontWeight.Bold),
    displayMedium = TextStyle(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold),
    headlineLarge = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold),
    headlineMedium = TextStyle(fontSize = 25.sp, lineHeight = 31.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 18.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    labelLarge = TextStyle(fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.Medium),
)

/** Touch targets sized for gloves, a dog leash, and a car mount at arm's length. */
object Dimens {
    val touchTarget = 64.dp
    val giantButton = 148.dp
    val gutter = 20.dp
    val gap = 12.dp
    val rowHeight = 76.dp
    val artworkLarge = 220.dp
    val artworkSmall = 56.dp
}

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

    MaterialTheme(
        colorScheme = colors,
        typography = BackbeeTypography,
        content = content,
    )
}
