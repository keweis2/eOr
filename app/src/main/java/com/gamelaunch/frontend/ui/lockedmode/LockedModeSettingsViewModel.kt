package com.gamelaunch.frontend.ui.lockedmode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamelaunch.frontend.domain.lockedmode.LockedModeRepository
import com.gamelaunch.frontend.domain.lockedmode.LockedModeState
import com.gamelaunch.frontend.domain.lockedmode.PinResult
import com.gamelaunch.frontend.domain.lockedmode.message
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
)

/**
 * Owns Locked Mode configuration and its PIN-dialog workflow on the Settings screen.
 * App-wide lock state, activation, and unlocking are handled by [LockedModeViewModel].
 */
@HiltViewModel
class LockedModeSettingsViewModel @Inject constructor(
    private val repository: LockedModeRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LockedModeSettingsUiState())
    val uiState: StateFlow<LockedModeSettingsUiState> = _uiState.asStateFlow()

    private var newPin = ""

    init {
        viewModelScope.launch {
            combine(repository.state, repository.hasPin) { state, hasPin -> state to hasPin }
                .collectLatest { (state, hasPin) ->
                _uiState.value = _uiState.value.copy(lockedModeState = state, hasPin = hasPin)
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
