package com.gamelaunch.frontend.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * The handful of choices the theme editor exposes, turned back into a full [EorTheme]. Anything the
 * user didn't touch is carried over from [base] unchanged — so opening a built-in theme and saving
 * it as-is reproduces it exactly, hand-tuned tones, glows and all. Changed colours derive the rest
 * (Material tones from the accent, every dark surface from the background colour).
 */
data class ThemeDraft(
    val name: String,
    val accent: Color,
    val accent2: Color,
    val accent3: Color,
    /** Dark-mode background; the other dark surfaces are derived from it. */
    val background: Color,
    val tiles: CardColorScheme,
    /** Tile colour when [tiles] is MONOCHROME. */
    val tileColor: Color,
    /**
     * Colour glows: in dark mode for a plain theme; over the photo (both modes) for a theme with a
     * background image, where that photo setting is the one that decides whether glows show.
     */
    val glows: Boolean,
    val focusDark: Color,
    val focusLight: Color,
    /** Background photo settings; null = no photo. The image itself is handled by the editor. */
    val wallpaper: EorTheme.Wallpaper? = null
) {
    fun toTheme(base: EorTheme, id: String): EorTheme {
        val accentSame = accent == base.accent
        val accentsSame = accentSame && accent2 == base.accent2 && accent3 == base.accent3
        val surfaces = if (background == base.darkSurfaces.background) base.darkSurfaces else surfacesFrom(background, accent)
        val wasGlowing = base.darkGlows.isNotEmpty()
        return base.copy(
            id = id,
            name = name.trim(),
            accent = accent,
            accent2 = accent2,
            accent3 = accent3,
            dark = if (accentSame) base.dark else darkTones(accent),
            light = if (accentSame) base.light else lightTones(accent),
            darkSurfaces = surfaces,
            darkGlows = when {
                // With a photo the glows are switched by wallpaper.glows; keep a set to draw.
                wallpaper != null -> if (accentsSame && wasGlowing) base.darkGlows else listOf(accent, accent2, accent3)
                !glows -> emptyList()
                accentsSame && wasGlowing -> base.darkGlows
                else -> listOf(accent, accent2, accent3)
            },
            wallpaper = wallpaper?.copy(glows = glows),
            lightGlows = if (accentsSame) base.lightGlows else listOf(accent, accent2, accent3, accent2),
            cards = CardColorConfig(tiles, tileColor),
            focusDark = focusDark,
            focusLight = focusLight
        )
    }

    companion object {
        fun from(theme: EorTheme) = ThemeDraft(
            name = theme.name,
            accent = theme.accent,
            accent2 = theme.accent2,
            accent3 = theme.accent3,
            background = theme.darkSurfaces.background,
            tiles = theme.cards.scheme,
            tileColor = theme.cards.monochromeSeed,
            glows = theme.wallpaper?.glows ?: theme.darkGlows.isNotEmpty(),
            focusDark = theme.focusDark,
            focusLight = theme.focusLight,
            wallpaper = theme.wallpaper
        )

        /** Material-ish primary tones from a single accent, for themes that don't spell them out. */
        fun darkTones(a: Color) = EorTheme.Tones(
            primary = lerp(a, Color.White, 0.45f),
            onPrimary = lerp(a, Color.Black, 0.75f),
            primaryContainer = lerp(a, Color.Black, 0.55f),
            onPrimaryContainer = lerp(a, Color.White, 0.8f)
        )

        fun lightTones(a: Color) = EorTheme.Tones(
            primary = lerp(a, Color.Black, 0.3f),
            onPrimary = Color.White,
            primaryContainer = lerp(a, Color.White, 0.8f),
            onPrimaryContainer = lerp(a, Color.Black, 0.8f)
        )

        /**
         * Every dark surface from one background colour: raised surfaces step toward white (the
         * same spacing as the original navy set), chips pick up a hint of the accent. Pure black
         * stays pure black underneath, so an OLED background still works.
         */
        fun surfacesFrom(bg: Color, accent: Color): EorTheme.Surfaces {
            fun up(f: Float) = lerp(bg, Color.White, f)
            val card = up(0.09f)
            val border = up(0.17f)
            return EorTheme.Surfaces(
                background = bg,
                surface = up(0.04f),
                card = card,
                border = border,
                containerLowest = lerp(bg, Color.Black, 0.3f),
                containerLow = up(0.04f),
                container = up(0.065f),
                containerHigh = up(0.11f),
                containerHighest = up(0.155f),
                outlineVariant = up(0.22f),
                chip = lerp(card, accent, 0.12f),
                chipBorder = lerp(border, accent, 0.18f)
            )
        }
    }
}
