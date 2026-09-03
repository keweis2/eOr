package com.gamelaunch.frontend.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamelaunch.frontend.data.webserver.WebTransferManager
import com.gamelaunch.frontend.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Web Transfer: master on/off toggle that starts/stops the LAN web server, plus the connect URL, PIN
 * and QR the browser needs. Mirrors the Save Sync view-model shape.
 */
@HiltViewModel
class WebTransferViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val manager: WebTransferManager,
) : ViewModel() {

    data class UiState(
        val enabled: Boolean = false,
        val running: Boolean = false,
        val url: String = "",
        val pin: String = "",
        val log: List<String> = emptyList(),
    )

    private val _enabled = MutableStateFlow(false)

    val uiState: StateFlow<UiState> =
        combine(_enabled, manager.state) { enabled, s ->
            UiState(
                enabled = enabled,
                running = s.running,
                url = s.url,
                pin = s.pin,
                log = s.log,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    init {
        viewModelScope.launch {
            val enabled = settings.webTransferEnabled.first()
            _enabled.value = enabled
            // If the user left it on, make sure the server is actually up when they return.
            if (enabled) manager.start()
        }
    }

    /** Master toggle: persist, then start or stop the server. */
    fun setEnabled(on: Boolean) {
        _enabled.value = on
        viewModelScope.launch { settings.setWebTransferEnabled(on) }
        if (on) manager.start() else manager.stop()
    }
}
