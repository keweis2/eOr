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
        deriveLockedModeState(record.pin, record.active)
    }

    override suspend fun configure(pin: String): PinResult {
        if (!isValidLockedModePin(pin)) return PinResult.InvalidPin
        dataStore.configureLockedMode(pin)
        return PinResult.Success
    }

    override suspend fun activate() {
        if (state.first() == LockedModeState.UNCONFIGURED) return
        dataStore.setLockedModeActive(true)
    }

    override suspend fun unlock(pin: String): PinResult {
        val result = verify(pin)
        if (result == PinResult.Success) dataStore.setLockedModeActive(false)
        return result
    }

    override suspend fun changePin(currentPin: String, newPin: String): PinResult {
        if (!isValidLockedModePin(newPin)) return PinResult.InvalidPin
        val result = verify(currentPin)
        if (result == PinResult.Success) dataStore.configureLockedMode(newPin)
        return result
    }

    override suspend fun remove(currentPin: String): PinResult {
        val result = verify(currentPin)
        if (result == PinResult.Success) dataStore.clearLockedMode()
        return result
    }

    override suspend fun isLocked(): Boolean = state.first() == LockedModeState.LOCKED

    override suspend fun verify(pin: String): PinResult {
        val record = dataStore.lockedMode.first()
        if (record.pin.isBlank()) return PinResult.NotConfigured
        return if (isValidLockedModePin(pin) && pin == record.pin) {
            PinResult.Success
        } else PinResult.InvalidPin
    }
}

internal fun deriveLockedModeState(pin: String, active: Boolean): LockedModeState {
    val configured = pin.isNotBlank()
    return when {
        !configured -> LockedModeState.UNCONFIGURED
        active -> LockedModeState.LOCKED
        else -> LockedModeState.READY
    }
}

internal fun isValidLockedModePin(pin: String) = pin.length == PIN_LENGTH && pin.all(Char::isDigit)
