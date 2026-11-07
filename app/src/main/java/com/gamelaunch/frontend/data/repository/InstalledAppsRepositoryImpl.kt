package com.gamelaunch.frontend.data.repository

import com.gamelaunch.frontend.domain.model.InstalledApp
import com.gamelaunch.frontend.domain.repository.InstalledAppsRepository
import com.gamelaunch.frontend.launcher.PackageManagerHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InstalledAppsRepositoryImpl @Inject constructor(
    private val packageManagerHelper: PackageManagerHelper,
) : InstalledAppsRepository {
    private val installedApps = MutableStateFlow<List<InstalledApp>?>(null)
    override val apps = installedApps.asStateFlow()
    private val scanMutex = Mutex()

    override suspend fun ensureLoaded() {
        scanMutex.withLock {
            if (installedApps.value == null) scan()
        }
    }

    override suspend fun refresh() {
        scanMutex.withLock { scan() }
    }

    private suspend fun scan() {
        val apps = withContext(Dispatchers.IO) { packageManagerHelper.getInstalledApps() }
        installedApps.value = apps
    }
}
