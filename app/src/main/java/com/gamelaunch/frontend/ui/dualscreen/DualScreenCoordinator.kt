package com.gamelaunch.frontend.ui.dualscreen

import com.gamelaunch.frontend.ui.navigation.Screen
import com.gamelaunch.frontend.ui.navigation.ScreenStateObserver
import com.gamelaunch.frontend.ui.screen.home.HomeUiState
import com.gamelaunch.frontend.ui.screen.home.TopTab
import com.gamelaunch.frontend.ui.screen.home.artworkGame
import com.gamelaunch.frontend.ui.screen.detail.GameDetailUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One navigation-authoritative snapshot; hidden library updates never overwrite detail artwork. */
data class DualScreenDisplayState(
    val imagePreference: TopScreenImage = TopScreenImage.MARQUEE,
    val route: String? = null,
    val detailGameId: Long? = null,
    val library: ArtworkUiState = ArtworkUiState(),
    val detail: ArtworkUiState? = null,
    val externalAppSessionActive: Boolean = false,
    val artwork: ArtworkUiState = ArtworkUiState(),
    val settingsActive: Boolean = false,
)

class DualScreenCoordinator(private val bus: ArtworkBus) : ScreenStateObserver {
    private var disposed = false
    private val mutableState = MutableStateFlow(DualScreenDisplayState())
    val state = mutableState.asStateFlow()

    // The replacement menu may already be publishing before the outgoing composition disposes.
    // Stop accepting its updates without clearing the process-wide artwork channel.
    fun dispose() { disposed = true }

    fun onDestination(route: String?, gameId: Long? = null) {
        val old = mutableState.value
        update(old.copy(route = route, detailGameId = gameId,
            detail = old.detail.takeIf { old.route == route && old.detailGameId == gameId }))
    }

    fun onImagePreference(image: TopScreenImage) = update(mutableState.value.copy(imagePreference = image))

    fun onExternalAppSession(active: Boolean) = update(mutableState.value.copy(externalAppSessionActive = active))

    override fun onHomeState(state: HomeUiState) {
        val game = state.artworkGame()
        val collectionActive = state.topTab == TopTab.FAVORITES || state.topTab == TopTab.RECENTLY_PLAYED
        val library = ArtworkUiState(
            gameId = game?.id,
            mode = when {
                collectionActive -> if (game != null) ArtworkMode.GAME else ArtworkMode.IDLE
                state.topTab != TopTab.GAMES -> ArtworkMode.IDLE
                !state.gameViewActive -> ArtworkMode.SYSTEM_GRID
                else -> ArtworkMode.GAME
            },
            media = game?.let { selected ->
                state.mediaForGames[selected.id]?.takeIf { it.gameId == selected.id }
                    ?: state.selectedGameMedia?.takeIf { it.gameId == selected.id }
            },
            title = game?.title,
            shouldPlayVideo = state.shouldPlayVideo && !collectionActive,
            videoMuted = state.videoMuted,
            fanActive = state.fanActive,
            fanIndex = state.fanIndex,
            fanTitle = state.fanGames.getOrNull(state.fanIndex)?.title,
            systemPreviewArt = state.systemPreviewArt,
            focusedPlatformId = state.previewPlatformId,
            topImageType = state.topScreenImage,
            idleSection = when (state.topTab) {
                TopTab.GAMES -> TopScreenSection.BRANDING
                TopTab.FAVORITES -> TopScreenSection.FAVORITES
                TopTab.RECENTLY_PLAYED -> TopScreenSection.RECENTLY_PLAYED
                TopTab.APPS -> TopScreenSection.APPS
                TopTab.RETROACHIEVEMENTS -> TopScreenSection.RETROACHIEVEMENTS
                TopTab.FRIENDS -> TopScreenSection.FRIENDS
            },
        )
        update(mutableState.value.copy(library = library))
    }

    override fun onDetailState(state: GameDetailUiState) {
        val current = mutableState.value
        val game = state.game ?: return
        if (current.route != Screen.GameDetail.route || current.detailGameId != game.id) return
        update(current.copy(detail = ArtworkUiState(
            gameId = game.id, mode = ArtworkMode.GAME, title = game.title,
            media = state.media?.takeIf { it.gameId == game.id },
            shouldPlayVideo = state.shouldPlayVideo, videoMuted = state.videoMuted,
        )))
    }

    private fun update(next: DualScreenDisplayState) {
        if (disposed) return
        val settings = next.route?.startsWith("settings") == true || next.route == Screen.EmulatorConfig.route
        val art = when (next.route) {
            Screen.Home.route -> next.library
            Screen.GameDetail.route -> (next.detail ?: ArtworkUiState()).copy(topImageType = next.imagePreference)
            else -> ArtworkUiState()
        }
        val visible = art.copy(topImageType = next.imagePreference, shouldPlayVideo = art.shouldPlayVideo && !next.externalAppSessionActive)
        mutableState.value = next.copy(artwork = visible, settingsActive = settings)
        bus.publish(visible)
        bus.setSettingsActive(settings)
    }
}
