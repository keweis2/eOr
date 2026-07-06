package com.gamelaunch.frontend.ui.screen.settings

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamelaunch.frontend.domain.sync.EmulatorSyncStatus
import com.gamelaunch.frontend.domain.sync.SaveLocationRegistry
import com.gamelaunch.frontend.launcher.PackageManagerHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Detection layer for Save Sync: which installed emulators eOr can sync on this device, given
 * Android's storage rules. Read-only for now — the Syncthing engine is added in a later milestone.
 */
@HiltViewModel
class SaveSyncViewModel @Inject constructor(
    private val packageManagerHelper: PackageManagerHelper
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val statuses: List<EmulatorSyncStatus> = emptyList()
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true)
            val statuses = withContext(Dispatchers.IO) {
                val installed = SaveLocationRegistry.specs
                    .flatMap { it.packages }
                    .filter { packageManagerHelper.isPackageInstalled(it) }
                    .toSet()
                // Android 11+ (API 30) blocks Android/data even with All-Files-Access.
                val appDataBlocked = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                SaveLocationRegistry.resolve(installed, appDataBlocked)
            }
            _uiState.value = UiState(loading = false, statuses = statuses)
        }
    }
}
