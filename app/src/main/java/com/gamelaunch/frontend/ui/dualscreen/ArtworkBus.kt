package com.gamelaunch.frontend.ui.dualscreen

import com.gamelaunch.frontend.domain.model.GameMedia
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** What the artwork (top) screen is currently showing. */
enum class ArtworkMode {
    /** No game context yet (onboarding, splash, non-Games tab) — show branding or a section icon. */
    IDLE,
    /** Browsing the system grid — show the focused system's preview art. */
    SYSTEM_GRID,
    /** Inside a system — show the selected game's screenshot / video / box art. */
    GAME
}

/**
 * Which home-screen section the bottom screen is on when there's no game art to show (ArtworkMode
 * .IDLE). The top panel mirrors it with the matching tab icon — the same way the Settings area shows
 * a gear — so a dual-screen user always sees what they're browsing on the top panel. [BRANDING] is
 * the neutral fallback (onboarding, splash, the Games tab before any system is focused).
 */
enum class TopScreenSection {
    BRANDING, FAVORITES, RECENTLY_PLAYED, APPS, RETROACHIEVEMENTS, FRIENDS
}

/** Immutable snapshot the [ArtworkPresentation] renders on the second screen. */
data class ArtworkUiState(
    val mode: ArtworkMode = ArtworkMode.IDLE,
    val media: GameMedia? = null,
    val shouldPlayVideo: Boolean = false,
    val videoMuted: Boolean = true,
    val systemPreviewArt: List<String> = emptyList(),
    /** The focused system id in SYSTEM_GRID mode, so the preview fan uses the right box aspect. */
    val focusedPlatformId: String? = null,
    val title: String? = null,
    /** Which media the top panel renders in GAME mode (marquee / screenshot / miximage). */
    val topImageType: TopScreenImage = TopScreenImage.MARQUEE,
    /** In IDLE mode, which non-game home section is active, so the top panel shows its icon. */
    val idleSection: TopScreenSection = TopScreenSection.BRANDING
)

/**
 * A process-wide hand-off channel from the interactive UI (bottom screen) to the artwork
 * [ArtworkPresentation] (top screen). [HomeViewModel] publishes a derived snapshot whenever the
 * selection/media changes; the Presentation observes [state]. Kept as a plain @Singleton (not a
 * ViewModel) so it survives independently of any NavBackStackEntry-scoped ViewModel and can be
 * read from the Activity that owns the Presentation.
 */
@Singleton
class ArtworkBus @Inject constructor() {
    private val _state = MutableStateFlow(ArtworkUiState())
    val state: StateFlow<ArtworkUiState> = _state.asStateFlow()

    // Light/dark is tracked separately from [state] because it's driven by the Activity's own
    // settings collector (which stays alive while the user is in Settings toggling the theme),
    // not by the HomeViewModel selection stream that fills [state]. The artwork Presentation reads
    // both so the top-screen gradient re-themes live without tearing down the Presentation.
    private val _darkMode = MutableStateFlow(false)
    val darkMode: StateFlow<Boolean> = _darkMode.asStateFlow()

    // True while the user is in the Settings area. Driven by the Activity's nav observer (not the
    // HomeViewModel selection stream), so the top panel can show a gear instead of the last game art.
    private val _settingsActive = MutableStateFlow(false)
    val settingsActive: StateFlow<Boolean> = _settingsActive.asStateFlow()

    fun publish(state: ArtworkUiState) {
        _state.value = state
    }

    fun setDarkMode(dark: Boolean) {
        _darkMode.value = dark
    }

    fun setSettingsActive(active: Boolean) {
        _settingsActive.value = active
    }
}
