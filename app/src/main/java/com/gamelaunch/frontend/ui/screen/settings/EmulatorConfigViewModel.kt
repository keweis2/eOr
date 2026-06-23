package com.gamelaunch.frontend.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamelaunch.frontend.domain.model.EmulatorMapping
import com.gamelaunch.frontend.domain.model.InstalledEmulator
import com.gamelaunch.frontend.domain.repository.EmulatorRepository
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
    val scanResult: String? = null  // shown as a one-shot snackbar message
)

@HiltViewModel
class EmulatorConfigViewModel @Inject constructor(
    private val emulatorRepository: EmulatorRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(EmulatorConfigUiState())
    val uiState: StateFlow<EmulatorConfigUiState> = _uiState

    init {
        val installed = emulatorRepository.getInstalledEmulators()
        _uiState.update { it.copy(installedEmulators = installed) }

        viewModelScope.launch {
            emulatorRepository.getAllMappings().collect { mappings ->
                _uiState.update { state ->
                    state.copy(mappings = mappings.associateBy { it.platformId })
                }
            }
        }

        // Auto-detect on first open when the user has never configured anything
        viewModelScope.launch {
            val existing = emulatorRepository.getAllMappings().first()
            if (existing.isEmpty()) {
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
