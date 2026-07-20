package com.gamelaunch.frontend.data.sync

import android.content.Context
import com.gamelaunch.frontend.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single owner of the embedded Syncthing daemon's run state. Both Save Sync and Friends need the same
 * daemon, so neither may stop it unilaterally — the service should run whenever *either* feature is on.
 * Callers persist their enabled flag first, then call [refresh]; [ensureRunning] is an idempotent start.
 */
@Singleton
class SyncEngineManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val syncthingController: SyncthingController
) {
    fun ensureRunning() {
        if (syncthingController.isSupported()) SyncthingService.start(context)
    }

    /** Start if any feature needs the daemon, otherwise stop it. Read persisted flags to decide. */
    suspend fun refresh() {
        val needed = settingsRepository.saveSyncEnabled.first() || settingsRepository.friendsEnabled.first()
        if (needed && syncthingController.isSupported()) SyncthingService.start(context)
        else SyncthingService.stop(context)
    }
}
