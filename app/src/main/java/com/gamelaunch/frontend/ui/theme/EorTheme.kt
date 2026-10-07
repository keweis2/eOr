package com.gamelaunch.frontend.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * An accent theme: the brand colours eOr paints highlights, focus rings, buttons and Material's
 * primary role with, plus the dark-mode surfaces. Light/dark mode stays a separate choice — every
 * theme defines both.
 *
 * [EorThemes.Default] reproduces the original hard-coded palette exactly, so existing installs
 * look unchanged until the user picks something else.
 */
data class EorTheme(
    val id: String,
    val name: String,
    /** Main accent — focus borders, highlights, links (was the ElectricBlue constant). */
    val accent: Color,
    /** Gradient partner for primary buttons (was NeonPurple). */
    val accent2: Color,
    /** Third accent for small highlights (was CyanAccent). */
    val accent3: Color,
    val dark: Tones,
    val light: Tones,
    /** Dark-mode backgrounds and surfaces. */
    val darkSurfaces: Surfaces = Surfaces.Navy,
    /**
     * Soft background glows (AmbientBackground), in draw order; each slot has a fixed position and
     * strength. Fewer colours = fewer glows; empty = a flat background (OLED).
     */
    val darkGlows: List<Color> = listOf(accent, accent2, accent3),
    val lightGlows: List<Color> = listOf(accent, accent2, accent3, accent2),
    /** Home-tile colours (what used to be the separate "Card colors" setting). */
    val cards: CardColorConfig = CardColorConfig(CardColorScheme.MONOCHROME, accent)
) {
    /** Material primary-role tones for one brightness. */
    data class Tones(
        val primary: Color,
        val onPrimary: Color,
        val primaryContainer: Color,
        val onPrimaryContainer: Color
    )

    data class Surfaces(
        val background: Color,
        val surface: Color,
        val card: Color,
        val border: Color,
        val containerLowest: Color,
        val containerLow: Color,
        val container: Color,
        val containerHigh: Color,
        val containerHighest: Color,
        val outlineVariant: Color,
        /** Unselected round "glass" chips (e.g. the home settings gear) and their border. */
        val chip: Color = Color(0xFF1A2448),
        val chipBorder: Color = Color(0xFF2E3B68)
    ) {
        companion object {
            /** The original navy dark surfaces. */
            val Navy = Surfaces(
                background = Color(0xFF111318),
                surface = Color(0xFF191C20),
                card = Color(0xFF232830),
                border = Color(0xFF383A42),
                containerLowest = Color(0xFF0C0E13),
                containerLow = Color(0xFF191C20),
                container = Color(0xFF1F2328),
                containerHigh = Color(0xFF2A2E35),
                containerHighest = Color(0xFF353941),
                outlineVariant = Color(0xFF44474F)
            )

            /** True black backgrounds for OLED screens; raised surfaces stay just off-black. */
            val Oled = Surfaces(
                background = Color(0xFF000000),
                surface = Color(0xFF000000),
                card = Color(0xFF111214),
                border = Color(0xFF2A2B2F),
                containerLowest = Color(0xFF000000),
                containerLow = Color(0xFF0A0A0C),
                container = Color(0xFF111214),
                containerHigh = Color(0xFF1A1B1E),
                containerHighest = Color(0xFF242529),
                outlineVariant = Color(0xFF34363B),
                chip = Color(0xFF111214),
                chipBorder = Color(0xFF2A2B2F)
            )
        }
    }
}

object EorThemes {

    private val BlueDark = EorTheme.Tones(
        primary = Color(0xFFA8C7FF), onPrimary = Color(0xFF003062),
        primaryContainer = Color(0xFF004689), onPrimaryContainer = Color(0xFFD6E3FF)
    )
    private val BlueLight = EorTheme.Tones(
        primary = Color(0xFF1A73E8), onPrimary = Color.White,
        primaryContainer = Color(0xFFD6E3FF), onPrimaryContainer = Color(0xFF001B3D)
    )

    /** The original eOr palette — identical to the pre-theme hard-coded values. */
    val Default = EorTheme(
        id = "blue", name = "Default",
        accent = Color(0xFF4C8DF6), accent2 = Color(0xFFA17CFF), accent3 = Color(0xFF5CD8FF),
        dark = BlueDark, light = BlueLight,
        // The original hand-tuned glow colours.
        darkGlows = listOf(Color(0xFF3D6FFF), Color(0xFF7B4FFF), Color(0xFF00CFFF)),
        lightGlows = listOf(Color(0xFF6FC4FF), Color(0xFFB58CFF), Color(0xFF59E0B8), Color(0xFFFF9CC0)),
        cards = CardColorConfig(CardColorScheme.RAINBOW)
    )

    // Pure black in dark mode means no glows either; light mode is the default look.
    val Oled = Default.copy(
        id = "oled", name = "OLED Black",
        darkSurfaces = EorTheme.Surfaces.Oled,
        darkGlows = emptyList()
    )

