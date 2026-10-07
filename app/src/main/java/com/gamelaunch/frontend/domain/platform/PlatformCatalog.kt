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

    // Extension → platform, for files outside any recognised folder. Ambiguous extensions (e.g.
    // .iso, .bin) are filtered out by PlatformDetector; folder-only platforms never appear here.
    val byExtension: Map<String, Platform> = buildMap {
        platforms.filter { it.detectByExtension }.forEach { platform ->
            platform.extensions.forEach { ext -> putIfAbsent(ext.lowercase(), platform) }
        }
    }

    // First claimant wins: built-ins come first, so a catalog-added system can never take over a
    // folder an existing system already owns (which would silently re-home that system's games).
    val byFolderName: Map<String, Platform> = buildMap {
        platforms.forEach { platform ->
            platform.folderNames.forEach { name -> putIfAbsent(name.lowercase(), platform) }
        }
    }

    val emulatorPackages: Set<String> = emulators.map { it.packageName }.toSet()

    /**
     * Layers a downloaded catalog over this one. The overlay can add and adjust, but never take
     * away: the ROM scanner deletes games whose platform it no longer recognises, so a bad remote
     * catalog must not be able to shrink a platform's extensions/folders or drop a platform.
     *
     * - Platforms: matched by id. Overlay wins for scalar fields it sets; extensions, folder names
     *   and ES-DE dirs are unioned. New platforms are appended (built-in order is kept, which some
     *   screens rely on) and can't claim a folder name an earlier platform already owns.
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
                defaultCoreForRetroArch = o.defaultCoreForRetroArch ?: base.defaultCoreForRetroArch,
                // detectByExtension stays as built: turning it off could drop loose-file games.
                iconKey = o.iconKey ?: base.iconKey,
                label = o.label ?: base.label,
                padStyle = o.padStyle ?: base.padStyle,
                coverAspect = o.coverAspect ?: base.coverAspect,
                releaseYear = o.releaseYear ?: base.releaseYear,
                brand = o.brand ?: base.brand,
                kind = o.kind ?: base.kind,
                libretroThumbnails = o.libretroThumbnails ?: base.libretroThumbnails,
                launchBoxPlatform = o.launchBoxPlatform ?: base.launchBoxPlatform,
                esdeDirs = (o.esdeDirs + base.esdeDirs).distinct()
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
