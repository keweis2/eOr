package com.gamelaunch.frontend.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamelaunch.frontend.data.db.dao.LaunchBoxDao
import com.gamelaunch.frontend.domain.model.ScraperConfig
import com.gamelaunch.frontend.domain.repository.EmulatorRepository
import com.gamelaunch.frontend.domain.repository.RetroAchievementsRepository
import com.gamelaunch.frontend.domain.repository.ScraperRepository
import com.gamelaunch.frontend.domain.repository.SettingsRepository
import com.gamelaunch.frontend.domain.usecase.EsdeImportStatus
import com.gamelaunch.frontend.domain.usecase.ImportEsdeMediaUseCase
import com.gamelaunch.frontend.domain.usecase.LbSyncStatus
import com.gamelaunch.frontend.domain.usecase.ScanAndroidGamesUseCase
import com.gamelaunch.frontend.domain.usecase.SyncLaunchBoxUseCase
import com.gamelaunch.frontend.ui.theme.LayoutMode
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
    val esdeImportStatus: EsdeImportStatus? = null,
    val showRecentlyPlayed: Boolean = true,
    val darkMode: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val scraperRepository: ScraperRepository,
    private val emulatorRepository: EmulatorRepository,
    private val syncLaunchBoxUseCase: SyncLaunchBoxUseCase,
    private val importEsdeMediaUseCase: ImportEsdeMediaUseCase,
    private val scanAndroidGamesUseCase: ScanAndroidGamesUseCase,
    private val raRepository: RetroAchievementsRepository,
    private val launchBoxDao: LaunchBoxDao
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
            }.collect { newState ->
                _uiState.update { current ->
                    newState.copy(
                        emulatorDetecting = current.emulatorDetecting,
                        emulatorDetectResult = current.emulatorDetectResult,
                        lbSyncStatus = current.lbSyncStatus,
                        lbGameCount = current.lbGameCount,
                        mediaFolderPath = current.mediaFolderPath,
                        esdeImportStatus = current.esdeImportStatus,
                        showRecentlyPlayed = current.showRecentlyPlayed
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
            settingsRepository.showRecentlyPlayed.collect { show ->
                _uiState.update { it.copy(showRecentlyPlayed = show) }
            }
        }
        viewModelScope.launch {
            settingsRepository.darkMode.collect { dark ->
                _uiState.update { it.copy(darkMode = dark) }
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
    }

    fun setShowRecentlyPlayed(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setShowRecentlyPlayed(enabled) }
    }

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDarkMode(enabled) }
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
}
