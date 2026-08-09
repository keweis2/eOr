package com.gamelaunch.frontend.ui.lockedmode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamelaunch.frontend.domain.model.Game
import com.gamelaunch.frontend.domain.model.GameMedia
import com.gamelaunch.frontend.domain.repository.GameRepository
import com.gamelaunch.frontend.domain.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LockedModeGamesUiState(
    val games: List<Game> = emptyList(),
    val mediaByGameId: Map<Long, GameMedia> = emptyMap(),
    val savingGameIds: Set<Long> = emptySet(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class LockedModeGamesViewModel @Inject constructor(
    private val gameRepository: GameRepository,
    mediaRepository: MediaRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LockedModeGamesUiState())
    val uiState: StateFlow<LockedModeGamesUiState> = _uiState.asStateFlow()
    private val pendingValues = mutableMapOf<Long, Boolean>()

    init {
        viewModelScope.launch {
            mediaRepository.observeAllMedia().collect { media ->
                _uiState.update { it.copy(mediaByGameId = media) }
            }
        }
        viewModelScope.launch {
            gameRepository.getAllGames()
                .catch { failure ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = failure.message ?: "Could not load games",
                        )
                    }
                }
                .collect { games ->
                    val displayedGames = games
                        .map { game ->
                            pendingValues[game.id]?.let { game.copy(isAvailableInLockedMode = it) }
                                ?: game
                        }
                        .sortedWith(compareBy<Game> { it.title.lowercase() }.thenBy { it.title }.thenBy { it.id })
                    _uiState.update { it.copy(games = displayedGames, isLoading = false) }
                }
        }
    }

    fun setGameAllowed(gameId: Long, allowed: Boolean) {
        val game = _uiState.value.games.firstOrNull { it.id == gameId } ?: return
        if (gameId in _uiState.value.savingGameIds || game.isAvailableInLockedMode == allowed) return
        val wasAllowed = game.isAvailableInLockedMode
        pendingValues[gameId] = allowed
        _uiState.update { state ->
            state.copy(
                games = state.games.map { if (it.id == gameId) it.copy(isAvailableInLockedMode = allowed) else it },
                savingGameIds = state.savingGameIds + gameId,
                error = null,
            )
        }
        viewModelScope.launch {
            runCatching { gameRepository.setAvailableInLockedMode(gameId, allowed) }
                .onFailure { failure ->
                    pendingValues.remove(gameId)
                    _uiState.update { state ->
                        state.copy(
                            games = state.games.map {
                                if (it.id == gameId) it.copy(isAvailableInLockedMode = wasAllowed) else it
                            },
                            error = failure.message ?: "Could not save game permission",
                        )
                    }
                }
                .onSuccess { pendingValues.remove(gameId) }
            _uiState.update { it.copy(savingGameIds = it.savingGameIds - gameId) }
        }
    }
}
