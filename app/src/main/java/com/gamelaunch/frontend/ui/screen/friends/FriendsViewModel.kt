package com.gamelaunch.frontend.ui.screen.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamelaunch.frontend.data.friends.NearbyBeacon
import com.gamelaunch.frontend.domain.friends.Friend
import com.gamelaunch.frontend.domain.friends.FriendCode
import com.gamelaunch.frontend.domain.friends.FriendStatus
import com.gamelaunch.frontend.domain.friends.PendingFriendLink
import com.gamelaunch.frontend.domain.repository.AddFriendResult
import com.gamelaunch.frontend.domain.repository.FriendRepository
import com.gamelaunch.frontend.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FriendsViewModel @Inject constructor(
    private val friendRepository: FriendRepository,
    private val settingsRepository: SettingsRepository,
    private val pendingFriendLink: PendingFriendLink,
    private val nearbyBeacon: NearbyBeacon
) : ViewModel() {

    data class UiState(
        val enabled: Boolean = false,
        val engineSupported: Boolean = true,
        val engineStarting: Boolean = false,
        val myDeviceId: String? = null,
        val myShareLink: String? = null,
        val displayName: String = "",
        val active: List<Friend> = emptyList(),
        val incoming: List<Friend> = emptyList(),
        val outgoing: List<Friend> = emptyList(),
        val status: String? = null,
        val pendingLink: FriendCode.Parsed? = null,
        val scanningNearby: Boolean = false,
        val nearby: List<NearbyBeacon.NearbyPeer> = emptyList()
    )

    private val _uiState = MutableStateFlow(UiState(engineSupported = friendRepository.engineSupported()))
    val uiState: StateFlow<UiState> = _uiState

    private val friends: StateFlow<List<Friend>> =
        friendRepository.observeFriends().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        // Reactive: friends list, enabled flag, display name.
        combine(
            friends,
            settingsRepository.friendsEnabled,
            settingsRepository.friendDisplayName
        ) { list, enabled, name ->
            Triple(list, enabled, name)
        }.onEach { (list, enabled, name) ->
            _uiState.update {
                it.copy(
                    enabled = enabled,
                    displayName = name,
                    active = list.filter { f -> f.status == FriendStatus.ACTIVE },
                    incoming = list.filter { f -> f.status == FriendStatus.PENDING_IN },
                    outgoing = list.filter { f -> f.status == FriendStatus.PENDING_OUT }
                )
            }
        }.launchIn(viewModelScope)

        pendingFriendLink.pending
            .onEach { parsed -> _uiState.update { it.copy(pendingLink = parsed) } }
            .launchIn(viewModelScope)

        nearbyBeacon.peers
            .onEach { peers -> _uiState.update { it.copy(nearby = peers) } }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            if (settingsRepository.friendsEnabled.first() && friendRepository.engineSupported()) {
                bringUpEngine()
            }
        }
    }

    private suspend fun bringUpEngine() {
        _uiState.update { it.copy(engineStarting = true) }
        val id = friendRepository.myDeviceId()
        val link = friendRepository.myShareLink()
        _uiState.update { it.copy(engineStarting = false, myDeviceId = id, myShareLink = link) }
        friendRepository.publishMyProfile()
        friendRepository.refreshFriends()
    }

    fun setEnabled(on: Boolean) {
        _uiState.update { it.copy(status = null) }
        viewModelScope.launch {
            friendRepository.setEnabled(on)
            if (on) bringUpEngine()
            else _uiState.update { it.copy(myDeviceId = null, myShareLink = null) }
        }
    }

    fun saveDisplayName(name: String) {
        viewModelScope.launch {
            friendRepository.setMyDisplayName(name)
            _uiState.update { it.copy(myShareLink = friendRepository.myShareLink()) }
        }
    }

    fun addFriend(codeOrLink: String) {
        viewModelScope.launch {
            val msg = when (val r = friendRepository.addFriend(codeOrLink)) {
                is AddFriendResult.Requested ->
                    "Request sent to ${r.displayName ?: "friend"} — you'll connect once they add you back."
                AddFriendResult.InvalidCode -> "That doesn't look like a valid friend code."
                AddFriendResult.EngineUnavailable -> "The connection engine isn't ready yet — try again in a moment."
                AddFriendResult.AlreadyFriend -> "You're already friends with them."
                AddFriendResult.Self -> "That's your own friend code."
            }
            _uiState.update { it.copy(status = msg) }
        }
    }

    fun acceptRequest(deviceId: String) = viewModelScope.launch { friendRepository.acceptRequest(deviceId) }
    fun declineRequest(deviceId: String) = viewModelScope.launch { friendRepository.declineRequest(deviceId) }
    fun removeFriend(deviceId: String) = viewModelScope.launch { friendRepository.removeFriend(deviceId) }

    fun refresh() = viewModelScope.launch { friendRepository.refreshFriends() }

    /** Start broadcasting/listening on the LAN so nearby devices can be tapped to add. */
    fun startNearby() {
        if (_uiState.value.scanningNearby) return
        viewModelScope.launch {
            val id = friendRepository.myDeviceId() ?: return@launch
            nearbyBeacon.start(id, friendRepository.myDisplayName())
            _uiState.update { it.copy(scanningNearby = true) }
        }
    }

    fun stopNearby() {
        nearbyBeacon.stop()
        _uiState.update { it.copy(scanningNearby = false, nearby = emptyList()) }
    }

    fun addNearby(peer: NearbyBeacon.NearbyPeer) {
        addFriend(peer.deviceId)
    }

    override fun onCleared() {
        nearbyBeacon.stop()
        super.onCleared()
    }

    fun confirmPendingLink() {
        val parsed = _uiState.value.pendingLink ?: return
        pendingFriendLink.clear()
        addFriend(FriendCode.buildLink(parsed.deviceId, parsed.displayName))
    }

    fun dismissPendingLink() {
        pendingFriendLink.clear()
        _uiState.update { it.copy(pendingLink = null) }
    }

    fun clearStatus() = _uiState.update { it.copy(status = null) }
}
