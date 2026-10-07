package com.gamelaunch.frontend.data.network.dto

// Wire format of catalog/catalog.json. Everything is nullable: Gson fills missing fields with null
// regardless of Kotlin types, and CatalogParser validates each entry before it reaches the app.
// Lives in dto/ so the existing proguard keep rule covers it under R8 full mode.

data class CatalogDto(
    val schemaVersion: Int? = null,
    val revision: Int? = null,
    /** Older apps skip the catalog entirely if their versionCode is below this. */
    val minAppVersionCode: Int? = null,
    val platforms: List<CatalogPlatformDto>? = null,
    val emulators: List<CatalogEmulatorDto>? = null
)

data class CatalogPlatformDto(
    val id: String? = null,
    val displayName: String? = null,
    val scraperSystemId: Int? = null,
    val extensions: List<String>? = null,
    val folderNames: List<String>? = null,
    val retroArchCore: String? = null,
    val defaultEmulator: String? = null,
    /** Auto-detect order: first installed package wins. */
    val emulators: List<String>? = null
)

data class CatalogEmulatorDto(
    val packageName: String? = null,
    val displayName: String? = null,
    val launch: CatalogLaunchDto? = null
)

data class CatalogLaunchDto(
    val activity: String? = null,
    /** "VIEW" or "MAIN". */
    val action: String? = null,
    val romExtraKey: String? = null,
    val mimeType: String? = null,
    /** "FILE" or "CONTENT". */
    val romUri: String? = null
)
