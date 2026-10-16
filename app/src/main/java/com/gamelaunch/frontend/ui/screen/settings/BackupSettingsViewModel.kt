package com.gamelaunch.frontend.ui.screen.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamelaunch.frontend.domain.usecase.ExportSettingsUseCase
import com.gamelaunch.frontend.domain.usecase.ImportSettingsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Settings backup to / restore from a file on the device, so moving to a new handheld doesn't mean
 * redoing every preference. Same format as the Web Transfer export (no credentials or PINs).
 */
@HiltViewModel
class BackupSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exportSettings: ExportSettingsUseCase,
    private val importSettings: ImportSettingsUseCase,
) : ViewModel() {

    /** Result of the last backup/restore, shown under the buttons. Null = nothing yet. */
    data class Message(val text: String, val isError: Boolean)

    private val _message = MutableStateFlow<Message?>(null)
    val message: StateFlow<Message?> = _message

    fun backupTo(uri: Uri) {
        viewModelScope.launch {
            _message.value = runCatching {
                val json = exportSettings()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(json.toByteArray()) }
                }
            }.fold(
                onSuccess = { Message("Settings backed up", isError = false) },
                onFailure = { Message("Couldn't save the backup: ${it.message ?: "unknown error"}", isError = true) }
            )
        }
    }

    fun restoreFrom(uri: Uri) {
        viewModelScope.launch {
            _message.value = runCatching {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
                }
                importSettings(text)
            }.fold(
                onSuccess = { Message("Restored $it settings", isError = false) },
                onFailure = { Message("That doesn't look like an eOr backup", isError = true) }
            )
        }
    }
}
