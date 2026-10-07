package com.gamelaunch.frontend.domain.repository

import com.gamelaunch.frontend.domain.model.EmulatorMapping
import com.gamelaunch.frontend.domain.model.InstalledEmulator
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
}
