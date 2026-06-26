package com.gamelaunch.frontend.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ── Color palette ──────────────────────────────────────────────────────────
val NavyBg       = Color(0xFF06091A)
val NavySurface  = Color(0xFF0D1330)
val NavyCard     = Color(0xFF172044)
val ElectricBlue = Color(0xFF4D7FFF)
val NeonPurple   = Color(0xFF9B5FFF)
val CyanAccent   = Color(0xFF00CFFF)
val IceWhite     = Color(0xFFE8EDF8)
val SteelGray    = Color(0xFF8899C0)
val NavyBorder   = Color(0xFF253060)

private val GameColorScheme = darkColorScheme(
    primary              = ElectricBlue,
    onPrimary            = Color.White,
    primaryContainer     = Color(0xFF1A2F5C),
    onPrimaryContainer   = Color(0xFFA8C4FF),
    secondary            = NeonPurple,
    onSecondary          = Color.White,
    secondaryContainer   = Color(0xFF2D1458),
    onSecondaryContainer = Color(0xFFD4AAFF),
    tertiary             = CyanAccent,
    onTertiary           = Color(0xFF003040),
    tertiaryContainer    = Color(0xFF003850),
    onTertiaryContainer  = Color(0xFF99EAFF),
    error                = Color(0xFFFF5060),
    onError              = Color.White,
    background           = NavyBg,
    onBackground         = IceWhite,
    surface              = NavySurface,
    onSurface            = IceWhite,
    surfaceVariant       = NavyCard,
    onSurfaceVariant     = SteelGray,
    outline              = NavyBorder,
    outlineVariant       = Color(0xFF182045),
    surfaceTint          = ElectricBlue,
    scrim                = Color(0xCC000000)
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
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GameColorScheme,
        typography  = GameTypography,
        content     = content
    )
}
