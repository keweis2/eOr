package com.gamelaunch.frontend.domain.friends

import java.security.MessageDigest

/**
 * Deterministic Syncthing folder ids for the Friends feature. Both devices in a friendship derive the
 * same id for a given owner's profile folder purely from that owner's device id, so their send-only and
 * receive-only folders line up and sync. Pure (no Android deps) to keep it unit-testable.
 *
 * [PREFIX] intentionally does NOT start with "eor-", so Save Sync's broadcast folder-sharing never
 * touches friend folders and vice-versa.
 */
object FriendFolders {
    const val PREFIX = "friendsync-"

    fun profileFolderId(ownerDeviceId: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(ownerDeviceId.trim().uppercase().toByteArray())
        return PREFIX + digest.joinToString("") { "%02x".format(it) }.take(16)
    }
}
