package com.gamelaunch.frontend.domain.lockedmode

import kotlinx.coroutines.flow.Flow

enum class LockedModeState { UNCONFIGURED, READY, LOCKED }

sealed interface PinResult {
    data object Success : PinResult
    data object InvalidPin : PinResult
    data object NotConfigured : PinResult
}

fun PinResult.message(): String? = when (this) {
    PinResult.Success -> null
    PinResult.InvalidPin -> "Incorrect PIN"
    PinResult.NotConfigured -> "Locked Mode is not configured"
}

interface LockedModeRepository {
    val state: Flow<LockedModeState>

    suspend fun configure(pin: String): PinResult
    suspend fun activate()
    suspend fun verify(pin: String): PinResult
    suspend fun unlock(pin: String): PinResult
    suspend fun changePin(currentPin: String, newPin: String): PinResult
    suspend fun remove(currentPin: String): PinResult
    suspend fun isLocked(): Boolean
}
