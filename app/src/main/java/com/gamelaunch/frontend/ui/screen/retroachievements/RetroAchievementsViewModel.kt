package com.gamelaunch.frontend.ui.screen.retroachievements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamelaunch.frontend.domain.model.RaProfile
import com.gamelaunch.frontend.domain.model.RaRecentGame
import com.gamelaunch.frontend.domain.repository.RetroAchievementsRepository
import com.gamelaunch.frontend.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface RaScreenState {
    data object NotConfigured : RaScreenState
    data object Loading : RaScreenState
    data class Loaded(val profile: RaProfile, val recentGames: List<RaRecentGame>) : RaScreenState
    data class Error(val message: String) : RaScreenState
}

@HiltViewModel
class RetroAchievementsViewModel @Inject constructor(
    private val raRepository: RetroAchievementsRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _state = MutableStateFlow<RaScreenState>(RaScreenState.NotConfigured)
    val state: StateFlow<RaScreenState> = _state

    private var loadedUsername: String? = null

    init {
        viewModelScope.launch {
            combine(settingsRepository.raUsername, settingsRepository.raApiKey) { u, k -> u to k }
                .collect { (username, apiKey) ->
                    if (username.isBlank() || apiKey.isBlank()) {
                        _state.value = RaScreenState.NotConfigured
                        loadedUsername = null
                    } else if (username != loadedUsername) {
                        load(username, apiKey)
                    }
                }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val username = settingsRepository.raUsername.first()
            val apiKey   = settingsRepository.raApiKey.first()
            if (username.isNotBlank() && apiKey.isNotBlank()) {
                loadedUsername = null
                load(username, apiKey)
            }
        }
    }

    private suspend fun load(username: String, apiKey: String) {
        _state.value = RaScreenState.Loading
        loadedUsername = username

        val profileResult = raRepository.getUserProfile(username, apiKey)
        val gamesResult   = raRepository.getRecentlyPlayed(username, apiKey, 20)

        val profile = profileResult.getOrNull()
        val games   = gamesResult.getOrNull()

        if (profile != null && games != null) {
            _state.value = RaScreenState.Loaded(profile, games)
        } else {
            val err = profileResult.exceptionOrNull() ?: gamesResult.exceptionOrNull()
            _state.value = RaScreenState.Error(err?.message ?: "Failed to load RetroAchievements data")
            loadedUsername = null
        }
    }
}
