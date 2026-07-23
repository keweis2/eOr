package com.gamelaunch.frontend.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamelaunch.frontend.data.db.dao.LaunchBoxDao
import com.gamelaunch.frontend.domain.model.ScraperConfig
import com.gamelaunch.frontend.domain.repository.EmulatorRepository
import com.gamelaunch.frontend.domain.repository.GameRepository
import com.gamelaunch.frontend.domain.platform.PlatformDefinitions
import com.gamelaunch.frontend.domain.repository.RetroAchievementsRepository
import com.gamelaunch.frontend.domain.repository.ScraperRepository
import com.gamelaunch.frontend.domain.platform.SystemSort
import android.net.Uri
import com.gamelaunch.frontend.domain.repository.SettingsRepository
import com.gamelaunch.frontend.domain.usecase.ConvertBackgroundImageUseCase
import com.gamelaunch.frontend.domain.usecase.EsdeImportStatus
import com.gamelaunch.frontend.domain.usecase.ImportEsdeMediaUseCase
import com.gamelaunch.frontend.domain.usecase.LbSyncStatus
import com.gamelaunch.frontend.domain.usecase.ScanAndroidGamesUseCase
import com.gamelaunch.frontend.domain.model.Game
import com.gamelaunch.frontend.domain.usecase.SyncLaunchBoxUseCase
import com.gamelaunch.frontend.ui.theme.LayoutMode
import kotlinx.coroutines.flow.first
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val androidScanResult: String? = null,
    val romRootPath: String = "",
    val ssId: String = "",
    val ssPassword: String = "",
    val raUsername: String = "",
    val raPassword: String = "",
    val raApiKey: String = "",
    val raLoggingIn: Boolean = false,
    val raLoginResult: String? = null,   // success or error message to surface
    val raLoggedIn: Boolean = false,     // a token is stored
    val layoutMode: LayoutMode = LayoutMode.CAROUSEL,
    val scrapeMetadata: Boolean = true,
    val scrapeBoxArt: Boolean = true,
    val scrapeScreenshots: Boolean = true,
    val scrapeWheelLogos: Boolean = true,
    val scrapeVideos: Boolean = true,
    val preferredRegion: String = "us",
    val credentialValidating: Boolean = false,
    val credentialValid: Boolean? = null,
    val videoDelayMs: Long = 1500L,
    val videoMuted: Boolean = true,
    val emulatorDetecting: Boolean = false,
    val emulatorDetectResult: String? = null,
    val lbSyncStatus: LbSyncStatus? = null,
    val lbGameCount: Int = 0,
    val mediaFolderPath: String = "",
    val mediaStoragePath: String = "",
    val esdeImportStatus: EsdeImportStatus? = null,
    val showRecentlyPlayed: Boolean = true,
    val showRetroAchievements: Boolean = true,
    val friendsEnabled: Boolean = false,
    val darkMode: Boolean = false,
    val backgroundImageEnabled: Boolean = false,
    val backgroundImagePath: String = "",
    val backgroundImageMode: String = "FILL",
    val backgroundImageOpacity: Float = 0.15f,
    val convertingBackground: Boolean = false,
    val systemSort: List<SystemSort> = emptyList(),
    // Systems currently in the library (platform ids) and which of them the user has hidden,
    // for the "Hide Systems" settings section.
    val libraryPlatforms: List<String> = emptyList(),
    val hiddenPlatforms: Set<String> = emptySet(),
    val showAndroidGameSelection: Boolean = false,
    val installedApps: List<com.gamelaunch.frontend.domain.model.InstalledApp> = emptyList(),
    val checkedPackages: Set<String> = emptySet()
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val scraperRepository: ScraperRepository,
    private val emulatorRepository: EmulatorRepository,
    private val syncLaunchBoxUseCase: SyncLaunchBoxUseCase,
    private val importEsdeMediaUseCase: ImportEsdeMediaUseCase,
    private val scanAndroidGamesUseCase: ScanAndroidGamesUseCase,
    private val convertBackgroundImageUseCase: ConvertBackgroundImageUseCase,
    private val raRepository: RetroAchievementsRepository,
    private val gameRepository: GameRepository,
    private val friendRepository: com.gamelaunch.frontend.domain.repository.FriendRepository,
    private val launchBoxDao: LaunchBoxDao,
    val packageManagerHelper: com.gamelaunch.frontend.launcher.PackageManagerHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState

    init {
        viewModelScope.launch {
            combine(
                settingsRepository.romRootPath,
                settingsRepository.layoutMode,
                settingsRepository.scraperConfig,
                settingsRepository.videoAutoplayDelayMs,
                settingsRepository.videoMuted
            ) { romPath, layout, config, delay, muted ->
                SettingsUiState(
                    romRootPath = romPath,
                    ssId = config.ssid,
                    ssPassword = config.sspassword,
                    layoutMode = layout,
                    scrapeMetadata = config.scrapeMetadata,
                    scrapeBoxArt = config.scrapeBoxArt,
                    scrapeScreenshots = config.scrapeScreenshots,
                    scrapeWheelLogos = config.scrapeWheelLogos,
                    scrapeVideos = config.scrapeVideos,
                    preferredRegion = config.preferredRegion,
                    videoDelayMs = delay,
                    videoMuted = muted
                )
            }.collect { owned ->
                // Merge only the fields this combine owns; everything else (darkMode, systemSort,
                // RA creds, etc.) is collected separately and must be preserved.
                _uiState.update { current ->
                    current.copy(
                        romRootPath = owned.romRootPath,
                        ssId = owned.ssId,
                        ssPassword = owned.ssPassword,
                        layoutMode = owned.layoutMode,
                        scrapeMetadata = owned.scrapeMetadata,
                        scrapeBoxArt = owned.scrapeBoxArt,
                        scrapeScreenshots = owned.scrapeScreenshots,
                        scrapeWheelLogos = owned.scrapeWheelLogos,
                        scrapeVideos = owned.scrapeVideos,
                        preferredRegion = owned.preferredRegion,
                        videoDelayMs = owned.videoDelayMs,
                        videoMuted = owned.videoMuted
                    )
                }
            }
        }
        viewModelScope.launch {
            launchBoxDao.getGameCount().collect { count ->
                _uiState.update { it.copy(lbGameCount = count) }
            }
        }
        viewModelScope.launch {
            settingsRepository.mediaFolderPath.collect { path ->
                _uiState.update { it.copy(mediaFolderPath = path) }
            }
        }
        viewModelScope.launch {
            settingsRepository.mediaStoragePath.collect { path ->
                _uiState.update { it.copy(mediaStoragePath = path) }
            }
        }
        viewModelScope.launch {
            settingsRepository.showRecentlyPlayed.collect { show ->
                _uiState.update { it.copy(showRecentlyPlayed = show) }
            }
        }
        viewModelScope.launch {
            settingsRepository.showRetroAchievements.collect { show ->
                _uiState.update { it.copy(showRetroAchievements = show) }
            }
        }
        viewModelScope.launch {
            settingsRepository.friendsEnabled.collect { on ->
                _uiState.update { it.copy(friendsEnabled = on) }
            }
        }
        viewModelScope.launch {
            settingsRepository.darkMode.collect { dark ->
                _uiState.update { it.copy(darkMode = dark) }
            }
        }
        viewModelScope.launch {
            settingsRepository.systemSort.collect { sorts ->
                _uiState.update { it.copy(systemSort = sorts) }
            }
        }
        viewModelScope.launch {
            // The platforms present in the library, ordered by display name, for the hide list.
            gameRepository.getDistinctPlatformIds().collect { ids ->
                val ordered = ids.sortedBy { PlatformDefinitions.byId[it]?.displayName ?: it }
                _uiState.update { it.copy(libraryPlatforms = ordered) }
            }
        }
        viewModelScope.launch {
            settingsRepository.hiddenPlatforms.collect { hidden ->
                _uiState.update { it.copy(hiddenPlatforms = hidden) }
            }
        }
        viewModelScope.launch {
            settingsRepository.backgroundImageEnabled.collect { enabled ->
                _uiState.update { it.copy(backgroundImageEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            settingsRepository.backgroundImagePath.collect { path ->
                _uiState.update { it.copy(backgroundImagePath = path) }
            }
        }
        viewModelScope.launch {
            settingsRepository.backgroundImageMode.collect { mode ->
                _uiState.update { it.copy(backgroundImageMode = mode) }
            }
        }
        viewModelScope.launch {
            settingsRepository.backgroundImageOpacity.collect { opacity ->
                _uiState.update { it.copy(backgroundImageOpacity = opacity) }
            }
        }
        viewModelScope.launch {
            settingsRepository.raUsername.collect { u ->
                _uiState.update { it.copy(raUsername = u) }
            }
        }
        viewModelScope.launch {
            settingsRepository.raApiKey.collect { k ->
                _uiState.update { it.copy(raApiKey = k) }
            }
        }
        viewModelScope.launch {
            settingsRepository.raToken.collect { t ->
                _uiState.update { it.copy(raLoggedIn = t.isNotBlank()) }
            }
        }
        viewModelScope.launch {
            gameRepository.getGamesByPlatform("android").collect { games ->
                val pkgs = games.map { it.romFilename }.toSet()
                _uiState.update { it.copy(checkedPackages = pkgs) }
            }
        }
    }

    fun setShowRecentlyPlayed(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setShowRecentlyPlayed(enabled) }
    }

    fun setShowRetroAchievements(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setShowRetroAchievements(enabled) }
    }

    /** Master opt-in/out for the Friends feature; routes through the repo so the P2P engine and
     *  profile sharing are brought up or fully torn down. */
    fun setFriendsEnabled(enabled: Boolean) {
        viewModelScope.launch { friendRepository.setEnabled(enabled) }
    }

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDarkMode(enabled) }
    }

    fun setPlatformHidden(platformId: String, hidden: Boolean) {
        viewModelScope.launch { settingsRepository.setPlatformHidden(platformId, hidden) }
    }

    /**
     * Convert the picked image to a single-colour branding mask, persist its path, and turn the
     * custom background on. A spinner flag is surfaced while the (off-main-thread) conversion runs.
     */
    fun importBackgroundImage(uri: Uri) {
        if (_uiState.value.convertingBackground) return
        _uiState.update { it.copy(convertingBackground = true) }
        viewModelScope.launch {
            val path = convertBackgroundImageUseCase(uri)
            if (path != null) {
                settingsRepository.setBackgroundImagePath(path)
                settingsRepository.setBackgroundImageEnabled(true)
            }
            _uiState.update { it.copy(convertingBackground = false) }
        }
    }

    fun setBackgroundImageEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setBackgroundImageEnabled(enabled) }
    }

    fun setBackgroundImageMode(mode: String) {
        viewModelScope.launch { settingsRepository.setBackgroundImageMode(mode) }
    }

    fun setBackgroundImageOpacity(opacity: Float) {
        viewModelScope.launch { settingsRepository.setBackgroundImageOpacity(opacity) }
    }

    fun clearBackgroundImage() {
        viewModelScope.launch { settingsRepository.clearBackgroundImage() }
    }

    /** Toggle a system-sort key. Selected keys are an ordered list of up to two (primary first). */
    fun toggleSystemSort(sort: SystemSort) {
        val current = _uiState.value.systemSort
        val next = when {
            sort in current      -> current - sort
            current.size < 2     -> current + sort
            else                 -> current   // already two — deselect one first
        }
        viewModelScope.launch { settingsRepository.setSystemSort(next) }
    }

    fun updateRaUsername(value: String) = _uiState.update { it.copy(raUsername = value, raLoginResult = null) }
    fun updateRaPassword(value: String) = _uiState.update { it.copy(raPassword = value, raLoginResult = null) }
    fun updateRaApiKey(value: String) = _uiState.update { it.copy(raApiKey = value, raLoginResult = null) }

    /**
     * Log in to RetroAchievements with username + password via the Connect API.
     * On success we persist the username + token (never the raw password) plus the optional
     * Web API key, which unlocks the full recently-played dashboard.
     */
    fun saveRaCredentials() {
        val s = _uiState.value
        val username = s.raUsername.trim()
        val password = s.raPassword
        if (username.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(raLoginResult = "Enter both username and password") }
            return
        }
        _uiState.update { it.copy(raLoggingIn = true, raLoginResult = null) }
        viewModelScope.launch {
            // Persist the optional Web API key first so the dashboard can use it after login.
            settingsRepository.setRaApiKey(s.raApiKey.trim())

            val result = raRepository.login(username, password)
            result.onSuccess { session ->
                settingsRepository.setRaSession(
                    username       = session.username,
                    token          = session.token,
                    points         = session.points,
                    softcorePoints = session.softcorePoints
                )
                _uiState.update {
                    it.copy(
                        raLoggingIn  = false,
                        raPassword   = "",   // clear from memory once exchanged for a token
                        raLoginResult = "Signed in as ${session.username}"
                    )
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(raLoggingIn = false, raLoginResult = e.message ?: "Login failed")
                }
            }
        }
    }

    fun signOutRa() {
        viewModelScope.launch {
            settingsRepository.clearRaCredentials()
            _uiState.update { it.copy(raPassword = "", raApiKey = "", raLoginResult = null) }
        }
    }

    fun clearRaLoginResult() {
        _uiState.update { it.copy(raLoginResult = null) }
    }

    fun updateSsId(value: String) = _uiState.update { it.copy(ssId = value, credentialValid = null) }
    fun updateSsPassword(value: String) = _uiState.update { it.copy(ssPassword = value, credentialValid = null) }

    fun saveCredentials() {
        val s = _uiState.value
        viewModelScope.launch {
            settingsRepository.setScraperCredentials(s.ssId, s.ssPassword)
        }
    }

    fun validateCredentials() {
        val s = _uiState.value
        _uiState.update { it.copy(credentialValidating = true, credentialValid = null) }
        viewModelScope.launch {
            val config = settingsRepository.scraperConfig.firstOrNull() ?: ScraperConfig()
            val result = scraperRepository.validateCredentials(config.copy(ssid = s.ssId, sspassword = s.ssPassword))
            _uiState.update {
                it.copy(credentialValidating = false, credentialValid = result.getOrDefault(false))
            }
        }
    }

    fun setRomRootPath(path: String) {
        viewModelScope.launch { settingsRepository.setRomRootPath(path) }
    }

    fun setMediaFolderPath(path: String) {
        viewModelScope.launch { settingsRepository.setMediaFolderPath(path) }
    }

    /**
     * Persist the chosen media-storage folder and, if it already contains an ES-DE media library,
     * import that media automatically so the user doesn't need a separate import step.
     */
    fun chooseMediaStorageFolder(path: String) {
        viewModelScope.launch {
            settingsRepository.setMediaStoragePath(path)
            importEsdeMediaUseCase(path).collect { status ->
                _uiState.update { it.copy(esdeImportStatus = status) }
            }
        }
    }

    fun clearMediaStoragePath() {
        viewModelScope.launch { settingsRepository.setMediaStoragePath("") }
        _uiState.update { it.copy(esdeImportStatus = null) }
    }

    fun importEsdeMedia() {
        val path = _uiState.value.mediaFolderPath
        if (path.isEmpty()) return
        if (_uiState.value.esdeImportStatus is EsdeImportStatus.Scanning) return
        viewModelScope.launch {
            importEsdeMediaUseCase(path).collect { status ->
                _uiState.update { it.copy(esdeImportStatus = status) }
            }
        }
    }

    fun clearEsdeImportStatus() {
        _uiState.update { it.copy(esdeImportStatus = null) }
    }

    fun setLayoutMode(mode: LayoutMode) {
        viewModelScope.launch { settingsRepository.setLayoutMode(mode) }
    }

    fun setScrapeMetadata(v: Boolean) = _uiState.update { it.copy(scrapeMetadata = v) }.also { saveOptions() }
    fun setScrapeBoxArt(v: Boolean) = _uiState.update { it.copy(scrapeBoxArt = v) }.also { saveOptions() }
    fun setScrapeScreenshots(v: Boolean) = _uiState.update { it.copy(scrapeScreenshots = v) }.also { saveOptions() }
    fun setScrapeWheelLogos(v: Boolean) = _uiState.update { it.copy(scrapeWheelLogos = v) }.also { saveOptions() }
    fun setScrapeVideos(v: Boolean) = _uiState.update { it.copy(scrapeVideos = v) }.also { saveOptions() }

    private fun saveOptions() {
        val s = _uiState.value
        viewModelScope.launch {
            settingsRepository.updateScraperOptions(
                s.scrapeMetadata, s.scrapeBoxArt, s.scrapeScreenshots, s.scrapeWheelLogos, s.scrapeVideos
            )
        }
    }

    fun setPreferredRegion(region: String) {
        viewModelScope.launch { settingsRepository.setPreferredRegion(region) }
    }

    fun setVideoDelayMs(ms: Long) {
        viewModelScope.launch { settingsRepository.setVideoAutoplayDelayMs(ms) }
    }

    fun autoDetectEmulators() {
        if (_uiState.value.emulatorDetecting) return
        _uiState.update { it.copy(emulatorDetecting = true, emulatorDetectResult = null) }
        viewModelScope.launch {
            val configured = emulatorRepository.autoDetectAndAssign()
            val found = emulatorRepository.getInstalledEmulators().count { it.isInstalled }
            _uiState.update {
                it.copy(
                    emulatorDetecting = false,
                    emulatorDetectResult = "Found $found emulator${if (found != 1) "s" else ""}, " +
                        "configured $configured platform${if (configured != 1) "s" else ""}"
                )
            }
        }
    }

    fun clearEmulatorDetectResult() {
        _uiState.update { it.copy(emulatorDetectResult = null) }
    }

    fun syncLaunchBox() {
        if (_uiState.value.lbSyncStatus is LbSyncStatus.Downloading ||
            _uiState.value.lbSyncStatus is LbSyncStatus.Parsing) return
        viewModelScope.launch {
            syncLaunchBoxUseCase().collect { status ->
                _uiState.update { it.copy(lbSyncStatus = status) }
            }
        }
    }

    fun dismissLbSyncStatus() {
        _uiState.update { it.copy(lbSyncStatus = null) }
    }

    fun scanAndroidGames() {
        viewModelScope.launch {
            scanAndroidGamesUseCase().collect { progress ->
                if (progress.scanned == progress.total) {
                    _uiState.update { it.copy(androidScanResult = "Found ${progress.added} new Android game${if (progress.added != 1) "s" else ""}") }
                }
            }
        }
    }

    fun clearAndroidScanResult() {
        _uiState.update { it.copy(androidScanResult = null) }
    }

    fun finishSetup() {
        viewModelScope.launch { settingsRepository.setFirstLaunchComplete() }
    }

    fun showAndroidGameSelection(show: Boolean) {
        if (show) {
            val apps = packageManagerHelper.getInstalledApps()
            _uiState.update { it.copy(showAndroidGameSelection = true, installedApps = apps) }
        } else {
            _uiState.update { it.copy(showAndroidGameSelection = false) }
        }
    }

    fun toggleAndroidGameSelection(app: com.gamelaunch.frontend.domain.model.InstalledApp, isChecked: Boolean) {
        viewModelScope.launch {
            val romPath = "package:${app.packageName}"
            if (isChecked) {
                val exists = _uiState.value.checkedPackages.contains(app.packageName)
                if (!exists) {
                    val game = Game(
                        title       = app.label,
                        romPath     = romPath,
                        romFilename = app.packageName,
                        platformId  = "android"
                    )
                    gameRepository.insertGame(game)
                }
            } else {
                val games = gameRepository.getGamesByPlatform("android").first()
                val game = games.find { it.romFilename == app.packageName }
                if (game != null) {
                    gameRepository.deleteGame(game.id)
                }
            }
        }
    }
}
