package com.gamelaunch.frontend.data.repository

import com.gamelaunch.frontend.data.preferences.AppDataStore
import com.gamelaunch.frontend.domain.lockedmode.LockedModeAppRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LockedModeAppRepositoryImpl @Inject constructor(
    private val dataStore: AppDataStore
) : LockedModeAppRepository {
    override val allowedPackages: Flow<Set<String>> = dataStore.lockedModeAllowedAppPackages
    override suspend fun setAllowed(packageName: String, allowed: Boolean) {
        dataStore.setLockedModeAppAllowed(packageName, allowed)
    }
    override suspend fun clearAllowed() {
        dataStore.clearLockedModeAllowedApps()
    }
    override suspend fun isAllowed(packageName: String) = allowedPackages.first().contains(packageName)
}
