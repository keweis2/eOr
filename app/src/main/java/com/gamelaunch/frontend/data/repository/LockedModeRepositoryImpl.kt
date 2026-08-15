package com.gamelaunch.frontend.data.repository

import com.gamelaunch.frontend.data.preferences.AppDataStore
import com.gamelaunch.frontend.domain.lockedmode.LockedModeRepository
import com.gamelaunch.frontend.domain.lockedmode.LockedModeState
import com.gamelaunch.frontend.domain.lockedmode.PinResult
import com.gamelaunch.frontend.domain.lockedmode.UNKNOWN_BOOT_COUNT
import com.gamelaunch.frontend.domain.system.BootCountProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private const val PIN_LENGTH = 4

@Singleton
class LockedModeRepositoryImpl @Inject constructor(
    private val dataStore: AppDataStore,
    private val bootCountProvider: BootCountProvider,
) : LockedModeRepository {
    override val state: Flow<LockedModeState> = dataStore.lockedMode.map { record ->
        deriveLockedModeState(
            record.enabled,
            record.active,
            record.activeBootCount,
            bootCountProvider.currentBootCount(),
        )
    }
    override suspend fun setEnabled(enabled: Boolean) {
        dataStore.setLockedModeEnabled(enabled)
    }

    override suspend fun activate() {
        if (state.first() == LockedModeState.DISABLED) return
        dataStore.setLockedModeActive(true, bootCountProvider.currentBootCount())
    }

    override suspend fun isLocked(): Boolean = state.first() == LockedModeState.LOCKED

    override val hasPin: Flow<Boolean> = dataStore.lockedMode.map { it.pin.isNotBlank() }
    override val blockSystemNavigation: Flow<Boolean> =
        dataStore.lockedMode.map { it.blockSystemNavigation }

    override suspend fun configure(pin: String): PinResult {
        if (!isValidLockedModePin(pin)) return PinResult.InvalidPin
        dataStore.configureLockedMode(pin)
        return PinResult.Success
    }

    override suspend fun unlock(pin: String?): PinResult {
        val record = dataStore.lockedMode.first()
        val result = if (record.pin.isBlank()) PinResult.Success else verify(pin.orEmpty())
        if (result == PinResult.Success) {
            dataStore.setLockedModeActive(false, bootCountProvider.currentBootCount())
        }
        return result
    }

    override suspend fun removePin() {
        dataStore.removeLockedModePin()
    }

    override suspend fun setBlockSystemNavigation(enabled: Boolean) {
        dataStore.setLockedModeBlockSystemNavigation(enabled)
    }

    override suspend fun isSystemNavigationWarningAcknowledged(): Boolean =
        dataStore.lockedMode.first().systemNavigationWarningAcknowledged

    override suspend fun acknowledgeSystemNavigationWarning() {
        dataStore.acknowledgeSystemNavigationWarning()
    }

    override suspend fun verify(pin: String): PinResult {
        val record = dataStore.lockedMode.first()
        if (record.pin.isBlank()) return PinResult.NotConfigured
        return if (isValidLockedModePin(pin) && pin == record.pin) {
            PinResult.Success
        } else PinResult.InvalidPin
    }
}

internal fun deriveLockedModeState(
    enabled: Boolean,
    active: Boolean,
    activeBootCount: Int,
    currentBootCount: Int,
): LockedModeState {
    return when {
        !enabled -> LockedModeState.DISABLED
        // Only really lock when activation was at this boot count
        active &&
            activeBootCount != UNKNOWN_BOOT_COUNT &&
            currentBootCount != UNKNOWN_BOOT_COUNT &&
            activeBootCount == currentBootCount -> LockedModeState.LOCKED
        else -> LockedModeState.READY
    }
}

internal fun isValidLockedModePin(pin: String) = pin.length == PIN_LENGTH && pin.all(Char::isDigit)