    /** Neutral greys throughout — the old "B & W" card colours as a whole theme. */
    val BlackAndWhite = EorTheme(
        id = "bw", name = "Black & White",
        accent = Color(0xFF9AA0AC), accent2 = Color(0xFF5F6470), accent3 = Color(0xFFC5CAD3),
        dark = EorTheme.Tones(Color(0xFFD5D8DE), Color(0xFF2B2F36), Color(0xFF41454D), Color(0xFFE8EAEF)),
        light = EorTheme.Tones(Color(0xFF4A4F59), Color.White, Color(0xFFE1E3E8), Color(0xFF1B1E24)),
        darkGlows = listOf(Color(0xFF9AA0AC), Color(0xFF5F6470), Color(0xFFC5CAD3)),
        lightGlows = listOf(Color(0xFFC5CAD3), Color(0xFF9AA0AC), Color(0xFFB8BDC7), Color(0xFF868D9B)),
        cards = CardColorConfig(CardColorScheme.BLACK_WHITE)
    )

    /** Blue tiles — the old Monochrome card colour's default seed as a theme. */
    val Ocean = EorTheme(
        id = "ocean", name = "Ocean",
        accent = Color(0xFF3E8BFF), accent2 = Color(0xFF00B4D8), accent3 = Color(0xFF8EC5FF),
        dark = BlueDark, light = BlueLight,
        cards = CardColorConfig(CardColorScheme.MONOCHROME, Color(0xFF3E7BFF))
    )

    val Violet = EorTheme(
        id = "violet", name = "Violet",
        accent = Color(0xFF9C6BFF), accent2 = Color(0xFFFF6BCB), accent3 = Color(0xFFC9A8FF),
        dark = EorTheme.Tones(Color(0xFFD3BBFF), Color(0xFF3B1D71), Color(0xFF523789), Color(0xFFEBDDFF)),
        light = EorTheme.Tones(Color(0xFF6B4BB5), Color.White, Color(0xFFEBDDFF), Color(0xFF250059)),
        cards = CardColorConfig(CardColorScheme.MONOCHROME, Color(0xFF7C4DFF))
    )

    val Emerald = EorTheme(
        id = "emerald", name = "Emerald",
        accent = Color(0xFF2ECC8F), accent2 = Color(0xFF29B6F6), accent3 = Color(0xFF9BE7C4),
        dark = EorTheme.Tones(Color(0xFF7DDBAE), Color(0xFF003824), Color(0xFF005236), Color(0xFF99F7C9)),
        light = EorTheme.Tones(Color(0xFF006C48), Color.White, Color(0xFF99F7C9), Color(0xFF002114)),
        cards = CardColorConfig(CardColorScheme.MONOCHROME, Color(0xFF2ECC71))
    )

    val Sunset = EorTheme(
        id = "sunset", name = "Sunset",
        accent = Color(0xFFFF8A3D), accent2 = Color(0xFFFF4F8B), accent3 = Color(0xFFFFC46B),
        dark = EorTheme.Tones(Color(0xFFFFB68C), Color(0xFF532200), Color(0xFF753400), Color(0xFFFFDBC9)),
        light = EorTheme.Tones(Color(0xFF9A4600), Color.White, Color(0xFFFFDBC9), Color(0xFF321200)),
        cards = CardColorConfig(CardColorScheme.MONOCHROME, Color(0xFFFF9F1C))
    )

    val Rose = EorTheme(
        id = "rose", name = "Rose",
        accent = Color(0xFFFF5C8A), accent2 = Color(0xFFB07BFF), accent3 = Color(0xFFFFA3C0),
        dark = EorTheme.Tones(Color(0xFFFFB1C3), Color(0xFF65002E), Color(0xFF8E0D45), Color(0xFFFFD9E0)),
        light = EorTheme.Tones(Color(0xFFB02A5B), Color.White, Color(0xFFFFD9E0), Color(0xFF3F0019)),
        cards = CardColorConfig(CardColorScheme.MONOCHROME, Color(0xFFFF4D8D))
    )

    val All: List<EorTheme> = listOf(Default, Oled, BlackAndWhite, Ocean, Violet, Emerald, Sunset, Rose)

    fun byId(id: String?): EorTheme = All.firstOrNull { it.id == id } ?: Default

    /**
     * The theme closest to a pre-theme "Card colors" choice, so upgrading keeps someone's tiles
     * looking the same: Rainbow → Default, B & W → Black & White, Monochrome → the coloured theme
     * nearest in hue to the chosen swatch.
     */
    fun forLegacyCardColors(scheme: CardColorScheme, monoSeedArgb: Int): EorTheme = when (scheme) {
        CardColorScheme.RAINBOW -> Default
        CardColorScheme.BLACK_WHITE -> BlackAndWhite
        CardColorScheme.MONOCHROME -> {
            val hue = hueOf(monoSeedArgb)
            if (hue == null) BlackAndWhite
            else listOf(Ocean, Violet, Emerald, Sunset, Rose).minBy { hueDistance(hue, hueOf(it.cards.monochromeSeed.toArgb())!!) }
        }
    }

    /** Hue in degrees, or null for an (almost) grey colour. */
    internal fun hueOf(argb: Int): Float? {
        val r = (argb shr 16 and 0xFF) / 255f
        val g = (argb shr 8 and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val d = max - min
        if (max == 0f || d / max < 0.05f) return null
        val h = when (max) {
            r -> 60f * (((g - b) / d) % 6f)
            g -> 60f * ((b - r) / d + 2f)
            else -> 60f * ((r - g) / d + 4f)
        }
        return (h + 360f) % 360f
    }

    private fun hueDistance(a: Float, b: Float): Float {
        val d = kotlin.math.abs(a - b) % 360f
        return if (d > 180f) 360f - d else d
    }
}

/** The active accent theme, provided at the root by [AppTheme]. */
val LocalEorTheme = staticCompositionLocalOf { EorThemes.Default }
