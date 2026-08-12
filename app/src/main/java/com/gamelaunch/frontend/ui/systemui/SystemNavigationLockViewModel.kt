package com.gamelaunch.frontend.ui.systemui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamelaunch.frontend.domain.lockedmode.LockedModeRepository
import com.gamelaunch.frontend.domain.lockedmode.LockedModeState
import com.gamelaunch.frontend.systemui.SystemNavigationLockController
import com.gamelaunch.frontend.systemui.SystemNavigationLockStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class SystemNavigationPrompt {
    ENABLE_DEVELOPER_OPTIONS,
    ENABLE_WIRELESS_DEBUGGING,
    PAIR_DEVICE,
    RESTORE_NAVIGATION,
}

data class SystemNavigationLockUiState(
    val prompt: SystemNavigationPrompt? = null,
)

@HiltViewModel
class SystemNavigationLockViewModel @Inject constructor(
    repository: LockedModeRepository,
    private val controller: SystemNavigationLockController,
) : ViewModel() {
    private val promptDismissed = MutableStateFlow(false)

    val uiState: StateFlow<SystemNavigationLockUiState> = combine(
        repository.state,
        repository.blockSystemNavigation,
        controller.status,
        promptDismissed,
    ) { lockedMode, optionEnabled, status, dismissed ->
        SystemNavigationLockUiState(
            prompt = if (dismissed) null else status.toPrompt(lockedMode, optionEnabled),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SystemNavigationLockUiState(),
    )

    fun reconcile() = controller.reconcile()

    fun dismissPrompt() {
        promptDismissed.value = true
    }

    fun performPrimaryAction(prompt: SystemNavigationPrompt) {
        when (prompt) {
            SystemNavigationPrompt.ENABLE_DEVELOPER_OPTIONS,
            SystemNavigationPrompt.ENABLE_WIRELESS_DEBUGGING,
            SystemNavigationPrompt.PAIR_DEVICE,
            -> controller.beginPairingSetup()
            SystemNavigationPrompt.RESTORE_NAVIGATION -> controller.startBrokerSetup()
        }
    }
}

internal fun SystemNavigationLockStatus.toPrompt(
    lockedMode: LockedModeState,
    optionEnabled: Boolean,
): SystemNavigationPrompt? = when {
    this == SystemNavigationLockStatus.RESTORE_REQUIRED ->
        SystemNavigationPrompt.RESTORE_NAVIGATION
    lockedMode != LockedModeState.LOCKED || !optionEnabled -> null
    this == SystemNavigationLockStatus.DEVELOPER_OPTIONS_REQUIRED -> SystemNavigationPrompt.ENABLE_DEVELOPER_OPTIONS
    this == SystemNavigationLockStatus.WIRELESS_DEBUGGING_REQUIRED -> SystemNavigationPrompt.ENABLE_WIRELESS_DEBUGGING
    this == SystemNavigationLockStatus.PAIRING_REQUIRED -> SystemNavigationPrompt.PAIR_DEVICE
    else -> null
}
