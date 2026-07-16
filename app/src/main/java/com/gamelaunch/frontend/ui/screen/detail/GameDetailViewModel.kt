package com.gamelaunch.frontend.ui.screen.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamelaunch.frontend.domain.model.Game
import com.gamelaunch.frontend.domain.model.GameMedia
import com.gamelaunch.frontend.domain.repository.GameRepository
import com.gamelaunch.frontend.domain.repository.MediaRepository
import com.gamelaunch.frontend.domain.repository.SettingsRepository
import com.gamelaunch.frontend.domain.usecase.LaunchGameUseCase
import com.gamelaunch.frontend.ui.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GameDetailUiState(
    val game: Game? = null,
    val media: GameMedia? = null,
    val shouldPlayVideo: Boolean = false,
    val videoMuted: Boolean = true,
    val isFavorite: Boolean = false,
    val launchError: String? = null,
    val isLoading: Boolean = true,
    val removed: Boolean = false
)

@HiltViewModel
class GameDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val gameRepository: GameRepository,
    private val mediaRepository: MediaRepository,
    private val settingsRepository: SettingsRepository,
    private val launchGameUseCase: LaunchGameUseCase
) : ViewModel() {

    private val gameId: Long = checkNotNull(savedStateHandle[Screen.GameDetail.ARG_GAME_ID])

    private val _uiState = MutableStateFlow(GameDetailUiState())
    val uiState: StateFlow<GameDetailUiState> = _uiState

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val game = gameRepository.getGameById(gameId)
            val media = mediaRepository.getMediaForGame(gameId)
            _uiState.update {
                it.copy(
                    game = game,
                    media = media,
                    isFavorite = game?.isFavorite ?: false,
                    isLoading = false
                )
            }
            // Auto-play video after a brief delay
            delay(1500)
            _uiState.update { it.copy(shouldPlayVideo = true) }
        }
        viewModelScope.launch {
            settingsRepository.videoMuted.collect { muted ->
                _uiState.update { it.copy(videoMuted = muted) }
            }
        }
    }

    fun launchGame() {
        val game = _uiState.value.game ?: return
        viewModelScope.launch {
            launchGameUseCase(game).onFailure { e ->
                _uiState.update { it.copy(launchError = e.message) }
            }
        }
    }

    fun toggleFavorite() {
        val game = _uiState.value.game ?: return
        viewModelScope.launch {
            val newValue = !game.isFavorite
            gameRepository.setFavorite(game.id, newValue)
            _uiState.update { it.copy(isFavorite = newValue) }
        }
    }

    /**
     * Remove this game from the library and remember its path so a rescan won't re-add it.
     * Works for both ROM games and Android-category games (whose path is "package:<pkg>").
     */
    fun removeFromLibrary() {
        val game = _uiState.value.game ?: return
        viewModelScope.launch {
            settingsRepository.addExcludedPath(game.romPath)
            gameRepository.deleteGame(game.id)
            _uiState.update { it.copy(removed = true) }
        }
    }

    fun toggleMute() {
        viewModelScope.launch {
            val newMuted = !_uiState.value.videoMuted
            settingsRepository.setVideoMuted(newMuted)
            _uiState.update { it.copy(videoMuted = newMuted) }
        }
    }

    fun dismissError() = _uiState.update { it.copy(launchError = null) }
}
