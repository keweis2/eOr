package com.gamelaunch.frontend.data.repository

import com.gamelaunch.frontend.data.db.dao.FriendDao
import com.gamelaunch.frontend.data.db.entity.FriendEntity
import com.gamelaunch.frontend.data.friends.FriendProfileStore
import com.gamelaunch.frontend.data.sync.SyncEngineManager
import com.gamelaunch.frontend.data.sync.SyncthingController
import com.gamelaunch.frontend.domain.friends.Friend
import com.gamelaunch.frontend.domain.friends.FriendCode
import com.gamelaunch.frontend.domain.friends.FriendProfile
import com.gamelaunch.frontend.domain.friends.FriendStatus
import com.gamelaunch.frontend.domain.friends.LastPlayed
import com.gamelaunch.frontend.domain.friends.RaInfo
import com.gamelaunch.frontend.domain.friends.RandomHandle
import com.gamelaunch.frontend.domain.repository.AddFriendResult
import com.gamelaunch.frontend.domain.repository.FriendRepository
import com.gamelaunch.frontend.domain.repository.GameRepository
import com.gamelaunch.frontend.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FriendRepositoryImpl @Inject constructor(
    private val friendDao: FriendDao,
    private val syncthingController: SyncthingController,
    private val syncEngineManager: SyncEngineManager,
    private val profileStore: FriendProfileStore,
    private val settingsRepository: SettingsRepository,
    private val gameRepository: GameRepository
) : FriendRepository {

    override fun observeFriends(): Flow<List<Friend>> =
        friendDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun engineSupported(): Boolean = syncthingController.isSupported()

    override suspend fun myDisplayName(): String {
        val current = settingsRepository.friendDisplayName.first()
        if (current.isNotBlank()) return current
        val generated = RandomHandle.generate()
        settingsRepository.setFriendDisplayName(generated)
        return generated
    }

    override suspend fun setMyDisplayName(name: String) {
        val trimmed = name.trim().ifBlank { RandomHandle.generate() }
        settingsRepository.setFriendDisplayName(trimmed)
        publishMyProfile()
    }

    override suspend fun myDeviceId(): String? = syncthingController.awaitDeviceId()

    override suspend fun myShareLink(): String? =
        myDeviceId()?.let { FriendCode.buildLink(it, myDisplayName()) }

    override suspend fun addFriend(codeOrLink: String): AddFriendResult {
        val parsed = FriendCode.parse(codeOrLink) ?: return AddFriendResult.InvalidCode
        val myId = myDeviceId() ?: return AddFriendResult.EngineUnavailable
        if (parsed.deviceId.equals(myId, ignoreCase = true)) return AddFriendResult.Self

        val existing = friendDao.getByDeviceId(parsed.deviceId)
        if (existing?.status == FriendStatus.ACTIVE.name) return AddFriendResult.AlreadyFriend

        // They may have already requested me (PENDING_IN) — adding them back completes the friendship.
        val nowActive = existing?.status == FriendStatus.PENDING_IN.name
        wirePeer(parsed.deviceId)
        upsert(
            deviceId = parsed.deviceId,
            displayName = parsed.displayName ?: existing?.displayName ?: "Pending friend",
            status = if (nowActive) FriendStatus.ACTIVE else FriendStatus.PENDING_OUT,
            existing = existing
        )
        publishMyProfile()
        return AddFriendResult.Requested(parsed.displayName)
    }

    override suspend fun acceptRequest(deviceId: String) {
        val existing = friendDao.getByDeviceId(deviceId)
        wirePeer(deviceId)
        upsert(deviceId, existing?.displayName ?: "Friend", FriendStatus.ACTIVE, existing)
        publishMyProfile()
    }

    override suspend fun declineRequest(deviceId: String) {
        val myId = myDeviceId()
        if (myId != null) syncthingController.removeFriendPeer(deviceId, myId)
        friendDao.delete(deviceId)
    }

    override suspend fun removeFriend(deviceId: String) {
        val myId = myDeviceId()
        if (myId != null) syncthingController.removeFriendPeer(deviceId, myId)
        friendDao.delete(deviceId)
    }

    override suspend fun publishMyProfile() {
        if (!settingsRepository.friendsEnabled.first()) {
            profileStore.clearMyProfile()
            return
        }
        val myId = myDeviceId() ?: return
        syncthingController.configureProfileFolder(myId, profileStore.outDir.absolutePath)

        val lastPlayed = if (settingsRepository.friendShareLastPlayed.first()) {
            gameRepository.getRecentlyPlayed(1).first().firstOrNull()?.let { g ->
                LastPlayed(g.title, g.platformId, g.md5, g.lastPlayedMs ?: System.currentTimeMillis())
            }
        } else null

        val raUsername = settingsRepository.raUsername.first()
        val ra = if (settingsRepository.friendShareRa.first() && raUsername.isNotBlank()) {
            RaInfo(raUsername, settingsRepository.raPoints.first(), settingsRepository.raSoftcorePoints.first())
        } else null

        val profile = FriendProfile(myId, myDisplayName(), lastPlayed, ra, System.currentTimeMillis())
        profileStore.writeMyProfile(profile)
    }

    override suspend fun refreshFriends() {
        val now = System.currentTimeMillis()
        // Incoming requests: devices that tried to connect but we haven't added yet.
        val existingIds = friendDao.getAll().associateBy { it.deviceId }
        syncthingController.pendingFriendRequests().forEach { pid ->
            val existing = existingIds[pid]
            if (existing == null) {
                friendDao.upsert(
                    FriendEntity(pid, "Friend request", FriendStatus.PENDING_IN.name, addedAt = now)
                )
            }
        }
        // Inbound profiles: if we can read a friend's profile, they've added us back → ACTIVE.
        friendDao.getAll().forEach { row ->
            val profile = profileStore.readFriendProfile(row.deviceId) ?: return@forEach
            friendDao.upsert(
                row.copy(
                    displayName = profile.displayName,
                    status = FriendStatus.ACTIVE.name,
                    lastPlayedTitle = profile.lastPlayed?.title,
                    lastPlayedPlatform = profile.lastPlayed?.platform,
                    lastPlayedMd5 = profile.lastPlayed?.md5,
                    lastPlayedAt = profile.lastPlayed?.at,
                    raUsername = profile.ra?.username,
                    raPoints = profile.ra?.points,
                    raSoftcorePoints = profile.ra?.softcorePoints,
                    profileUpdatedAt = profile.updatedAt,
                    lastSyncedAt = now
                )
            )
        }
    }

    override suspend fun setEnabled(enabled: Boolean) {
        settingsRepository.setFriendsEnabled(enabled)
        if (enabled) {
            syncEngineManager.ensureRunning()
            myDisplayName() // generate + persist a handle on first enable
            publishMyProfile()
        } else {
            syncthingController.teardownFriends()
            profileStore.clearMyProfile()
            syncEngineManager.refresh() // stop the daemon unless Save Sync still needs it
        }
    }

    /** Register the peer + isolated profile folders for a friendship (shared by request/accept). */
    private suspend fun wirePeer(friendDeviceId: String) {
        val myId = myDeviceId() ?: return
        syncthingController.configureProfileFolder(myId, profileStore.outDir.absolutePath)
        syncthingController.addFriendPeer(
            friendDeviceId = friendDeviceId,
            myDeviceId = myId,
            myProfilePath = profileStore.outDir.absolutePath,
            inboundPath = profileStore.inDir(friendDeviceId).absolutePath
        )
    }

    private suspend fun upsert(
        deviceId: String,
        displayName: String,
        status: FriendStatus,
        existing: FriendEntity?
    ) {
        friendDao.upsert(
            (existing ?: FriendEntity(deviceId, displayName, status.name, addedAt = System.currentTimeMillis()))
                .copy(deviceId = deviceId, displayName = displayName, status = status.name)
        )
    }

    private fun FriendEntity.toDomain() = Friend(
        deviceId = deviceId,
        displayName = displayName,
        status = runCatching { FriendStatus.valueOf(status) }.getOrDefault(FriendStatus.PENDING_OUT),
        lastPlayed = lastPlayedTitle?.let {
            LastPlayed(it, lastPlayedPlatform ?: "", lastPlayedMd5, lastPlayedAt ?: 0L)
        },
        ra = raUsername?.let { RaInfo(it, raPoints ?: 0, raSoftcorePoints ?: 0) },
        profileUpdatedAt = profileUpdatedAt,
        lastSyncedAt = lastSyncedAt
    )
}
