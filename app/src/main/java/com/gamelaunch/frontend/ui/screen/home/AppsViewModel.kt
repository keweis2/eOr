package com.gamelaunch.frontend.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamelaunch.frontend.domain.model.InstalledApp
import com.gamelaunch.frontend.launcher.PackageManagerHelper
import com.gamelaunch.frontend.domain.lockedmode.LockedModeAppRepository
import com.gamelaunch.frontend.domain.lockedmode.LockedModeRepository
import com.gamelaunch.frontend.domain.lockedmode.LockedModeState
import com.gamelaunch.frontend.domain.usecase.LaunchAppUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class AppsUiState(
    val apps: List<InstalledApp> = emptyList(),
    val isLoading: Boolean = true,
    val isLocked: Boolean = true,
    val launchError: String? = null
)

@HiltViewModel
class AppsViewModel @Inject constructor(
    val packageManagerHelper: PackageManagerHelper,
    lockedModeRepository: LockedModeRepository,
    lockedModeAppRepository: LockedModeAppRepository,
    private val launchAppUseCase: LaunchAppUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppsUiState())
    val uiState: StateFlow<AppsUiState> = _uiState

    private val installedApps = MutableStateFlow<List<InstalledApp>?>(null)

    init {
        viewModelScope.launch {
            combine(
                installedApps,
                lockedModeRepository.state,
                lockedModeAppRepository.allowedPackages
            ) { installed, lockState, allowed ->
                val locked = lockState == LockedModeState.LOCKED
                AppsUiState(
                    apps = filterAppsForLockedMode(installed, locked, allowed),
                    isLoading = installed == null,
                    isLocked = locked
                )
            }.collect { _uiState.value = it }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val apps = withContext(Dispatchers.IO) { packageManagerHelper.getInstalledApps() }
            installedApps.value = apps
        }
    }

    fun launchApp(packageName: String) {
        viewModelScope.launch {
            launchAppUseCase(packageName).onFailure { failure ->
                _uiState.update { it.copy(launchError = failure.message ?: "App could not be launched") }
            }
        }
    }

    fun clearLaunchError() = _uiState.update { it.copy(launchError = null) }
}

internal fun filterAppsForLockedMode(
    installed: List<InstalledApp>?,
    locked: Boolean,
    allowed: Set<String>
): List<InstalledApp> = when {
    installed == null -> emptyList()
    locked -> installed.filter { it.packageName in allowed }
    else -> installed
}
