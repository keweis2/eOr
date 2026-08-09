package com.gamelaunch.frontend.data.repository

import com.gamelaunch.frontend.data.preferences.AppDataStore
import com.gamelaunch.frontend.domain.lockedmode.LockedModeRepository
import com.gamelaunch.frontend.domain.lockedmode.LockedModeState
import com.gamelaunch.frontend.domain.lockedmode.PinResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private const val PIN_LENGTH = 4

@Singleton
class LockedModeRepositoryImpl @Inject constructor(
    private val dataStore: AppDataStore
) : LockedModeRepository {
    override val state: Flow<LockedModeState> = dataStore.lockedMode.map { record ->
        deriveLockedModeState(record.enabled, record.active)
    }
    override suspend fun setEnabled(enabled: Boolean) {
        dataStore.setLockedModeEnabled(enabled)
    }

    override suspend fun activate() {
        if (state.first() == LockedModeState.DISABLED) return
        dataStore.setLockedModeActive(true)
    }

    override suspend fun isLocked(): Boolean = state.first() == LockedModeState.LOCKED

    override val hasPin: Flow<Boolean> = dataStore.lockedMode.map { it.pin.isNotBlank() }

    override suspend fun configure(pin: String): PinResult {
        if (!isValidLockedModePin(pin)) return PinResult.InvalidPin
        dataStore.configureLockedMode(pin)
        return PinResult.Success
    }

    override suspend fun unlock(pin: String?): PinResult {
        val record = dataStore.lockedMode.first()
        val result = if (record.pin.isBlank()) PinResult.Success else verify(pin.orEmpty())
        if (result == PinResult.Success) dataStore.setLockedModeActive(false)
        return result
    }

    override suspend fun removePin() {
        dataStore.removeLockedModePin()
    }

    override suspend fun verify(pin: String): PinResult {
        val record = dataStore.lockedMode.first()
        if (record.pin.isBlank()) return PinResult.NotConfigured
        return if (isValidLockedModePin(pin) && pin == record.pin) {
            PinResult.Success
        } else PinResult.InvalidPin
    }
}

internal fun deriveLockedModeState(enabled: Boolean, active: Boolean): LockedModeState {
    return when {
        !enabled -> LockedModeState.DISABLED
        active -> LockedModeState.LOCKED
        else -> LockedModeState.READY
    }
}

internal fun isValidLockedModePin(pin: String) = pin.length == PIN_LENGTH && pin.all(Char::isDigit)
