package com.gamelaunch.frontend.domain.friends

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds a friend code parsed from an incoming `eor://friend/...` deep link until the UI can show a
 * confirmation. Never auto-adds — the deep-link source is untrusted, so a user must explicitly accept.
 */
@Singleton
class PendingFriendLink @Inject constructor() {
    private val _pending = MutableStateFlow<FriendCode.Parsed?>(null)
    val pending: StateFlow<FriendCode.Parsed?> = _pending

    fun offer(parsed: FriendCode.Parsed) { _pending.value = parsed }
    fun clear() { _pending.value = null }
}
