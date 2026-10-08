package com.gamelaunch.frontend.data.network.dto

import com.google.gson.JsonElement

// The theme.json inside an .eortheme file. All nullable: Gson fills missing fields with null, and
// ThemeFile validates and fills defaults. Lives in dto/ so the R8 keep rule covers it.

data class ThemeFileDto(
    val format: String? = null,
    val schemaVersion: Int? = null,
    val name: String? = null,
    val author: String? = null,
    val accent: String? = null,
    val accent2: String? = null,
    val accent3: String? = null,
    val dark: ThemeTonesDto? = null,
    val light: ThemeTonesDto? = null,
    /** "navy", "oled", or an object of surface colours. */
    val darkBackground: JsonElement? = null,
    val darkGlows: List<String>? = null,
    val lightGlows: List<String>? = null,
    /** "rainbow", "grey", "accent", or a "#RRGGBB" tile colour. */
    val tiles: String? = null,
    val focus: ThemeFocusDto? = null
)

data class ThemeTonesDto(
    val primary: String? = null,
    val onPrimary: String? = null,
    val primaryContainer: String? = null,
    val onPrimaryContainer: String? = null
)

data class ThemeSurfacesDto(
    val background: String? = null,
    val surface: String? = null,
    val card: String? = null,
    val border: String? = null,
    val containerLowest: String? = null,
    val containerLow: String? = null,
    val container: String? = null,
    val containerHigh: String? = null,
    val containerHighest: String? = null,
    val outlineVariant: String? = null,
    val chip: String? = null,
    val chipBorder: String? = null
)

data class ThemeFocusDto(
    val dark: String? = null,
    val light: String? = null
)
