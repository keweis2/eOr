package com.gamelaunch.frontend.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamelaunch.frontend.data.db.dao.LaunchBoxDao
import com.gamelaunch.frontend.domain.model.ScraperConfig
import com.gamelaunch.frontend.domain.repository.EmulatorRepository
import com.gamelaunch.frontend.domain.repository.ScraperRepository
import com.gamelaunch.frontend.domain.repository.SettingsRepository
import com.gamelaunch.frontend.domain.usecase.EsdeImportStatus
import com.gamelaunch.frontend.domain.usecase.ImportEsdeMediaUseCase
import com.gamelaunch.frontend.domain.usecase.LbSyncStatus
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
    val romRootPath: String = "",
    val ssId: String = "",
    val ssPassword: String = "",
    val layoutMode: LayoutMode = LayoutMode.CAROUSEL,
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
    val esdeImportStatus: EsdeImportStatus? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val scraperRepository: ScraperRepository,
    private val emulatorRepository: EmulatorRepository,
    private val syncLaunchBoxUseCase: SyncLaunchBoxUseCase,
    private val importEsdeMediaUseCase: ImportEsdeMediaUseCase,
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
                        esdeImportStatus = current.esdeImportStatus
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

    fun setScrapeBoxArt(v: Boolean) = _uiState.update { it.copy(scrapeBoxArt = v) }.also { saveOptions() }
    fun setScrapeScreenshots(v: Boolean) = _uiState.update { it.copy(scrapeScreenshots = v) }.also { saveOptions() }
    fun setScrapeWheelLogos(v: Boolean) = _uiState.update { it.copy(scrapeWheelLogos = v) }.also { saveOptions() }
    fun setScrapeVideos(v: Boolean) = _uiState.update { it.copy(scrapeVideos = v) }.also { saveOptions() }

    private fun saveOptions() {
        val s = _uiState.value
        viewModelScope.launch {
            settingsRepository.updateScraperOptions(s.scrapeBoxArt, s.scrapeScreenshots, s.scrapeWheelLogos, s.scrapeVideos)
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

    fun finishSetup() {
        viewModelScope.launch { settingsRepository.setFirstLaunchComplete() }
    }
}
