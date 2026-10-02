package com.gamelaunch.frontend.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ── Google Material 3 Color Tokens ─────────────────────────────────────────
val GoogleBlue       = Color(0xFF1A73E8)
val GoogleBlueDark   = Color(0xFFA8C7FF)
val GoogleNavyBg     = Color(0xFF111318)
val GoogleNavySurface= Color(0xFF191C20)
val GoogleNavyCard   = Color(0xFF232830)
val ElectricBlue     = Color(0xFF4C8DF6)
val NeonPurple       = Color(0xFFA17CFF)
val CyanAccent       = Color(0xFF5CD8FF)
val IceWhite         = Color(0xFFE2E2E9)
val SteelGray        = Color(0xFF8E9099)
val NavyBorder       = Color(0xFF383A42)

val NavyBg          = GoogleNavyBg
val NavySurface     = GoogleNavySurface
val NavyCard        = GoogleNavyCard

val GameColorScheme = darkColorScheme(
    primary              = GoogleBlueDark,
    onPrimary            = Color(0xFF003062),
    primaryContainer     = Color(0xFF004689),
    onPrimaryContainer   = Color(0xFFD6E3FF),
    secondary            = Color(0xFFC0C6DC),
    onSecondary          = Color(0xFF2A3042),
    secondaryContainer   = Color(0xFF404659),
    onSecondaryContainer = Color(0xFFDCE2F9),
    tertiary             = Color(0xFFDEBCDF),
    onTertiary           = Color(0xFF402843),
    tertiaryContainer    = Color(0xFF583E5B),
    onTertiaryContainer  = Color(0xFFFBD7FC),
    error                = Color(0xFFFFB4AB),
    onError              = Color(0xFF690005),
    background           = GoogleNavyBg,
    onBackground         = IceWhite,
    surface              = GoogleNavySurface,
    onSurface            = IceWhite,
    surfaceVariant       = GoogleNavyCard,
    onSurfaceVariant     = SteelGray,
    surfaceContainerLowest= Color(0xFF0C0E13),
    surfaceContainerLow  = Color(0xFF191C20),
    surfaceContainer     = Color(0xFF1F2328),
    surfaceContainerHigh = Color(0xFF2A2E35),
    surfaceContainerHighest= Color(0xFF353941),
    outline              = NavyBorder,
    outlineVariant       = Color(0xFF44474F),
    surfaceTint          = GoogleBlueDark,
    scrim                = Color(0xCC000000)
)

val GameLightColorScheme = lightColorScheme(
    primary              = GoogleBlue,
    onPrimary            = Color.White,
    primaryContainer     = Color(0xFFD6E3FF),
    onPrimaryContainer   = Color(0xFF001B3D),
    secondary            = Color(0xFF585E71),
    onSecondary          = Color.White,
    secondaryContainer   = Color(0xFFDCE2F9),
    onSecondaryContainer = Color(0xFF151B2C),
    tertiary             = Color(0xFF715573),
    onTertiary           = Color.White,
    tertiaryContainer    = Color(0xFFFBD7FC),
    onTertiaryContainer  = Color(0xFF2A132D),
    error                = Color(0xFFBA1A1A),
    onError              = Color.White,
    background           = Color(0xFFF8F9FF),
    onBackground         = Color(0xFF191C20),
    surface              = Color(0xFFF8F9FF),
    onSurface            = Color(0xFF191C20),
    surfaceVariant       = Color(0xFFE0E2EC),
    onSurfaceVariant     = Color(0xFF44474F),
    surfaceContainerLowest= Color(0xFFFFFFFF),
    surfaceContainerLow  = Color(0xFFF3F3FA),
    surfaceContainer     = Color(0xFFEDEEF5),
    surfaceContainerHigh = Color(0xFFE7E8F0),
    surfaceContainerHighest= Color(0xFFE1E2EA),
    outline              = Color(0xFF74777F),
    outlineVariant       = Color(0xFFC4C6D0),
    surfaceTint          = GoogleBlue,
    scrim                = Color(0x66000000)
)

private val GameTypography = Typography(
    headlineLarge  = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold,      fontSize = 28.sp, letterSpacing = (-0.3).sp),
    headlineSmall  = TextStyle(fontWeight = FontWeight.Bold,      fontSize = 24.sp),
    titleLarge     = TextStyle(fontWeight = FontWeight.Bold,      fontSize = 22.sp),
    titleMedium    = TextStyle(fontWeight = FontWeight.SemiBold,  fontSize = 16.sp, letterSpacing = 0.1.sp),
    titleSmall     = TextStyle(fontWeight = FontWeight.SemiBold,  fontSize = 14.sp),
    labelLarge     = TextStyle(fontWeight = FontWeight.SemiBold,  fontSize = 14.sp, letterSpacing = 0.3.sp),
    labelMedium    = TextStyle(fontWeight = FontWeight.Medium,    fontSize = 12.sp, letterSpacing = 0.3.sp),
    labelSmall     = TextStyle(fontWeight = FontWeight.Medium,    fontSize = 11.sp, letterSpacing = 0.4.sp),
    bodyLarge      = TextStyle(fontWeight = FontWeight.Normal,    fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium     = TextStyle(fontWeight = FontWeight.Normal,    fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall      = TextStyle(fontWeight = FontWeight.Normal,    fontSize = 12.sp, lineHeight = 16.sp)
)

@Composable
fun AppTheme(
    darkMode: Boolean = false,
    branding: BackgroundBranding = BackgroundBranding(),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalDarkMode provides darkMode,
        LocalBackgroundBranding provides branding
    ) {
        MaterialTheme(
            colorScheme = if (darkMode) GameColorScheme else GameLightColorScheme,
            typography  = GameTypography,
            content     = content
        )
    }
}

/**
 * Wrap a screen so its Material colours follow the user's light/dark choice. The app's root
 * MaterialTheme stays dark and most screens read [LocalDarkMode] for their own colours; screens
 * built largely from Material components (Settings, detail, scan, etc.) wrap their content in this
 * so TopAppBar / Card / OutlinedTextField / Text adapt automatically.
 */
@Composable
fun ThemedScreen(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (LocalDarkMode.current) GameColorScheme else GameLightColorScheme,
        typography  = GameTypography,
        content     = content
    )
}
