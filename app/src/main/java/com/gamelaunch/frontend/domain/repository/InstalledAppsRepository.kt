package com.gamelaunch.frontend.domain.repository

import com.gamelaunch.frontend.domain.model.InstalledApp
import kotlinx.coroutines.flow.StateFlow

/** Shared installed apps; null means the first scan has not completed. */
interface InstalledAppsRepository {
    val apps: StateFlow<List<InstalledApp>?>
    suspend fun ensureLoaded()
    suspend fun refresh()
}
