package com.gamelaunch.frontend.domain.lockedmode

import kotlinx.coroutines.flow.Flow

interface LockedModeAppRepository {
    val allowedPackages: Flow<Set<String>>
    suspend fun setAllowed(packageName: String, allowed: Boolean)
    suspend fun clearAllowed()
    suspend fun isAllowed(packageName: String): Boolean
}
