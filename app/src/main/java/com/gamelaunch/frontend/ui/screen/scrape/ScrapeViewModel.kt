package com.gamelaunch.frontend.ui.screen.scrape

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamelaunch.frontend.domain.usecase.BatchScrapeState
import com.gamelaunch.frontend.domain.usecase.BatchScrapeUseCase
import com.gamelaunch.frontend.domain.usecase.ScrapeResult
import com.gamelaunch.frontend.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ScrapeUiState(
    val batchState: BatchScrapeState? = null,
    val isRunning: Boolean = false,
    val isConfigured: Boolean = false
)

@HiltViewModel
class ScrapeViewModel @Inject constructor(
    private val batchScrapeUseCase: BatchScrapeUseCase,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScrapeUiState())
    val uiState: StateFlow<ScrapeUiState> = _uiState

    private var scrapeJob: Job? = null

    // Whether the user has ScreenScraper credentials set (best results).
    // Scraping still runs without them via libretro + LaunchBox fallbacks.
    val hasSsCredentials: kotlinx.coroutines.flow.StateFlow<Boolean> =
        settingsRepository.scraperConfig
            .map { it.isConfigured }
            .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), false)

    init {
        // Always considered configured — libretro + LaunchBox work without SS credentials.
        _uiState.update { it.copy(isConfigured = true) }
    }

    fun startScrape() {
        viewModelScope.launch {
            // Config may be empty — ScrapeGameUseCase falls back to LaunchBox if SS not set
            val config = settingsRepository.scraperConfig.firstOrNull()
                ?: return@launch

            _uiState.update { it.copy(isRunning = true) }
            scrapeJob = launch {
                try {
                    batchScrapeUseCase(config).collect { state ->
                        _uiState.update { it.copy(batchState = state) }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    // Last-resort guard so an unexpected failure (e.g. disk full) can't crash the app.
                    _uiState.update {
                        it.copy(batchState = (it.batchState ?: com.gamelaunch.frontend.domain.usecase.BatchScrapeState(0, 0, 0, 0, 0))
                            .copy(isFinished = true, storageFull = true))
                    }
                }
            }
            scrapeJob?.join()
            _uiState.update { it.copy(isRunning = false) }
        }
    }

    fun cancelScrape() {
        scrapeJob?.cancel()
        _uiState.update { it.copy(isRunning = false) }
    }

    override fun onCleared() {
        super.onCleared()
        scrapeJob?.cancel()
    }
}
