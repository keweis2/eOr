package com.gamelaunch.frontend.domain.repository

import com.gamelaunch.frontend.domain.model.PackApp

/**
 * Access to the Obtainium Emulation Pack — the mapping of emulator packages to their update sources.
 * Loaded from the network (kept current as new emulators are added) with a bundled asset fallback.
 */
interface ObtainiumPackRepository {

    /** The full pack, network-first with a bundled-asset fallback. Cached for the session. */
    suspend fun getPack(): List<PackApp>

    /** The pack entry that tracks [packageName], resolving eOr's package-id variants. */
    suspend fun entryForPackage(packageName: String): PackApp?

    /**
     * Build the `obtainium://apps/` payload (a JSON array of raw pack entries, verbatim) for the
     * given entries — used both to track installed emulators and to install missing essentials.
     */
    suspend fun buildImportJson(entries: List<PackApp>): String

    /** Recommended emulators (see essential set) whose package isn't in [installedPackages]. */
    suspend fun missingEssentials(installedPackages: Set<String>): List<PackApp>
}
