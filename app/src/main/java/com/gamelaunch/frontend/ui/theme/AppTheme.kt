package com.gamelaunch.frontend.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ── Palette ─────────────────────────────────────────────────────────────────
// Neutral text greys are fixed; the brand accents and dark surfaces come from the active
// [EorTheme] (see EorThemes.Default for the original values). They're composable getters so the
// many existing call sites keep reading `ElectricBlue`, `NavyBg`… and pick up the theme for free.
val IceWhite         = Color(0xFFE2E2E9)
val SteelGray        = Color(0xFF8E9099)

val ElectricBlue: Color @Composable @ReadOnlyComposable get() = LocalEorTheme.current.accent
val NeonPurple: Color   @Composable @ReadOnlyComposable get() = LocalEorTheme.current.accent2
val CyanAccent: Color   @Composable @ReadOnlyComposable get() = LocalEorTheme.current.accent3
val NavyBg: Color       @Composable @ReadOnlyComposable get() = LocalEorTheme.current.darkSurfaces.background
val NavySurface: Color  @Composable @ReadOnlyComposable get() = LocalEorTheme.current.darkSurfaces.surface
val NavyCard: Color     @Composable @ReadOnlyComposable get() = LocalEorTheme.current.darkSurfaces.card
val NavyBorder: Color   @Composable @ReadOnlyComposable get() = LocalEorTheme.current.darkSurfaces.border

/** Outline colour for the focused / selected item, from the theme and light/dark mode. */
val FocusRing: Color @Composable @ReadOnlyComposable get() =
    if (LocalDarkMode.current) LocalEorTheme.current.focusDark else LocalEorTheme.current.focusLight

/** Material dark scheme for [theme] — with the default theme, identical to the original. */
fun gameDarkColorScheme(theme: EorTheme): ColorScheme {
    val t = theme.dark
    val s = theme.darkSurfaces
    return darkColorScheme(
        primary              = t.primary,
        onPrimary            = t.onPrimary,
        primaryContainer     = t.primaryContainer,
        onPrimaryContainer   = t.onPrimaryContainer,
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
        background           = s.background,
        onBackground         = IceWhite,
        surface              = s.surface,
        onSurface            = IceWhite,
        surfaceVariant       = s.card,
        onSurfaceVariant     = SteelGray,
        surfaceContainerLowest = s.containerLowest,
        surfaceContainerLow  = s.containerLow,
        surfaceContainer     = s.container,
        surfaceContainerHigh = s.containerHigh,
        surfaceContainerHighest = s.containerHighest,
        outline              = s.border,
        outlineVariant       = s.outlineVariant,
        surfaceTint          = t.primary,
        scrim                = Color(0xCC000000)
    )
}

/** Material light scheme for [theme] — with the default theme, identical to the original. */
fun gameLightColorScheme(theme: EorTheme): ColorScheme {
    val t = theme.light
    return lightColorScheme(
        primary              = t.primary,
        onPrimary            = t.onPrimary,
        primaryContainer     = t.primaryContainer,
        onPrimaryContainer   = t.onPrimaryContainer,
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
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow  = Color(0xFFF3F3FA),
        surfaceContainer     = Color(0xFFEDEEF5),
        surfaceContainerHigh = Color(0xFFE7E8F0),
        surfaceContainerHighest = Color(0xFFE1E2EA),
        outline              = Color(0xFF74777F),
        outlineVariant       = Color(0xFFC4C6D0),
        surfaceTint          = t.primary,
        scrim                = Color(0x66000000)
    )
}

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
    theme: EorTheme = EorThemes.Default,
    wallpaper: ImageBitmap? = null,
    content: @Composable () -> Unit
) {
    val colorScheme = remember(theme, darkMode) {
        if (darkMode) gameDarkColorScheme(theme) else gameLightColorScheme(theme)
    }
    CompositionLocalProvider(
        LocalDarkMode provides darkMode,
        LocalEorTheme provides theme,
        LocalThemeWallpaper provides wallpaper,
        LocalCardColorScheme provides theme.cards
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
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
    val dark = LocalDarkMode.current
    val theme = LocalEorTheme.current
    MaterialTheme(
        colorScheme = remember(theme, dark) { if (dark) gameDarkColorScheme(theme) else gameLightColorScheme(theme) },
        typography  = GameTypography,
        content     = content
    )
}
