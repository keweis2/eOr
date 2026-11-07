package com.gamelaunch.frontend.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamelaunch.frontend.domain.model.InstalledApp
import com.gamelaunch.frontend.launcher.LaunchScreen
import com.gamelaunch.frontend.launcher.PackageManagerHelper
import com.gamelaunch.frontend.domain.lockedmode.LockedModeAppRepository
import com.gamelaunch.frontend.domain.lockedmode.LockedModeRepository
import com.gamelaunch.frontend.domain.lockedmode.LockedModeState
import com.gamelaunch.frontend.domain.repository.InstalledAppsRepository
import com.gamelaunch.frontend.domain.usecase.LaunchAppUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    private val installedAppsRepository: InstalledAppsRepository,
    private val launchAppUseCase: LaunchAppUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppsUiState())
    val uiState: StateFlow<AppsUiState> = _uiState

    init {
        viewModelScope.launch {
            combine(
                installedAppsRepository.apps,
                lockedModeRepository.state,
                lockedModeAppRepository.allowedPackages
            ) { installed, lockState, allowed ->
                val locked = lockState == LockedModeState.LOCKED
                AppsUiState(
                    apps = filterAppsForLockedMode(installed, locked, allowed),
                    isLoading = installed == null,
                    isLocked = locked
                )
            }.collect { appsState ->
                _uiState.update { appsState.copy(launchError = it.launchError) }
            }
        }
        viewModelScope.launch { installedAppsRepository.ensureLoaded() }
    }

    fun refresh() {
        viewModelScope.launch {
            installedAppsRepository.refresh()
        }
    }

    fun launchApp(packageName: String) = launchApp(packageName, LaunchScreen.BOTTOM)

    fun launchApp(packageName: String, screen: LaunchScreen) {
        viewModelScope.launch {
            launchAppUseCase(packageName, screen).onFailure { failure ->
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
