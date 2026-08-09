package com.gamelaunch.frontend.ui.screen.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.imageLoader
import coil.request.ImageRequest
import com.gamelaunch.frontend.domain.model.Game
import com.gamelaunch.frontend.domain.model.GameMedia
import com.gamelaunch.frontend.domain.model.GameSort
import com.gamelaunch.frontend.domain.model.sortedBy
import com.gamelaunch.frontend.domain.platform.PlatformDefinitions
import com.gamelaunch.frontend.domain.platform.sortedBySystems
import com.gamelaunch.frontend.domain.repository.GameRepository
import com.gamelaunch.frontend.domain.repository.MediaRepository
import com.gamelaunch.frontend.domain.repository.SettingsRepository
import com.gamelaunch.frontend.domain.lockedmode.LockedModeRepository
import com.gamelaunch.frontend.domain.lockedmode.LockedModeState
import com.gamelaunch.frontend.ui.dualscreen.ArtworkBus
import com.gamelaunch.frontend.ui.dualscreen.ArtworkMode
import com.gamelaunch.frontend.ui.dualscreen.ArtworkUiState
import com.gamelaunch.frontend.ui.perf.PerformanceState
import com.gamelaunch.frontend.ui.theme.LayoutMode
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

enum class TopTab { GAMES, FAVORITES, RECENTLY_PLAYED, APPS, RETROACHIEVEMENTS, FRIENDS }

data class HomeUiState(
    val topTab: TopTab = TopTab.GAMES,
    val gameViewActive: Boolean = false,      // Games tab: false = system grid, true = game UI
    val platforms: List<String> = emptyList(),
    val platformCounts: Map<String, Int> = emptyMap(),
    val systemPreviewArt: List<String> = emptyList(),  // box art for the focused system card
    val previewPlatformId: String? = null,             // which system the preview art belongs to
    val selectedPlatform: String? = null,
    val showRecentlyPlayed: Boolean = true,
    val showFavorites: Boolean = true,
    val showRetroAchievements: Boolean = true,
    val showFriends: Boolean = false,
    val recentlyPlayed: List<Game> = emptyList(),
    val favorites: List<Game> = emptyList(),
    val games: List<Game> = emptyList(),
    val gameSort: GameSort = GameSort.DEFAULT,
    val gameGridColumns: Int = 0,             // 0 = auto-fit; > 0 = user-chosen column count
    val selectedGameIndex: Int = 0,
    val selectedGameMedia: GameMedia? = null,
    val mediaForGames: Map<Long, GameMedia> = emptyMap(),
    val shouldPlayVideo: Boolean = false,
    val layoutMode: LayoutMode = LayoutMode.CAROUSEL,
    val videoMuted: Boolean = true,
    val videoDelayMs: Long = 1500L,
    val topScreenImage: com.gamelaunch.frontend.ui.dualscreen.TopScreenImage =
        com.gamelaunch.frontend.ui.dualscreen.TopScreenImage.MARQUEE,
    val isLoading: Boolean = true
)

