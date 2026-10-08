package com.gamelaunch.frontend.ui.screen.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamelaunch.frontend.data.theme.CustomThemeRepository
import com.gamelaunch.frontend.data.theme.ThemeFile
import com.gamelaunch.frontend.domain.repository.SettingsRepository
import com.gamelaunch.frontend.ui.theme.EorTheme
import com.gamelaunch.frontend.ui.theme.ThemeDraft
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ThemeEditorState(
    /** The theme being edited (a built-in one is the starting point for a new custom theme). */
    val base: EorTheme,
    val draft: ThemeDraft,
    val saving: Boolean = false,
    val error: String? = null,
    /** Set once saved — the screen closes. */
    val saved: Boolean = false
) {
    /** Live preview of the draft. */
    val preview: EorTheme get() = draft.toTheme(base, base.id)
    val isNew: Boolean get() = !base.id.startsWith(ThemeFile.ID_PREFIX)
}

/**
 * Backs the theme editor. Opened with a theme id ([ARG_BASE]): editing a custom theme updates it,
 * editing a built-in starts a new custom theme from it ("My Ocean"). Nothing is written until Save.
 */
@HiltViewModel
class ThemeEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val customThemes: CustomThemeRepository,
    private val settings: SettingsRepository
) : ViewModel() {

    private val _state: MutableStateFlow<ThemeEditorState>
    val state: StateFlow<ThemeEditorState>

    init {
        val base = customThemes.resolve(savedStateHandle.get<String>(ARG_BASE))
        val draft = ThemeDraft.from(base).let {
            if (base.id.startsWith(ThemeFile.ID_PREFIX)) it else it.copy(name = "My ${base.name}".take(32))
        }
        _state = MutableStateFlow(ThemeEditorState(base, draft))
        state = _state.asStateFlow()
    }

    fun update(change: (ThemeDraft) -> ThemeDraft) =
        _state.update { it.copy(draft = change(it.draft), error = null) }

    /** Back to how the theme was when the editor opened (keeping the name being typed). */
    fun reset() = _state.update { it.copy(draft = ThemeDraft.from(it.base).copy(name = it.draft.name), error = null) }

    fun save() {
        val s = _state.value
        if (s.saving) return
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            runCatching { customThemes.saveEdited(s.base, s.draft.toTheme(s.base, s.base.id)) }.fold(
                onSuccess = { theme ->
                    settings.setThemeId(theme.id)
                    _state.update { it.copy(saving = false, saved = true) }
                },
                onFailure = { e -> _state.update { it.copy(saving = false, error = e.message ?: "Couldn't save the theme") } }
            )
        }
    }

    companion object {
        const val ARG_BASE = "base"
    }
}
