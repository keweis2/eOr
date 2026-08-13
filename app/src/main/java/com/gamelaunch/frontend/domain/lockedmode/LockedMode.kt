package com.gamelaunch.frontend.domain.lockedmode

import kotlinx.coroutines.flow.Flow

const val UNKNOWN_BOOT_COUNT = -1

enum class LockedModeState { DISABLED, READY, LOCKED }

sealed interface PinResult {
    data object Success : PinResult
    data object InvalidPin : PinResult
    data object NotConfigured : PinResult
}

fun PinResult.message(): String? = when (this) {
    PinResult.Success -> null
    PinResult.InvalidPin -> "Incorrect PIN"
    PinResult.NotConfigured -> "No PIN is configured"
}

interface LockedModeRepository {
    val state: Flow<LockedModeState>
    val hasPin: Flow<Boolean>
    val blockSystemNavigation: Flow<Boolean>

    suspend fun setEnabled(enabled: Boolean)
    suspend fun activate()
    suspend fun isLocked(): Boolean
    suspend fun configure(pin: String): PinResult
    suspend fun verify(pin: String): PinResult
    suspend fun unlock(pin: String? = null): PinResult
    suspend fun removePin()
    suspend fun setBlockSystemNavigation(enabled: Boolean)
}