@HiltViewModel
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HomeViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val gameRepository: GameRepository,
    private val mediaRepository: MediaRepository,
    private val settingsRepository: SettingsRepository,
    private val lockedModeRepository: LockedModeRepository,
    private val artworkBus: ArtworkBus,
    private val performanceState: PerformanceState
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    private var videoDelayJob: Job? = null
    private val isLocked = lockedModeRepository.state
        .map { it == LockedModeState.LOCKED }
        // Treat the brief DataStore-loading window conservatively so a locked library never flashes.
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private fun observeLockedModeTransitions() {
        viewModelScope.launch {
            isLocked.collect { locked ->
                previewJob?.cancel()
                previewArtCache.clear()
                prefetchedSystems.clear()
                _uiState.update {
                    it.copy(
                        systemPreviewArt = emptyList(),
                        previewPlatformId = null,
                        selectedGameMedia = if (locked) null else it.selectedGameMedia,
                        shouldPlayVideo = false
                    )
                }
                // Clearing the preview does not change the carousel's focus index, so Compose's
                // focus effect will not run again. Reload the currently focused system explicitly
                // after the lock filter changes.
                _uiState.value.selectedPlatform?.let(::focusSystem)
                loadGamesForPlatform(_uiState.value.selectedPlatform)
            }
        }
    }

    /**
     * Mirror the current selection/media into [ArtworkBus] so the second (artwork) screen on
     * dual-screen devices stays in sync. Harmless on single-screen devices — nothing observes the
     * bus there. One collector covers every selection change, so no per-action wiring is needed.
     */
    private fun publishArtwork() {
        viewModelScope.launch {
            _uiState.collect { state ->
                val selectedGame = state.games.getOrNull(state.selectedGameIndex)
                val mode = when {
                    state.topTab != TopTab.GAMES -> ArtworkMode.IDLE
                    !state.gameViewActive -> ArtworkMode.SYSTEM_GRID
                    else -> ArtworkMode.GAME
                }
                artworkBus.publish(
                    ArtworkUiState(
                        mode = mode,
                        media = state.selectedGameMedia,
                        shouldPlayVideo = state.shouldPlayVideo,
                        videoMuted = state.videoMuted,
                        systemPreviewArt = state.systemPreviewArt,
                        focusedPlatformId = state.previewPlatformId,
                        title = selectedGame?.title,
                        topImageType = state.topScreenImage
                    )
                )
            }
        }
    }

    // Latest per-system column overrides from settings. Held here (not just in ui state) so the
    // effective count can be re-resolved whenever the selected system changes, not only when the
    // stored map changes. A "" key holds the legacy single-value setting, used as a fallback for any
    // system the user hasn't sized individually yet.
    private var gridColumnsByPlatform: Map<String, Int> = emptyMap()

    /** Resolve the fixed column count for [platformId] (0 = auto-fit) from the stored overrides. */
    private fun resolveGridColumns(platformId: String?): Int =
        gridColumnsByPlatform[platformId] ?: gridColumnsByPlatform[""] ?: 0

    /** The Select-menu options (game sort + per-system column count) for the game grid. */
    private fun observeGameViewPrefs() {
        viewModelScope.launch {
            combine(
                settingsRepository.gameSort,
                settingsRepository.gameGridColumnsByPlatform
            ) { sort, cols -> sort to cols }
                .collect { (sort, cols) ->
                    gridColumnsByPlatform = cols
                    _uiState.update {
                        it.copy(gameSort = sort, gameGridColumns = resolveGridColumns(it.selectedPlatform))
                    }
                }
        }
    }

    private fun observeRecentlyPlayed() {
        viewModelScope.launch {
            isLocked.flatMapLatest { gameRepository.getRecentlyPlayed(30, it) }.collect { games ->
                _uiState.update { it.copy(recentlyPlayed = games) }
            }
        }
    }

    /** Favourited games across every system, for the all-systems Favorites tab. */
    private fun observeFavorites() {
        viewModelScope.launch {
            isLocked.flatMapLatest { gameRepository.getFavorites(it) }.collect { games ->
                _uiState.update { it.copy(favorites = games) }
            }
        }
    }

    private var lastPlatformIdSet: Set<String> = emptySet()

    private fun observePlatforms() {
        viewModelScope.launch {
            isLocked.flatMapLatest { locked -> combine(
                gameRepository.getDistinctPlatformIds(locked),
                gameRepository.getPlatformCounts(locked),
                settingsRepository.systemSort,
                settingsRepository.hiddenPlatforms
            ) { ids, counts, sorts, hidden ->
                // Systems the user has hidden never appear on the home screen.
                val visibleIds = ids.filter { it !in hidden }
                val sorted = visibleIds.sortedBySystems(
                    sorts = sorts,
                    displayName = { PlatformDefinitions.byId[it]?.displayName ?: it },
                    gameCount = { counts[it] ?: 0 }
                )
                Triple(sorted, counts, sorted.toSet())
            } }
                .collect { (sorted, counts, idSet) ->
                    _uiState.update { state ->
                        // If the currently-selected system was just hidden (or removed), fall back
                        // to the first visible one so the grid never points at a gone platform.
                        val selected = state.selectedPlatform
                            ?.takeIf { it in idSet }
                            ?: sorted.firstOrNull()
                        state.copy(
                            platforms = sorted,
                            platformCounts = counts,
                            selectedPlatform = selected,
                            gameGridColumns = resolveGridColumns(selected),
                            isLoading = false
                        )
                    }
                    // Only (re)load the games list when the set of visible platforms actually
                    // changes, not on every count tick during a scrape.
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
    private val previewArtCache = mutableMapOf<Pair<String, Boolean>, List<String>>()
    private val prefetchedSystems = mutableSetOf<Pair<String, Boolean>>()

    /** Load a handful of box-art covers to preview the system the carousel is focused on. */
    fun focusSystem(platformId: String) {
        val locked = isLocked.value
        val cacheKey = platformId to locked
        val cached = previewArtCache[cacheKey]
        if (cached != null) {
            _uiState.update { it.copy(systemPreviewArt = cached, previewPlatformId = platformId) }
            prefetchNeighbours(platformId)
            return
        }
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            val art = artForSystem(platformId, locked)
            _uiState.update { it.copy(systemPreviewArt = art, previewPlatformId = platformId) }
            prefetchNeighbours(platformId)
        }
    }

    /**
     * The covers shown for a system, sampled once and cached. Both the visible fan and the
     * look-ahead prefetch read this same list, so a neighbour we warm is exactly the art that
     * renders when the user lands on it (the underlying query is `ORDER BY RANDOM()`, so without
     * caching each call would return different covers and the prefetch would miss).
     */
    private suspend fun artForSystem(platformId: String, locked: Boolean = isLocked.value): List<String> {
        val cacheKey = platformId to locked
        return previewArtCache[cacheKey]
            ?: mediaRepository.boxArtSampleForPlatform(platformId, 8, locked)
                .also { previewArtCache[cacheKey] = it }
    }

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
            val locked = isLocked.value
            if (!prefetchedSystems.add(pid to locked)) return@forEach
            viewModelScope.launch {
                val loader = appContext.imageLoader
                artForSystem(pid, locked).take(5).forEach { art ->
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
        viewModelScope.launch {
            settingsRepository.friendsEnabled.collect { on ->
                _uiState.update {
                    val fallback = if (!on && it.topTab == TopTab.FRIENDS) TopTab.GAMES else it.topTab
                    it.copy(showFriends = on, topTab = fallback)
                }
            }
        }
        viewModelScope.launch {
            settingsRepository.showFavorites.collect { on ->
                _uiState.update {
                    val fallback = if (!on && it.topTab == TopTab.FAVORITES) TopTab.GAMES else it.topTab
                    it.copy(showFavorites = on, topTab = fallback)
                }
            }
        }
        viewModelScope.launch {
            settingsRepository.topScreenImage.collect { mode ->
                _uiState.update { it.copy(topScreenImage = mode) }
            }
        }
    }

    private var gamesJob: Job? = null

    private fun loadGamesForPlatform(platformId: String?) {
        gamesJob?.cancel()
        if (platformId == null) return
        gamesJob = viewModelScope.launch {
            // Re-sort whenever the games change or the chosen sort order changes.
            combine(
                isLocked.flatMapLatest { gameRepository.getGamesByPlatform(platformId, it) },
                settingsRepository.gameSort
            ) { games, sort -> games.sortedBy(sort) }
                // Sort a large library off the main thread. Room re-emits this flow on every games
                // table change (favourite toggle, play-count/last-played bump), and sorting on Main
                // was dropping frames on the lite build's RK3568.
                .flowOn(Dispatchers.Default)
                .collect { games ->
                    _uiState.update { state ->
                        // Preserve the current selection across re-emissions. Room re-queries this
                        // flow whenever the games table changes — e.g. a favourite toggle or a
                        // play-count/last-played bump after returning from a launched game — and a
                        // blind reset to index 0 would snap the selection (and the top-screen marquee)
                        // back to the first game. Re-find the selected game by id; only fall back to 0
                        // when it's genuinely gone (platform switch, game removed).
                        val previousId = state.games.getOrNull(state.selectedGameIndex)?.id
                        val newIndex = previousId
                            ?.let { id -> games.indexOfFirst { it.id == id } }
                            ?.takeIf { it >= 0 }
                            ?: 0
                        val selectedGame = games.getOrNull(newIndex)
                        state.copy(
                            games             = games,
                            selectedGameIndex = newIndex,
                            shouldPlayVideo   = false,
                            selectedGameMedia = selectedGame?.let { g -> state.mediaForGames[g.id] }
                        )
                    }
                }
        }
    }

    /** Game-grid Select-menu actions. */
    fun setGameSort(sort: GameSort) {
        viewModelScope.launch { settingsRepository.setGameSort(sort) }
    }

    /** Persist the grid size for the system currently being viewed, so each system keeps its own. */
    fun setGameGridColumns(columns: Int) {
        val platformId = _uiState.value.selectedPlatform ?: return
        viewModelScope.launch { settingsRepository.setGameGridColumns(platformId, columns) }
    }

    fun selectPlatform(platformId: String) {
        videoDelayJob?.cancel()
        _uiState.update {
            it.copy(
                selectedPlatform = platformId,
                gameGridColumns = resolveGridColumns(platformId),
                shouldPlayVideo = false
            )
        }
        loadGamesForPlatform(platformId)
    }

    fun onGameSelected(index: Int) {
        val games = _uiState.value.games
        if (index !in games.indices) return

        videoDelayJob?.cancel()
        val media = _uiState.value.mediaForGames[games[index].id]
        _uiState.update { it.copy(selectedGameIndex = index, shouldPlayVideo = false, selectedGameMedia = media) }

        // When running reduced (lite build / performance mode / dual-screen), wait longer before the
        // preview video kicks in, so quick browsing stays smooth and video only starts if the user
        // lingers — instead of decoding a stream on every selection.
        val delayMs =
            if (performanceState.reduced.value) maxOf(_uiState.value.videoDelayMs, PERF_VIDEO_DELAY_MS)
            else _uiState.value.videoDelayMs
        videoDelayJob = viewModelScope.launch {
            delay(delayMs)
            _uiState.update { it.copy(shouldPlayVideo = true) }
        }
    }

    // Keep initialization after every backing field. StateFlow collectors can emit immediately,
    // and several of them touch the preview caches and jobs declared throughout this class.
    init {
        observePlatforms()
        observeSettings()
        observeGameViewPrefs()
        observeAllMedia()
        observeRecentlyPlayed()
        observeFavorites()
        publishArtwork()
        observeLockedModeTransitions()
    }

    private companion object {
        /** Minimum delay before preview video autoplays while running reduced. */
        const val PERF_VIDEO_DELAY_MS = 4000L
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
