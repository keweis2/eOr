package com.gamelaunch.frontend.ui.screen.settings

import android.content.Context
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamelaunch.frontend.data.sync.ConflictFile
import com.gamelaunch.frontend.data.sync.RunConditions
import com.gamelaunch.frontend.data.sync.SaveFilesManager
import com.gamelaunch.frontend.data.sync.SyncEngineManager
import com.gamelaunch.frontend.data.sync.SyncthingController
import com.gamelaunch.frontend.data.sync.SyncthingService
import com.gamelaunch.frontend.domain.repository.SettingsRepository
import com.gamelaunch.frontend.domain.sync.EmulatorSyncStatus
import com.gamelaunch.frontend.domain.sync.SaveLocationRegistry
import com.gamelaunch.frontend.domain.sync.SyncReadiness
import com.gamelaunch.frontend.launcher.PackageManagerHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Save Sync: master on/off toggle, emulator sync-readiness detection, the embedded Syncthing engine
 * (device ID + pairing), auto-registration of Ready save folders, a one-time pre-sync backup,
 * conflict resolution, and run conditions (Wi-Fi only / while charging).
 */
@HiltViewModel
class SaveSyncViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val packageManagerHelper: PackageManagerHelper,
    private val settingsRepository: SettingsRepository,
    private val syncthingController: SyncthingController,
    private val saveFilesManager: SaveFilesManager,
    private val syncEngineManager: SyncEngineManager,
    private val runConditions: RunConditions
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val statuses: List<EmulatorSyncStatus> = emptyList(),
        val enabled: Boolean = false,
        val engineSupported: Boolean = true,
        val engineRunning: Boolean = false,
        val engineStarting: Boolean = false,
        val deviceId: String? = null,
        val engineError: String? = null,
        val linkResult: String? = null,
        val backupCount: Int = -1,
        val conflicts: List<ConflictFile> = emptyList(),
        val wifiOnly: Boolean = false,
        val chargingOnly: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    init {
        viewModelScope.launch {
            val enabled = settingsRepository.saveSyncEnabled.first()
            val wifi = settingsRepository.syncWifiOnly.first()
            val charging = settingsRepository.syncChargingOnly.first()
            val statuses = computeStatuses()
            _uiState.update {
                it.copy(
                    loading = false,
                    engineSupported = syncthingController.isSupported(),
                    enabled = enabled,
                    wifiOnly = wifi,
                    chargingOnly = charging,
                    statuses = statuses
                )
            }
            if (enabled && syncthingController.isSupported()) startEngine()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            val statuses = computeStatuses()
            _uiState.update { it.copy(loading = false, statuses = statuses) }
        }
    }

    private suspend fun computeStatuses(): List<EmulatorSyncStatus> = withContext(Dispatchers.IO) {
        val installed = SaveLocationRegistry.specs
            .flatMap { it.packages }
            .filter { packageManagerHelper.isPackageInstalled(it) }
            .toSet()
        val appDataBlocked = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        SaveLocationRegistry.resolve(installed, appDataBlocked)
    }

    /** Master toggle: persist, then start or stop the engine accordingly. */
    fun setEnabled(on: Boolean) {
        _uiState.update { it.copy(enabled = on, engineError = null) }
        viewModelScope.launch { settingsRepository.setSaveSyncEnabled(on) }
        if (on) startEngine() else stopEngine()
    }

    fun setWifiOnly(on: Boolean) {
        _uiState.update { it.copy(wifiOnly = on) }
        viewModelScope.launch { settingsRepository.setSyncWifiOnly(on) }
        reevaluateConditions()
    }

    fun setChargingOnly(on: Boolean) {
        _uiState.update { it.copy(chargingOnly = on) }
        viewModelScope.launch { settingsRepository.setSyncChargingOnly(on) }
        reevaluateConditions()
    }

    /** When run conditions change, stop if now unmet, or (re)start if enabled and now met. */
    private fun reevaluateConditions() {
        val s = _uiState.value
        if (!s.enabled) return
        val reason = runConditions.unmetReason(s.wifiOnly, s.chargingOnly)
        if (reason != null) {
            stopEngine()
            _uiState.update { it.copy(engineError = reason) }
        } else if (!s.engineRunning && !s.engineStarting) {
            startEngine()
        }
    }

    private fun startEngine() {
        val s = _uiState.value
        if (s.engineStarting || s.engineRunning) return
        if (!syncthingController.isSupported()) {
            _uiState.update { it.copy(engineError = "Sync engine isn't available for this device.") }
            return
        }
        runConditions.unmetReason(s.wifiOnly, s.chargingOnly)?.let { reason ->
            _uiState.update { it.copy(engineError = reason) }
            return
        }
        _uiState.update { it.copy(engineStarting = true, engineError = null) }
        syncEngineManager.ensureRunning()
        viewModelScope.launch {
            val id = syncthingController.awaitDeviceId()
            if (id == null) {
                _uiState.update { it.copy(engineStarting = false, engineError = "Engine didn't respond in time.") }
                return@launch
            }
            val folders = readyFolders(_uiState.value.statuses)
            var backupCount = _uiState.value.backupCount
            if (folders.isNotEmpty()) {
                // One-time safety backup before the first sync could touch existing saves.
                if (!saveFilesManager.hasInitialBackup()) {
                    backupCount = withContext(Dispatchers.IO) { saveFilesManager.backup(folders) }
                }
                syncthingController.configureFolders(folders)
            }
            val conflicts = withContext(Dispatchers.IO) { saveFilesManager.findConflicts(folders) }
            _uiState.update {
                it.copy(
                    engineStarting = false,
                    engineRunning = true,
                    deviceId = id,
                    backupCount = backupCount,
                    conflicts = conflicts
                )
            }
        }
    }

    private fun stopEngine() {
        // Only actually stops the daemon if Friends isn't using it too.
        viewModelScope.launch { syncEngineManager.refresh() }
        _uiState.update { it.copy(engineRunning = false, engineStarting = false, deviceId = null) }
    }

    /** Link another device by its Syncthing ID (scanned or pasted) so folders sync with it. */
    fun linkDevice(deviceId: String) {
        val id = deviceId.trim()
        if (id.isBlank()) return
        viewModelScope.launch {
            val ok = syncthingController.addPeer(id)
            _uiState.update {
                it.copy(linkResult = if (ok) "Device linked — sync will begin when it's online." else "Couldn't link that device ID.")
            }
        }
    }

    fun resolveConflict(conflict: ConflictFile, keep: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { saveFilesManager.resolveConflict(conflict.path, keep) }
            val folders = readyFolders(_uiState.value.statuses)
            val conflicts = withContext(Dispatchers.IO) { saveFilesManager.findConflicts(folders) }
            _uiState.update { it.copy(conflicts = conflicts) }
        }
    }

    /** Ready shared-storage folders → Syncthing folders with stable IDs shared across devices. */
    private fun readyFolders(statuses: List<EmulatorSyncStatus>): List<SyncthingController.SyncFolder> =
        statuses.filter { it.readiness == SyncReadiness.READY }.flatMap { st ->
            st.syncableDirs.map { dir ->
                val base = dir.substringAfterLast('/')
                val slug = "${st.spec.displayName}-$base"
                    .lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
                SyncthingController.SyncFolder(
                    id = "${SyncthingController.EOR_FOLDER_PREFIX}$slug",
                    label = "${st.spec.displayName} $base",
                    path = dir
                )
            }
        }
}
