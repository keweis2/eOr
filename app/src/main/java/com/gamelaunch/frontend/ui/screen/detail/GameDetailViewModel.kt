package com.gamelaunch.frontend.ui.screen.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamelaunch.frontend.domain.model.Game
import com.gamelaunch.frontend.domain.model.GameMedia
import com.gamelaunch.frontend.domain.repository.GameRepository
import com.gamelaunch.frontend.domain.repository.MediaRepository
import com.gamelaunch.frontend.domain.repository.SettingsRepository
import com.gamelaunch.frontend.domain.lockedmode.LockedModeRepository
import com.gamelaunch.frontend.domain.lockedmode.LockedModeState
import com.gamelaunch.frontend.domain.usecase.LaunchGameUseCase
import com.gamelaunch.frontend.ui.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
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
    val removed: Boolean = false,
    val lockedModeState: LockedModeState? = null,
    val isSavingLockedModeAvailability: Boolean = false,
    val availabilityFeedback: String? = null,
    val unavailableWhileLocked: Boolean = false
)

@HiltViewModel
class GameDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val gameRepository: GameRepository,
    private val mediaRepository: MediaRepository,
    private val settingsRepository: SettingsRepository,
    private val launchGameUseCase: LaunchGameUseCase,
    private val lockedModeRepository: LockedModeRepository
) : ViewModel() {

    private val gameId: Long = checkNotNull(savedStateHandle[Screen.GameDetail.ARG_GAME_ID])

    private val _uiState = MutableStateFlow(GameDetailUiState())
    val uiState: StateFlow<GameDetailUiState> = _uiState

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val lockState = lockedModeRepository.state.first()
            val game = gameRepository.getGameById(gameId)
            if (lockState == LockedModeState.LOCKED && game?.isAvailableInLockedMode != true) {
                _uiState.update { it.copy(isLoading = false, lockedModeState = lockState, unavailableWhileLocked = true) }
                return@launch
            }
            val media = mediaRepository.getMediaForGame(gameId)
            _uiState.update {
                it.copy(
                    game = game,
                    media = media,
                    isFavorite = game?.isFavorite ?: false,
                    isLoading = false,
                    lockedModeState = lockState
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
        viewModelScope.launch {
            lockedModeRepository.state.collect { lockState ->
                _uiState.update { state ->
                    if (lockState == LockedModeState.LOCKED && state.game?.isAvailableInLockedMode == false) {
                        state.copy(
                            game = null,
                            media = null,
                            shouldPlayVideo = false,
                            lockedModeState = lockState,
                            unavailableWhileLocked = true
                        )
                    } else state.copy(lockedModeState = lockState)
                }
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

    fun toggleLockedModeAvailability() {
        val state = _uiState.value
        val game = state.game ?: return
        if (state.lockedModeState != LockedModeState.READY || state.isSavingLockedModeAvailability) return
        val available = !game.isAvailableInLockedMode
        _uiState.update {
            it.copy(
                game = game.copy(isAvailableInLockedMode = available),
                isSavingLockedModeAvailability = true,
                availabilityFeedback = null
            )
        }
        viewModelScope.launch {
            runCatching { gameRepository.setAvailableInLockedMode(game.id, available) }
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isSavingLockedModeAvailability = false,
                            availabilityFeedback = if (available) "Available in Locked Mode" else "Hidden in Locked Mode"
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            game = game,
                            isSavingLockedModeAvailability = false,
                            availabilityFeedback = error.message ?: "Couldn't update Locked Mode availability"
                        )
                    }
                }
        }
    }

    /**
     * Remove this game from the library and remember its path so a rescan won't re-add it.
     * Works for both ROM games and Android-category games (whose path is "package:<pkg>").
     */
    fun removeFromLibrary() {
        val game = _uiState.value.game ?: return
        viewModelScope.launch {
            if (lockedModeRepository.isLocked()) return@launch
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
