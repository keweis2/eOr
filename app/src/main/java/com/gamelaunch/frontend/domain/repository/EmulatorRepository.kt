package com.gamelaunch.frontend.domain.repository

import com.gamelaunch.frontend.domain.model.EmulatorMapping
import com.gamelaunch.frontend.domain.model.InstalledEmulator
import com.gamelaunch.frontend.domain.platform.PlatformCatalog
import kotlinx.coroutines.flow.Flow

interface EmulatorRepository {
    suspend fun getMappingForPlatform(platformId: String): EmulatorMapping?
    fun getAllMappings(): Flow<List<EmulatorMapping>>
    suspend fun upsertMapping(mapping: EmulatorMapping)
    suspend fun deleteMappingForPlatform(platformId: String)
    fun getInstalledEmulators(): List<InstalledEmulator>
    /** Scans installed emulators and auto-assigns the best one per platform. Returns configured count. */
    suspend fun autoDetectAndAssign(): Int
    /**
     * Like [autoDetectAndAssign] but only for platforms with no mapping yet — never overrides a
     * choice. Used for systems that arrive with a downloaded catalog. Returns configured count.
     */
    suspend fun assignMissing(): Int
    /**
     * After a catalog update, moves RetroArch mappings still on a platform's old default core to
     * the new default — so a catalog core fix reaches existing installs. A core the user chose
     * themselves (anything other than the old default) is left alone. Returns updated count.
     */
    suspend fun followCatalogCores(before: PlatformCatalog, after: PlatformCatalog): Int
}
