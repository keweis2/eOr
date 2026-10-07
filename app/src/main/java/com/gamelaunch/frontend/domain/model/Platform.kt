package com.gamelaunch.frontend.domain.model

data class Platform(
    val id: String,
    val displayName: String,
    val scraperSystemId: Int,
    val extensions: List<String>,
    val folderNames: List<String>,
    val defaultEmulatorPackage: String? = null,
    val defaultCoreForRetroArch: String? = null,
    /**
     * Whether a file outside any recognised system folder may be claimed by extension alone.
     * Built-in systems keep the historical behaviour (true); systems added by the downloaded
     * catalog default to folder-only, because they bring generic extensions (.dat, .exe, .img…)
     * that would otherwise turn stray files into "games".
     */
    val detectByExtension: Boolean = true,
    // ── Presentation & artwork. Null = use the app's built-in tables (PlatformVisuals,
    // PlatformMetadata, scraper maps), so these only need setting for catalog-added systems. ──
    /** Key into the bundled console-icon pack (see PlatformVisuals). */
    val iconKey: String? = null,
    /** Short pill label, e.g. "WSC". */
    val label: String? = null,
    /** Controller silhouette family: nes, handheld, arcade or gamepad. */
    val padStyle: String? = null,
    /** Cover art width ÷ height. */
    val coverAspect: Float? = null,
    val releaseYear: Int? = null,
    val brand: String? = null,
    /** console, handheld, arcade, computer, mobile or other (used for sorting). */
    val kind: String? = null,
    /** thumbnails.libretro.com system folder, e.g. "Atari - Lynx". */
    val libretroThumbnails: String? = null,
    /** LaunchBox Games DB platform name. */
    val launchBoxPlatform: String? = null,
    /** ES-DE downloaded_media directory names to try, in order. */
    val esdeDirs: List<String> = emptyList()
)
