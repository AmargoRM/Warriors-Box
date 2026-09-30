package com.warriorsbox.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.warriorsbox.app.R

val WbBlack = Color(0xFF0D0D0D)
val WbCharcoal = Color(0xFF1E1E1E)
val WbGraphite = Color(0xFF2A2A2A)
val WbWhite = Color(0xFFFFFFFF)
val WbGold = Color(0xFFE0A95B)
val WbGoldDark = Color(0xFF9A6B2A)
val WbGreen = Color(0xFF5DBB7A)
val WbBlue = Color(0xFF4F8EDC)
val WbRed = Color(0xFFE0645B)

val Stencil = FontFamily(Font(R.font.black_ops_one))

private val DarkColors = darkColorScheme(
    primary = WbGold,
    onPrimary = WbBlack,
    primaryContainer = WbGoldDark,
    onPrimaryContainer = WbWhite,
    secondary = WbWhite,
    onSecondary = WbBlack,
    background = WbBlack,
    onBackground = WbWhite,
    surface = WbCharcoal,
    onSurface = WbWhite,
    surfaceVariant = WbGraphite,
    onSurfaceVariant = Color(0xFFD6D6D6),
    surfaceContainer = WbCharcoal,
    surfaceContainerHigh = WbGraphite,
    surfaceContainerHighest = Color(0xFF333333),
    outline = Color(0xFF5A5A5A),
    error = WbRed,
)

private val LightColors = lightColorScheme(
    primary = WbGoldDark,
    onPrimary = WbWhite,
    primaryContainer = Color(0xFFFFE2BC),
    onPrimaryContainer = WbBlack,
    secondary = WbBlack,
    onSecondary = WbWhite,
    background = Color(0xFFF4F2EF),
    onBackground = WbBlack,
    surface = WbWhite,
    onSurface = WbBlack,
    surfaceVariant = Color(0xFFECE8E3),
    onSurfaceVariant = Color(0xFF3A3A3A),
    outline = Color(0xFF8A8A8A),
    error = Color(0xFFB3261E),
)

private val base = Typography()

val WbTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = Stencil),
    displayMedium = base.displayMedium.copy(fontFamily = Stencil),
    displaySmall = base.displaySmall.copy(fontFamily = Stencil),
    headlineLarge = base.headlineLarge.copy(fontFamily = Stencil),
    headlineMedium = base.headlineMedium.copy(fontFamily = Stencil),
    headlineSmall = base.headlineSmall.copy(fontFamily = Stencil),
    titleLarge = base.titleLarge.copy(fontFamily = Stencil, letterSpacing = 0.5.sp),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Bold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.Bold),
)

val ButtonText = TextStyle(fontFamily = Stencil, fontSize = 22.sp, letterSpacing = 1.sp)

/** Tema oscuro por defecto (identidad Warriors Box); respeta el modo claro si el sistema lo pide. */
@Composable
fun WarriorsTheme(forceDark: Boolean = true, content: @Composable () -> Unit) {
    val dark = forceDark || isSystemInDarkTheme()
    val colors = if (dark) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, typography = WbTypography) {
        // Color de texto por defecto: nunca negro sobre fondo oscuro.
        CompositionLocalProvider(LocalContentColor provides colors.onBackground, content = content)
    }
}
