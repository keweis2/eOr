package com.gamelaunch.frontend.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamelaunch.frontend.domain.model.ScraperConfig
import com.gamelaunch.frontend.domain.repository.ScraperRepository
import com.gamelaunch.frontend.domain.repository.SettingsRepository
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
    val videoMuted: Boolean = true
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val scraperRepository: ScraperRepository
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
            }.collect { _uiState.value = it }
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

    fun finishSetup() {
        viewModelScope.launch { settingsRepository.setFirstLaunchComplete() }
    }
}
