package com.gamelaunch.frontend

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.gamelaunch.frontend.ui.theme.CardColorConfig
import com.gamelaunch.frontend.ui.theme.CardColorScheme
import com.gamelaunch.frontend.ui.theme.EorThemes
import com.gamelaunch.frontend.ui.theme.gameDarkColorScheme
import com.gamelaunch.frontend.ui.theme.gameLightColorScheme
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The default accent theme must look exactly like eOr did before themes existed. The expected
 * values below are the literal colours from the pre-theme AppTheme.kt (GameColorScheme /
 * GameLightColorScheme), so any drift in the default shows up here.
 */
class EorThemeTest {

    private fun ColorScheme.byName(): Map<String, Color> = mapOf(
        "primary" to primary, "onPrimary" to onPrimary, "primaryContainer" to primaryContainer,
        "onPrimaryContainer" to onPrimaryContainer, "secondary" to secondary, "onSecondary" to onSecondary,
        "secondaryContainer" to secondaryContainer, "onSecondaryContainer" to onSecondaryContainer,
        "tertiary" to tertiary, "onTertiary" to onTertiary, "tertiaryContainer" to tertiaryContainer,
        "onTertiaryContainer" to onTertiaryContainer, "error" to error, "onError" to onError,
        "background" to background, "onBackground" to onBackground, "surface" to surface,
        "onSurface" to onSurface, "surfaceVariant" to surfaceVariant, "onSurfaceVariant" to onSurfaceVariant,
        "surfaceContainerLowest" to surfaceContainerLowest, "surfaceContainerLow" to surfaceContainerLow,
        "surfaceContainer" to surfaceContainer, "surfaceContainerHigh" to surfaceContainerHigh,
        "surfaceContainerHighest" to surfaceContainerHighest, "outline" to outline,
        "outlineVariant" to outlineVariant, "surfaceTint" to surfaceTint, "scrim" to scrim
    )

    @Test fun `default dark scheme is unchanged from the pre-theme palette`() {
        val expected = mapOf(
            "primary" to Color(0xFFA8C7FF),
            "onPrimary" to Color(0xFF003062),
            "primaryContainer" to Color(0xFF004689),
            "onPrimaryContainer" to Color(0xFFD6E3FF),
            "secondary" to Color(0xFFC0C6DC),
            "onSecondary" to Color(0xFF2A3042),
            "secondaryContainer" to Color(0xFF404659),
            "onSecondaryContainer" to Color(0xFFDCE2F9),
            "tertiary" to Color(0xFFDEBCDF),
            "onTertiary" to Color(0xFF402843),
            "tertiaryContainer" to Color(0xFF583E5B),
            "onTertiaryContainer" to Color(0xFFFBD7FC),
            "error" to Color(0xFFFFB4AB),
            "onError" to Color(0xFF690005),
            "background" to Color(0xFF111318),
            "onBackground" to Color(0xFFE2E2E9),
            "surface" to Color(0xFF191C20),
            "onSurface" to Color(0xFFE2E2E9),
            "surfaceVariant" to Color(0xFF232830),
            "onSurfaceVariant" to Color(0xFF8E9099),
            "surfaceContainerLowest" to Color(0xFF0C0E13),
            "surfaceContainerLow" to Color(0xFF191C20),
            "surfaceContainer" to Color(0xFF1F2328),
            "surfaceContainerHigh" to Color(0xFF2A2E35),
            "surfaceContainerHighest" to Color(0xFF353941),
            "outline" to Color(0xFF383A42),
            "outlineVariant" to Color(0xFF44474F),
            "surfaceTint" to Color(0xFFA8C7FF),
            "scrim" to Color(0xCC000000),
        )
        val actual = gameDarkColorScheme(EorThemes.Default).byName()
        expected.forEach { (k, v) -> assertEquals(k, v, actual[k]) }
    }

    @Test fun `default light scheme is unchanged from the pre-theme palette`() {
        val expected = mapOf(
            "primary" to Color(0xFF1A73E8),
            "onPrimary" to Color.White,
            "primaryContainer" to Color(0xFFD6E3FF),
            "onPrimaryContainer" to Color(0xFF001B3D),
            "secondary" to Color(0xFF585E71),
            "onSecondary" to Color.White,
            "secondaryContainer" to Color(0xFFDCE2F9),
            "onSecondaryContainer" to Color(0xFF151B2C),
            "tertiary" to Color(0xFF715573),
            "onTertiary" to Color.White,
            "tertiaryContainer" to Color(0xFFFBD7FC),
            "onTertiaryContainer" to Color(0xFF2A132D),
            "error" to Color(0xFFBA1A1A),
            "onError" to Color.White,
            "background" to Color(0xFFF8F9FF),
            "onBackground" to Color(0xFF191C20),
            "surface" to Color(0xFFF8F9FF),
            "onSurface" to Color(0xFF191C20),
            "surfaceVariant" to Color(0xFFE0E2EC),
            "onSurfaceVariant" to Color(0xFF44474F),
            "surfaceContainerLowest" to Color(0xFFFFFFFF),
            "surfaceContainerLow" to Color(0xFFF3F3FA),
            "surfaceContainer" to Color(0xFFEDEEF5),
            "surfaceContainerHigh" to Color(0xFFE7E8F0),
            "surfaceContainerHighest" to Color(0xFFE1E2EA),
            "outline" to Color(0xFF74777F),
            "outlineVariant" to Color(0xFFC4C6D0),
            "surfaceTint" to Color(0xFF1A73E8),
            "scrim" to Color(0x66000000),
        )
        val actual = gameLightColorScheme(EorThemes.Default).byName()
        expected.forEach { (k, v) -> assertEquals(k, v, actual[k]) }
    }

