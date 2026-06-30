package com.gamelaunch.frontend.ui.screen.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.imageLoader
import coil.request.ImageRequest
import com.gamelaunch.frontend.domain.model.Game
import com.gamelaunch.frontend.domain.model.GameMedia
import com.gamelaunch.frontend.domain.platform.PlatformDefinitions
import com.gamelaunch.frontend.domain.platform.sortedBySystems
import com.gamelaunch.frontend.domain.repository.GameRepository
import com.gamelaunch.frontend.domain.repository.MediaRepository
import com.gamelaunch.frontend.domain.repository.SettingsRepository
import com.gamelaunch.frontend.ui.theme.LayoutMode
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
import java.io.File
import javax.inject.Inject

enum class TopTab { GAMES, RECENTLY_PLAYED, APPS, RETROACHIEVEMENTS }

data class HomeUiState(
    val topTab: TopTab = TopTab.GAMES,
    val gameViewActive: Boolean = false,      // Games tab: false = system grid, true = game UI
    val platforms: List<String> = emptyList(),
    val platformCounts: Map<String, Int> = emptyMap(),
    val systemPreviewArt: List<String> = emptyList(),  // box art for the focused system card
    val selectedPlatform: String? = null,
    val showRecentlyPlayed: Boolean = true,
    val showRetroAchievements: Boolean = true,
    val recentlyPlayed: List<Game> = emptyList(),
    val games: List<Game> = emptyList(),
    val selectedGameIndex: Int = 0,
    val selectedGameMedia: GameMedia? = null,
    val mediaForGames: Map<Long, GameMedia> = emptyMap(),
    val shouldPlayVideo: Boolean = false,
    val layoutMode: LayoutMode = LayoutMode.CAROUSEL,
    val videoMuted: Boolean = true,
    val videoDelayMs: Long = 1500L,
    val isLoading: Boolean = true
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
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
        observeAllMedia()
        observeRecentlyPlayed()
    }

    private fun observeRecentlyPlayed() {
        viewModelScope.launch {
            gameRepository.getRecentlyPlayed(30).collect { games ->
                _uiState.update { it.copy(recentlyPlayed = games) }
            }
        }
    }

    private var lastPlatformIdSet: Set<String> = emptySet()

    private fun observePlatforms() {
        viewModelScope.launch {
            combine(
                gameRepository.getDistinctPlatformIds(),
                gameRepository.getPlatformCounts(),
                settingsRepository.systemSort
            ) { ids, counts, sorts -> Triple(ids, counts, sorts) }
                .collect { (ids, counts, sorts) ->
                    val sorted = ids.sortedBySystems(
                        sorts = sorts,
                        displayName = { PlatformDefinitions.byId[it]?.displayName ?: it },
                        gameCount = { counts[it] ?: 0 }
                    )
                    _uiState.update { state ->
                        state.copy(
                            platforms = sorted,
                            platformCounts = counts,
                            selectedPlatform = state.selectedPlatform ?: sorted.firstOrNull(),
                            isLoading = false
                        )
                    }
                    // Only (re)load the games list when the set of platforms actually changes,
                    // not on every count tick during a scrape.
                    val idSet = ids.toSet()
                    if (idSet != lastPlatformIdSet) {
                        lastPlatformIdSet = idSet
                        loadGamesForPlatform(_uiState.value.selectedPlatform)
                    }
                }
        }
    }

    fun selectTopTab(tab: TopTab) {
        _uiState.update { it.copy(topTab = tab) }
    }

    private var previewJob: Job? = null
    // Art is randomised once per platform per ViewModel lifetime so re-focusing the same
    // console returns the same list object — LaunchedEffect(previewArt) won't re-trigger
    // the fan animation and images are already warm in Coil's disk cache.
    private val previewArtCache = mutableMapOf<String, List<String>>()
    private val prefetchedSystems = mutableSetOf<String>()

    /** Load a handful of box-art covers to preview the system the carousel is focused on. */
    fun focusSystem(platformId: String) {
        val cached = previewArtCache[platformId]
        if (cached != null) {
            _uiState.update { it.copy(systemPreviewArt = cached) }
            prefetchNeighbours(platformId)
            return
        }
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            val art = artForSystem(platformId)
            _uiState.update { it.copy(systemPreviewArt = art) }
            prefetchNeighbours(platformId)
        }
    }

    /**
     * The covers shown for a system, sampled once and cached. Both the visible fan and the
     * look-ahead prefetch read this same list, so a neighbour we warm is exactly the art that
     * renders when the user lands on it (the underlying query is `ORDER BY RANDOM()`, so without
     * caching each call would return different covers and the prefetch would miss).
     */
    private suspend fun artForSystem(platformId: String): List<String> =
        previewArtCache[platformId] ?: mediaRepository.boxArtSampleForPlatform(platformId, 8)
            .also { previewArtCache[platformId] = it }

    /**
     * Warm the fan art of the systems on either side of the focused one into Coil's memory cache,
     * so scrolling the carousel left/right shows covers immediately instead of grey placeholders.
     * Each system is warmed at most once; the memory-cache key matches AsyncGameArtwork's so the
     * UI request is a synchronous hit.
     */
    private fun prefetchNeighbours(platformId: String) {
        val platforms = _uiState.value.platforms
        val idx = platforms.indexOf(platformId)
        if (idx < 0) return
        val neighbours = listOfNotNull(
            platforms.getOrNull(idx - 1),
            platforms.getOrNull(idx + 1),
            platforms.getOrNull(idx + 2),
        )
        neighbours.forEach { pid ->
            if (!prefetchedSystems.add(pid)) return@forEach
            viewModelScope.launch {
                val loader = appContext.imageLoader
                artForSystem(pid).take(5).forEach { art ->
                    val req = ImageRequest.Builder(appContext)
                        .data(if (art.startsWith("http")) art else File(art))
                        .memoryCacheKey(art)
                        .build()
                    loader.enqueue(req)   // async, non-blocking
                }
            }
        }
    }

    /** Games tab: open a system's game UI. */
    fun enterSystem(platformId: String) {
        selectPlatform(platformId)
        _uiState.update { it.copy(gameViewActive = true) }
    }

    /** Games tab: return from the game UI to the system grid. */
    fun exitToSystems() {
        videoDelayJob?.cancel()
        _uiState.update { it.copy(gameViewActive = false, shouldPlayVideo = false) }
    }

    private fun observeAllMedia() {
        viewModelScope.launch {
            mediaRepository.observeAllMedia().collect { mediaMap ->
                val selectedGame = _uiState.value.games.getOrNull(_uiState.value.selectedGameIndex)
                _uiState.update {
                    it.copy(
                        mediaForGames     = mediaMap,
                        selectedGameMedia = selectedGame?.let { g -> mediaMap[g.id] }
                    )
                }
            }
        }
    }

    private fun observeSettings() {
        viewModelScope.launch {
            combine(
                settingsRepository.layoutMode,
                settingsRepository.videoMuted,
                settingsRepository.videoAutoplayDelayMs,
                settingsRepository.showRecentlyPlayed,
                settingsRepository.showRetroAchievements
            ) { layout, muted, delay, showRecent, showRa ->
                _uiState.update {
                    // if a tab gets hidden while selected, fall back to Games
                    val fallbackTab = when {
                        !showRecent && it.topTab == TopTab.RECENTLY_PLAYED   -> TopTab.GAMES
                        !showRa && it.topTab == TopTab.RETROACHIEVEMENTS      -> TopTab.GAMES
                        else                                                 -> it.topTab
                    }
                    it.copy(
                        layoutMode = layout,
                        videoMuted = muted,
                        videoDelayMs = delay,
                        showRecentlyPlayed = showRecent,
                        showRetroAchievements = showRa,
                        topTab = fallbackTab
                    )
                }
            }.collect { }
        }
    }

    private var gamesJob: Job? = null

    private fun loadGamesForPlatform(platformId: String?) {
        gamesJob?.cancel()
        if (platformId == null) return
        gamesJob = viewModelScope.launch {
            gameRepository.getGamesByPlatform(platformId).collect { games ->
                val firstMedia = _uiState.value.mediaForGames[games.firstOrNull()?.id]
                _uiState.update { state ->
                    state.copy(
                        games             = games,
                        selectedGameIndex = 0,
                        shouldPlayVideo   = false,
                        selectedGameMedia = firstMedia
                    )
                }
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
        val media = _uiState.value.mediaForGames[games[index].id]
        _uiState.update { it.copy(selectedGameIndex = index, shouldPlayVideo = false, selectedGameMedia = media) }

        videoDelayJob = viewModelScope.launch {
            delay(_uiState.value.videoDelayMs)
            _uiState.update { it.copy(shouldPlayVideo = true) }
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
