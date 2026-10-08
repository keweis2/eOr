package com.gamelaunch.frontend.data.network.dto

// themes/index.json in the eOr repo. Nullable throughout; ThemeGalleryRepository validates.
// Lives in dto/ so the R8 keep rule covers it.

data class ThemeGalleryDto(
    val schemaVersion: Int? = null,
    val themes: List<ThemeGalleryEntryDto>? = null
)

data class ThemeGalleryEntryDto(
    val id: String? = null,
    val name: String? = null,
    val author: String? = null,
    /** File name next to index.json, e.g. "synthwave.eortheme". */
    val file: String? = null,
    /** Swatch colours, so the list renders without downloading every theme. */
    val accent: String? = null,
    val accent2: String? = null,
    val background: String? = null,
    /** Optional preview image next to index.json, "<id>.jpg" (made by themes/make_previews.py). */
    val preview: String? = null
)
