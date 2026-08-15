package com.gamelaunch.frontend.ui.lockedmode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamelaunch.frontend.domain.lockedmode.LockedModeRepository
import com.gamelaunch.frontend.domain.lockedmode.LockedModeState
import com.gamelaunch.frontend.domain.lockedmode.PinResult
import com.gamelaunch.frontend.domain.lockedmode.message
import com.gamelaunch.frontend.systemui.SystemNavigationLockController
import com.gamelaunch.frontend.systemui.SystemNavigationLockStatus
import com.gamelaunch.frontend.systemui.SystemNavigationSetupProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

enum class LockedModeDialogStep {
    CREATE_PIN,
    CONFIRM_PIN,
    NEW_PIN,
    CONFIRM_NEW_PIN,
}

data class LockedModeSettingsUiState(
    val lockedModeState: LockedModeState? = null,
    val hasPin: Boolean = false,
    val dialogStep: LockedModeDialogStep? = null,
    val error: String? = null,
    val blockSystemNavigation: Boolean = false,
    val showSystemNavigationWarning: Boolean = false,
    val systemNavigationStatus: SystemNavigationLockStatus = SystemNavigationLockStatus.DISABLED,
    val systemNavigationSetupProgress: SystemNavigationSetupProgress = SystemNavigationSetupProgress(),
)

/**
 * Owns Locked Mode configuration and its PIN-dialog workflow on the Settings screen.
 * App-wide lock state, activation, and unlocking are handled by [LockedModeViewModel].
 */
@HiltViewModel
class LockedModeSettingsViewModel @Inject constructor(
    private val repository: LockedModeRepository,
    private val systemNavigationLockController: SystemNavigationLockController,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LockedModeSettingsUiState())
    val uiState: StateFlow<LockedModeSettingsUiState> = _uiState.asStateFlow()
    private var newPin = ""

    init {
        viewModelScope.launch {
            combine(
                repository.state,
                repository.hasPin,
                repository.blockSystemNavigation,
                systemNavigationLockController.status,
                systemNavigationLockController.setupProgress,
            ) { state, hasPin, blockNavigation, navigationStatus, setupProgress ->
                LockedModeSettingsUiState(
                    lockedModeState = state,
                    hasPin = hasPin,
                    blockSystemNavigation = blockNavigation,
                    systemNavigationStatus = navigationStatus,
                    systemNavigationSetupProgress = setupProgress,
                )
            }.collectLatest { owned ->
                _uiState.value = _uiState.value.copy(
                    lockedModeState = owned.lockedModeState,
                    hasPin = owned.hasPin,
                    blockSystemNavigation = owned.blockSystemNavigation,
                    systemNavigationStatus = owned.systemNavigationStatus,
                    systemNavigationSetupProgress = owned.systemNavigationSetupProgress,
                )
            }
        }
    }

    fun startSetup() = start(LockedModeDialogStep.CREATE_PIN)

    fun startChange() = start(LockedModeDialogStep.NEW_PIN)

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setEnabled(enabled) }
    }

    fun lockNow() {
        viewModelScope.launch { repository.activate() }
    }

    fun removePin() {
        viewModelScope.launch { repository.removePin() }
    }

    fun setBlockSystemNavigation(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled && !repository.isSystemNavigationWarningAcknowledged()) {
                _uiState.value = _uiState.value.copy(showSystemNavigationWarning = true)
            } else {
                repository.setBlockSystemNavigation(enabled)
                if (!enabled) systemNavigationLockController.dismissPairingNotification()
            }
        }
    }

    fun confirmBlockSystemNavigation() {
        _uiState.value = _uiState.value.copy(showSystemNavigationWarning = false)
        viewModelScope.launch {
            repository.acknowledgeSystemNavigationWarning()
            repository.setBlockSystemNavigation(true)
        }
    }

    fun dismissSystemNavigationWarning() {
        _uiState.value = _uiState.value.copy(showSystemNavigationWarning = false)
    }

    fun openDevelopmentSettings() = systemNavigationLockController.openDevelopmentSettings()

    fun openDeviceInfoSettings() = systemNavigationLockController.openDeviceInfoSettings()

    fun beginEmbeddedPairingSetup() = systemNavigationLockController.beginPairingSetup()

    fun prepareEmbeddedPairingNotification() =
        systemNavigationLockController.preparePairingNotification()

    fun refreshSystemNavigationSetupProgress() = systemNavigationLockController.reconcile()

    fun dismissDialog() = finishWorkflow()

    fun submitPin(pin: String) {
        _uiState.value = _uiState.value.copy(error = null)
        when (_uiState.value.dialogStep) {
            LockedModeDialogStep.CREATE_PIN -> {
                newPin = pin
                showStep(LockedModeDialogStep.CONFIRM_PIN)
            }

            LockedModeDialogStep.CONFIRM_PIN -> confirmSetup(pin)
            LockedModeDialogStep.NEW_PIN -> {
                newPin = pin
                showStep(LockedModeDialogStep.CONFIRM_NEW_PIN)
            }

            LockedModeDialogStep.CONFIRM_NEW_PIN -> confirmChange(pin)
            null -> Unit
        }
    }

    private fun start(step: LockedModeDialogStep) {
        clearPins()
        _uiState.value = _uiState.value.copy(dialogStep = step, error = null)
    }

    private fun confirmSetup(pin: String) {
        if (pin != newPin) {
            newPin = ""
            showStep(LockedModeDialogStep.CREATE_PIN, "PINs do not match")
            return
        }
        viewModelScope.launch {
            val result = repository.configure(pin)
            handleResult(result)
        }
    }

    private fun confirmChange(pin: String) {
        if (pin != newPin) {
            newPin = ""
            showStep(LockedModeDialogStep.NEW_PIN, "PINs do not match")
            return
        }
        viewModelScope.launch {
            handleResult(repository.configure(pin))
        }
    }

    private fun handleResult(result: PinResult) {
        if (result == PinResult.Success) finishWorkflow()
        else _uiState.value = _uiState.value.copy(error = result.message())
    }

    private fun showStep(step: LockedModeDialogStep, error: String? = null) {
        _uiState.value = _uiState.value.copy(dialogStep = step, error = error)
    }

    private fun finishWorkflow() {
        clearPins()
        _uiState.value = _uiState.value.copy(dialogStep = null, error = null)
    }

    private fun clearPins() {
        newPin = ""
    }
}
