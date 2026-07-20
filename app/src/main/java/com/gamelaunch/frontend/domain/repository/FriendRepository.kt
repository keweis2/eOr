package com.gamelaunch.frontend.domain.repository

import com.gamelaunch.frontend.domain.friends.Friend
import kotlinx.coroutines.flow.Flow

/** Result of trying to add a friend from a code/link. */
sealed interface AddFriendResult {
    data class Requested(val displayName: String?) : AddFriendResult
    object InvalidCode : AddFriendResult
    object EngineUnavailable : AddFriendResult
    object AlreadyFriend : AddFriendResult
    object Self : AddFriendResult
}

/**
 * The Friends feature's data + P2P orchestration boundary. All state is on-device; identity is the
 * Syncthing device id. No accounts, no server.
 */
interface FriendRepository {
    /** All friends and pending requests (ACTIVE + PENDING_*), reactive from the local DB. */
    fun observeFriends(): Flow<List<Friend>>

    /** True once the Friends feature is usable on this device (the sync engine is supported). */
    fun engineSupported(): Boolean

    /** My display name, generating and persisting a random one on first use if unset. */
    suspend fun myDisplayName(): String
    suspend fun setMyDisplayName(name: String)

    /** My friend identity (Syncthing device id); null if the engine hasn't come up yet. */
    suspend fun myDeviceId(): String?
    /** Shareable eor:// link for my identity, or null if the engine isn't up. */
    suspend fun myShareLink(): String?

    /** Add a friend from a scanned/pasted/deep-linked code or link (mutual — they must add back). */
    suspend fun addFriend(codeOrLink: String): AddFriendResult

    /** Accept an incoming request (they added me); completes the friendship. */
    suspend fun acceptRequest(deviceId: String)
    suspend fun declineRequest(deviceId: String)
    suspend fun removeFriend(deviceId: String)

    /** Recompute and write my profile.json (last-played + RA), if the feature is enabled. */
    suspend fun publishMyProfile()

    /** Read friends' inbound profiles + incoming requests into the local DB. */
    suspend fun refreshFriends()

    /** Enable/disable the whole feature: (de)registers the profile folder and tears down sharing. */
    suspend fun setEnabled(enabled: Boolean)
}
