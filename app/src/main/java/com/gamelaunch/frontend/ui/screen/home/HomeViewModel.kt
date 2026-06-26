package com.gamelaunch.frontend.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamelaunch.frontend.domain.model.Game
import com.gamelaunch.frontend.domain.model.GameMedia
import com.gamelaunch.frontend.domain.platform.PlatformDefinitions
import com.gamelaunch.frontend.domain.repository.GameRepository
import com.gamelaunch.frontend.domain.repository.MediaRepository
import com.gamelaunch.frontend.domain.repository.SettingsRepository
import com.gamelaunch.frontend.ui.theme.LayoutMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val platforms: List<String> = emptyList(),
    val selectedPlatform: String? = null,
    val games: List<Game> = emptyList(),
    val selectedGameIndex: Int = 0,
    val selectedGameMedia: GameMedia? = null,
    val shouldPlayVideo: Boolean = false,
    val layoutMode: LayoutMode = LayoutMode.CAROUSEL,
    val videoMuted: Boolean = true,
    val videoDelayMs: Long = 1500L,
    val isLoading: Boolean = true
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val gameRepository: GameRepository,
    private val mediaRepository: MediaRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    private var videoDelayJob: Job? = null

    init {
        observePlatforms()
        observeSettings()
    }

    private fun observePlatforms() {
        viewModelScope.launch {
            gameRepository.getDistinctPlatformIds().collect { platformIds ->
                val displayNames = platformIds.map { id ->
                    PlatformDefinitions.byId[id]?.displayName ?: id.uppercase()
                }
                _uiState.update { state ->
                    state.copy(
                        platforms = platformIds,
                        selectedPlatform = state.selectedPlatform ?: platformIds.firstOrNull(),
                        isLoading = false
                    )
                }
                loadGamesForPlatform(_uiState.value.selectedPlatform)
            }
        }
    }

    private fun observeSettings() {
        viewModelScope.launch {
            combine(
                settingsRepository.layoutMode,
                settingsRepository.videoMuted,
                settingsRepository.videoAutoplayDelayMs
            ) { layout, muted, delay ->
                Triple(layout, muted, delay)
            }.collect { (layout, muted, delay) ->
                _uiState.update { it.copy(layoutMode = layout, videoMuted = muted, videoDelayMs = delay) }
            }
        }
    }

    private var gamesJob: Job? = null

    private fun loadGamesForPlatform(platformId: String?) {
        gamesJob?.cancel()
        if (platformId == null) return
        gamesJob = viewModelScope.launch {
            gameRepository.getGamesByPlatform(platformId).collect { games ->
                _uiState.update { state ->
                    state.copy(
                        games = games,
                        selectedGameIndex = 0,
                        shouldPlayVideo = false
                    )
                }
                if (games.isNotEmpty()) loadMediaForGame(games[0].id)
            }
        }
    }

    fun selectPlatform(platformId: String) {
        videoDelayJob?.cancel()
        _uiState.update { it.copy(selectedPlatform = platformId, shouldPlayVideo = false) }
        loadGamesForPlatform(platformId)
    }

    fun onGameSelected(index: Int) {
        val games = _uiState.value.games
        if (index !in games.indices) return

        videoDelayJob?.cancel()
        _uiState.update { it.copy(selectedGameIndex = index, shouldPlayVideo = false) }

        loadMediaForGame(games[index].id)

        videoDelayJob = viewModelScope.launch {
            delay(_uiState.value.videoDelayMs)
            _uiState.update { it.copy(shouldPlayVideo = true) }
        }
    }

    private fun loadMediaForGame(gameId: Long) {
        viewModelScope.launch {
            val media = mediaRepository.getMediaForGame(gameId)
            _uiState.update { it.copy(selectedGameMedia = media) }
        }
    }

    fun toggleLayoutMode() {
        val next = if (_uiState.value.layoutMode == LayoutMode.CAROUSEL) LayoutMode.GRID else LayoutMode.CAROUSEL
        viewModelScope.launch { settingsRepository.setLayoutMode(next) }
    }

    fun toggleMute() {
        _uiState.update { it.copy(videoMuted = !it.videoMuted) }
        viewModelScope.launch {
            settingsRepository.setVideoMuted(_uiState.value.videoMuted)
        }
    }
}
