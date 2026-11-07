package com.gamelaunch.frontend.ui.screen.retroachievements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamelaunch.frontend.domain.model.RaProfile
import com.gamelaunch.frontend.domain.model.RaRecentGame
import com.gamelaunch.frontend.domain.model.RaSession
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
    /** Username + password sign-in only: points + avatar, no rank or recently-played list. */
    data class LoadedBasic(val profile: RaProfile) : RaScreenState
    /** Web API key present: full profile + recently-played games with completion. */
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

    // Tracks the username|apiKey combo currently loaded into the full dashboard so a
    // points-only change (from a token refresh) doesn't trigger a redundant Web API reload.
    private var loadedDashboardKey: String? = null

    private data class RaCreds(
        val username: String,
        val token: String,
        val apiKey: String,
        val points: Int,
        val softcorePoints: Int
    )

    init {
        viewModelScope.launch {
            combine(
                settingsRepository.raUsername,
                settingsRepository.raToken,
                settingsRepository.raApiKey,
                settingsRepository.raPoints,
                settingsRepository.raSoftcorePoints
            ) { username, token, apiKey, points, softcore ->
                RaCreds(username, token, apiKey, points, softcore)
            }.collect { creds -> applyCreds(creds) }
        }
    }

    private suspend fun applyCreds(creds: RaCreds) {
        // Requires a username + token, i.e. the user has signed in with their password.
        if (creds.username.isBlank() || creds.token.isBlank()) {
            _state.value = RaScreenState.NotConfigured
            loadedDashboardKey = null
            return
        }

        if (creds.apiKey.isNotBlank()) {
            val key = "${creds.username}|${creds.apiKey}"
            if (key != loadedDashboardKey) loadDashboard(creds.username, creds.apiKey, key)
        } else {
            // No key — show the basic profile built from the cached Connect-login points.
            loadedDashboardKey = null
            _state.value = RaScreenState.LoadedBasic(
                RaSession(creds.username, creds.token, creds.points, creds.softcorePoints).toProfile()
            )
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val username = settingsRepository.raUsername.first()
            val token    = settingsRepository.raToken.first()
            val apiKey   = settingsRepository.raApiKey.first()
            if (username.isBlank() || token.isBlank()) return@launch

            if (apiKey.isNotBlank()) {
                loadDashboard(username, apiKey, "$username|$apiKey")
            } else {
                // Re-validate the token to pull fresh point totals; persisting re-emits the flow.
                raRepository.refreshSession(username, token).onSuccess { session ->
                    settingsRepository.setRaSession(
                        session.username, session.token, session.points, session.softcorePoints
                    )
                }
            }
        }
    }

    private suspend fun loadDashboard(username: String, apiKey: String, key: String) {
        _state.value = RaScreenState.Loading
        loadedDashboardKey = key

        val profileResult = raRepository.getUserProfile(username, apiKey)
        val gamesResult   = raRepository.getRecentlyPlayed(username, apiKey, 20)

        val profile = profileResult.getOrNull()
        val games   = gamesResult.getOrNull()

        if (profile != null && games != null) {
            _state.value = RaScreenState.Loaded(profile, games)
        } else {
            val err = profileResult.exceptionOrNull() ?: gamesResult.exceptionOrNull()
            _state.value = RaScreenState.Error(err?.message ?: "Failed to load achievements data")
            loadedDashboardKey = null
        }
    }
}
