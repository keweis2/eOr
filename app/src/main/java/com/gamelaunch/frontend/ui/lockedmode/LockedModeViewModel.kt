package com.gamelaunch.frontend.ui.lockedmode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamelaunch.frontend.domain.lockedmode.LockedModeRepository
import com.gamelaunch.frontend.domain.lockedmode.LockedModeState
import com.gamelaunch.frontend.domain.lockedmode.PinResult
import com.gamelaunch.frontend.systemui.SystemNavigationLockController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class LockedModeUiState(
    val state: LockedModeState? = null,
    val hasPin: Boolean? = null,
)

/**
 * Owns the app-wide runtime state and actions for entering or leaving Locked Mode.
 * Settings-specific configuration and PIN workflows live in [LockedModeSettingsViewModel].
 */
@HiltViewModel
class LockedModeViewModel @Inject constructor(
    private val repository: LockedModeRepository,
    private val systemNavigationLockController: SystemNavigationLockController,
) : ViewModel() {
    val uiState: StateFlow<LockedModeUiState> =
        combine(repository.state, repository.hasPin) { state, hasPin ->
            LockedModeUiState(state = state, hasPin = hasPin)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = LockedModeUiState(),
        )

    suspend fun activate() {
        repository.activate()
        systemNavigationLockController.reconcileFromRepository()
    }

    suspend fun unlock(pin: String? = null): PinResult {
        val result = repository.unlock(pin)
        if (result == PinResult.Success) {
            // Do not rely on the controller's asynchronous collector: unlocking must not complete
            // until the repository's new state has been read and SystemUI restoration has finished.
            systemNavigationLockController.reconcileFromRepository()
        }
        return result
    }
}
