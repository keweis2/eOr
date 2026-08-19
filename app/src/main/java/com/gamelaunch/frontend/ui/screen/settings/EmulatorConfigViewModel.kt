package com.gamelaunch.frontend.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamelaunch.frontend.domain.model.EmulatorMapping
import com.gamelaunch.frontend.domain.model.EmulatorUpdate
import com.gamelaunch.frontend.domain.model.InstalledEmulator
import com.gamelaunch.frontend.domain.repository.EmulatorRepository
import com.gamelaunch.frontend.domain.repository.ObtainiumPackRepository
import com.gamelaunch.frontend.domain.usecase.CheckEmulatorUpdatesUseCase
import com.gamelaunch.frontend.launcher.ObtainiumLauncher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EmulatorConfigUiState(
    val mappings: Map<String, EmulatorMapping> = emptyMap(),
    val installedEmulators: List<InstalledEmulator> = emptyList(),
    val isScanning: Boolean = false,
    val scanResult: String? = null,  // shown as a one-shot snackbar message
    // Obtainium update tracking
    val obtainiumInstalled: Boolean = false,
    val isCheckingUpdates: Boolean = false,
    val emulatorUpdates: List<EmulatorUpdate> = emptyList()
)

@HiltViewModel
class EmulatorConfigViewModel @Inject constructor(
    private val emulatorRepository: EmulatorRepository,
    private val packRepository: ObtainiumPackRepository,
    private val checkEmulatorUpdatesUseCase: CheckEmulatorUpdatesUseCase,
    private val obtainiumLauncher: ObtainiumLauncher
) : ViewModel() {

    private val _uiState = MutableStateFlow(EmulatorConfigUiState())
    val uiState: StateFlow<EmulatorConfigUiState> = _uiState

    init {
        val installed = emulatorRepository.getInstalledEmulators()
        _uiState.update {
            it.copy(installedEmulators = installed, obtainiumInstalled = obtainiumLauncher.isInstalled())
        }
        checkForEmulatorUpdates()

        viewModelScope.launch {
            emulatorRepository.getAllMappings().collect { mappings ->
                _uiState.update { state ->
                    state.copy(mappings = mappings.associateBy { it.platformId })
                }
            }
        }

        // Auto-detect on first open, or whenever any saved mapping points at a package
        // that is no longer installed (catches stale DB after package-name fixes).
        viewModelScope.launch {
            val existing = emulatorRepository.getAllMappings().first()
            val installedPkgs = _uiState.value.installedEmulators
                .filter { it.isInstalled }.map { it.packageName }.toSet()
            val hasStale = existing.any { it.packageName !in installedPkgs }
            if (existing.isEmpty() || hasStale) {
                runAutoDetect(silent = true)
            }
        }
    }

    fun upsertMapping(mapping: EmulatorMapping) {
        viewModelScope.launch {
            emulatorRepository.upsertMapping(mapping)
        }
    }

    fun rescanEmulators() {
        viewModelScope.launch { runAutoDetect(silent = false) }
    }

    fun clearScanResult() {
        _uiState.update { it.copy(scanResult = null) }
    }

    /** Native check of installed, GitHub-tracked emulators for newer releases. */
    fun checkForEmulatorUpdates() {
        viewModelScope.launch {
            _uiState.update { it.copy(isCheckingUpdates = true) }
            val updates = runCatching { checkEmulatorUpdatesUseCase() }.getOrDefault(emptyList())
            _uiState.update { it.copy(isCheckingUpdates = false, emulatorUpdates = updates) }
        }
    }

    /**
     * Hand the user's installed emulators to Obtainium so it tracks and installs their updates.
     * Routes to Obtainium's install page when it isn't installed yet.
     */
    fun trackWithObtainium() {
        viewModelScope.launch {
            if (!obtainiumLauncher.isInstalled()) {
                obtainiumLauncher.openInstallPage()
                _uiState.update { it.copy(scanResult = "Install Obtainium, then tap “Track updates” again") }
                return@launch
            }
            val entries = _uiState.value.installedEmulators
                .filter { it.isInstalled }
                .mapNotNull { packRepository.entryForPackage(it.packageName) }
                .distinctBy { it.id }
            val ok = obtainiumLauncher.importApps(entries)
            _uiState.update {
                it.copy(
                    obtainiumInstalled = true,
                    scanResult = when {
                        entries.isEmpty() -> "No installed emulators are tracked by the Obtainium pack yet"
                        ok -> "Opening Obtainium to track ${entries.size} emulator${if (entries.size != 1) "s" else ""}…"
                        else -> "Couldn't open Obtainium"
                    }
                )
            }
        }
    }

    /** Send a single emulator's source to Obtainium to update it (falls back to opening Obtainium). */
    fun updateWithObtainium(update: EmulatorUpdate) {
        val ok = obtainiumLauncher.addSingle(update.sourceUrl)
        if (!ok) obtainiumLauncher.open()
    }

    private suspend fun runAutoDetect(silent: Boolean) {
        _uiState.update { it.copy(isScanning = true, scanResult = null) }
        val count = emulatorRepository.autoDetectAndAssign()
        val installedCount = _uiState.value.installedEmulators.count { it.isInstalled }
        _uiState.update {
            it.copy(
                isScanning = false,
                scanResult = if (silent && count == 0) null
                             else "Found $installedCount emulator${if (installedCount != 1) "s" else ""}, configured $count platform${if (count != 1) "s" else ""}"
            )
        }
    }
}
