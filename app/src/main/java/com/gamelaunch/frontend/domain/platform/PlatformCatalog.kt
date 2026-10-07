package com.gamelaunch.frontend.domain.platform

import com.gamelaunch.frontend.domain.model.KnownEmulator
import com.gamelaunch.frontend.domain.model.LaunchSpec
import com.gamelaunch.frontend.domain.model.Platform

/**
 * Everything eOr knows about systems and emulators: which files belong to which platform, which
 * emulators exist, which one auto-detect prefers, and how to boot a ROM in each.
 *
 * The app ships a built-in catalog ([PlatformDefinitions.BUILT_IN_CATALOG]); a newer one can be downloaded from the eOr repo so
 * new systems and emulator forks don't need an app release (see CatalogRepository).
 */
data class PlatformCatalog(
    /** 0 for the built-in catalog; the downloaded catalog's revision otherwise. */
    val revision: Int,
    val platforms: List<Platform>,
    val emulators: List<KnownEmulator>,
    val emulatorPriority: Map<String, List<String>>,
    val launchSpecs: Map<String, LaunchSpec>
) {
    val byId: Map<String, Platform> = platforms.associateBy { it.id }

    // Extension → platform; for ambiguous extensions (e.g. .iso, .bin) folder name takes priority
    val byExtension: Map<String, Platform> = buildMap {
        platforms.forEach { platform ->
            platform.extensions.forEach { ext -> putIfAbsent(ext.lowercase(), platform) }
        }
    }

    val byFolderName: Map<String, Platform> = buildMap {
        platforms.forEach { platform ->
            platform.folderNames.forEach { name -> put(name.lowercase(), platform) }
        }
    }

    val emulatorPackages: Set<String> = emulators.map { it.packageName }.toSet()

    /**
     * Layers a downloaded catalog over this one. The overlay can add and adjust, but never take
     * away: the ROM scanner deletes games whose platform it no longer recognises, so a bad remote
     * catalog must not be able to shrink a platform's extensions/folders or drop a platform.
     *
     * - Platforms: matched by id. Overlay wins for scalar fields; extensions and folder names are
     *   unioned. New platforms are appended (built-in order is kept, which some screens rely on).
     * - Emulators: matched by package; overlay display name wins; new ones appended.
     * - Priority: the overlay's order wins for a platform, with any built-in packages it omitted
     *   appended so auto-detect never loses a fallback.
     * - Launch specs: overlay replaces per package — this is how a broken recipe gets fixed.
     */
    fun mergedWith(overlay: PlatformCatalog): PlatformCatalog {
        val overlayPlatforms = overlay.byId
        val mergedPlatforms = platforms.map { base ->
            val o = overlayPlatforms[base.id] ?: return@map base
            base.copy(
                displayName = o.displayName,
                scraperSystemId = o.scraperSystemId,
                extensions = (base.extensions + o.extensions).distinctBy { it.lowercase() },
                folderNames = (base.folderNames + o.folderNames).distinct(),
                defaultEmulatorPackage = o.defaultEmulatorPackage ?: base.defaultEmulatorPackage,
                defaultCoreForRetroArch = o.defaultCoreForRetroArch ?: base.defaultCoreForRetroArch
            )
        } + overlay.platforms.filter { it.id !in byId }

        val overlayNames = overlay.emulators.associate { it.packageName to it.displayName }
        val mergedEmulators = emulators.map { e ->
            overlayNames[e.packageName]?.let { e.copy(displayName = it) } ?: e
        } + overlay.emulators.filter { it.packageName !in emulatorPackages }

        val mergedPriority = (emulatorPriority.keys + overlay.emulatorPriority.keys).associateWith { id ->
            val o = overlay.emulatorPriority[id].orEmpty()
            (o + emulatorPriority[id].orEmpty()).distinct()
        }

        return PlatformCatalog(
            revision = overlay.revision,
            platforms = mergedPlatforms,
            emulators = mergedEmulators,
            emulatorPriority = mergedPriority,
            launchSpecs = launchSpecs + overlay.launchSpecs
        )
    }

}
