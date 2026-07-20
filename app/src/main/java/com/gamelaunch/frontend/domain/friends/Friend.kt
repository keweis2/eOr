package com.gamelaunch.frontend.domain.friends

enum class FriendStatus {
    /** I added them; waiting for them to add me back. */
    PENDING_OUT,
    /** They added me; waiting for me to accept. */
    PENDING_IN,
    /** Both sides added each other — profiles sync. */
    ACTIVE
}

/**
 * A friend (or pending request). Profile fields are the last snapshot received over P2P sync and may
 * be stale; [profileUpdatedAt] is the friend's own timestamp, [lastSyncedAt] is when we last read it.
 */
data class Friend(
    val deviceId: String,
    val displayName: String,
    val status: FriendStatus,
    val lastPlayed: LastPlayed?,
    val ra: RaInfo?,
    val profileUpdatedAt: Long?,
    val lastSyncedAt: Long?
)