    @Test fun `default accents are the original constants`() {
        assertEquals(Color(0xFF4C8DF6), EorThemes.Default.accent)     // ElectricBlue
        assertEquals(Color(0xFFA17CFF), EorThemes.Default.accent2)    // NeonPurple
        assertEquals(Color(0xFF5CD8FF), EorThemes.Default.accent3)    // CyanAccent
        assertEquals(Color(0xFF111318), EorThemes.Default.darkSurfaces.background) // NavyBg
        assertEquals(Color(0xFF383A42), EorThemes.Default.darkSurfaces.border)     // NavyBorder
        assertEquals(Color(0xFF1A2448), EorThemes.Default.darkSurfaces.chip)       // glassChip fill
        assertEquals(Color(0xFF2E3B68), EorThemes.Default.darkSurfaces.chipBorder) // glassChip border
    }

    @Test fun `default background glows are the original hand-tuned colours, OLED has none`() {
        assertEquals(listOf(Color(0xFF3D6FFF), Color(0xFF7B4FFF), Color(0xFF00CFFF)), EorThemes.Default.darkGlows)
        assertEquals(listOf(Color(0xFF6FC4FF), Color(0xFFB58CFF), Color(0xFF59E0B8), Color(0xFFFF9CC0)), EorThemes.Default.lightGlows)
        assertEquals(emptyList<Color>(), EorThemes.Oled.darkGlows)
    }

    @Test fun `old Card colors choices map to the closest theme`() {
        assertEquals(EorThemes.Default, EorThemes.forLegacyCardColors(CardColorScheme.RAINBOW, 0))
        assertEquals(EorThemes.BlackAndWhite, EorThemes.forLegacyCardColors(CardColorScheme.BLACK_WHITE, 0))
        // The ten swatches the old Monochrome picker offered.
        val expected = mapOf(
            0xFF3E7BFF to EorThemes.Ocean,    // blue (old default)
            0xFF7C4DFF to EorThemes.Violet,   // violet
            0xFFB07BFF to EorThemes.Violet,   // lavender
            0xFFFF4D8D to EorThemes.Rose,     // pink
            0xFFFF5A5A to EorThemes.Rose,     // red
            0xFFFF9F1C to EorThemes.Sunset,   // orange
            0xFFFFC93C to EorThemes.Sunset,   // amber
            0xFF2ECC71 to EorThemes.Emerald,  // green
            0xFF17C3B2 to EorThemes.Emerald,  // teal
            0xFF00B4D8 to EorThemes.Ocean     // cyan
        )
        expected.forEach { (seed, theme) ->
            assertEquals("seed ${seed.toString(16)}", theme.id,
                EorThemes.forLegacyCardColors(CardColorScheme.MONOCHROME, seed.toInt()).id)
        }
        // A grey "monochrome" seed reads as black & white.
        assertEquals(EorThemes.BlackAndWhite, EorThemes.forLegacyCardColors(CardColorScheme.MONOCHROME, 0xFF808080.toInt()))
    }

    @Test fun `themes carry their tile style`() {
        assertEquals(CardColorConfig(CardColorScheme.RAINBOW), EorThemes.Default.cards)  // unchanged look
        assertEquals(CardColorScheme.RAINBOW, EorThemes.Oled.cards.scheme)
        assertEquals(CardColorScheme.BLACK_WHITE, EorThemes.BlackAndWhite.cards.scheme)
        listOf(EorThemes.Ocean, EorThemes.Violet, EorThemes.Emerald, EorThemes.Sunset, EorThemes.Rose)
            .forEach { assertEquals(it.id, CardColorScheme.MONOCHROME, it.cards.scheme) }
    }

    @Test fun `theme ids are unique and unknown ids fall back to the default`() {
        assertEquals(EorThemes.All.size, EorThemes.All.map { it.id }.toSet().size)
        assertEquals(EorThemes.Default, EorThemes.byId(""))
        assertEquals(EorThemes.Default, EorThemes.byId("removed-theme"))
        assertEquals(EorThemes.Oled, EorThemes.byId("oled"))
    }

    @Test fun `OLED dark mode is true black`() {
        val s = gameDarkColorScheme(EorThemes.Oled)
        assertEquals(Color.Black, s.background)
        assertEquals(Color.Black, s.surface)
        // Light mode is unaffected by the OLED surfaces.
        assertEquals(gameLightColorScheme(EorThemes.Default).byName(), gameLightColorScheme(EorThemes.Oled).byName())
    }
}
