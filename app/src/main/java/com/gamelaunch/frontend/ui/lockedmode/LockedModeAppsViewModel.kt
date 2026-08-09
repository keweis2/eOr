package com.gamelaunch.frontend.ui.lockedmode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamelaunch.frontend.domain.lockedmode.LockedModeAppRepository
import com.gamelaunch.frontend.domain.model.InstalledApp
import com.gamelaunch.frontend.launcher.PackageManagerHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LockedModeAppsUiState(
    val installedApps: List<InstalledApp> = emptyList(),
    val allowedPackages: Set<String> = emptySet(),
    val savingPackages: Set<String> = emptySet(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class LockedModeAppsViewModel @Inject constructor(
    private val appRepository: LockedModeAppRepository,
    val packageManagerHelper: PackageManagerHelper,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LockedModeAppsUiState())
    val uiState: StateFlow<LockedModeAppsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            appRepository.allowedPackages.collectLatest { allowed ->
                _uiState.update { it.copy(allowedPackages = allowed) }
            }
        }
        loadApps()
    }

    fun retryLoadingApps() {
        if (!_uiState.value.isLoading) loadApps()
    }

    fun setAppAllowed(packageName: String, allowed: Boolean) {
        val wasAllowed = packageName in _uiState.value.allowedPackages
        if (packageName in _uiState.value.savingPackages || wasAllowed == allowed) return

        _uiState.update {
            it.copy(
                allowedPackages = it.allowedPackages.withMembership(packageName, allowed),
                savingPackages = it.savingPackages + packageName,
                error = null,
            )
        }
        viewModelScope.launch {
            runCatching { appRepository.setAllowed(packageName, allowed) }
                .onFailure { failure ->
                    _uiState.update {
                        it.copy(
                            allowedPackages = it.allowedPackages.withMembership(packageName, wasAllowed),
                            error = failure.message ?: "Could not save app permission",
                        )
                    }
                }
            _uiState.update { it.copy(savingPackages = it.savingPackages - packageName) }
        }
    }

    private fun loadApps() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { packageManagerHelper.getInstalledApps() } }
                .onSuccess { apps ->
                    _uiState.update { it.copy(installedApps = apps, isLoading = false) }
                }
                .onFailure { failure ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = failure.message ?: "Could not load installed apps",
                        )
                    }
                }
        }
    }
}

private fun Set<String>.withMembership(packageName: String, included: Boolean): Set<String> =
    if (included) this + packageName else this - packageName
