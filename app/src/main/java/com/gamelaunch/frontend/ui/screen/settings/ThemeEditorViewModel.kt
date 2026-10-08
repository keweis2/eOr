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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.gamelaunch.frontend.data.theme.WallpaperImages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
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
    /** The background photo as drawn (blurred), for the preview; null when there's none. */
    val wallpaperPreview: ImageBitmap? = null,
    /** A picked image is being resized. */
    val processingImage: Boolean = false,
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
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ThemeEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val customThemes: CustomThemeRepository,
    private val settings: SettingsRepository
) : ViewModel() {

    /** A newly picked (already normalised) image, not saved until Save; null = keep the base's. */
    private val pickedImage = MutableStateFlow<ByteArray?>(null)

    private val _state: MutableStateFlow<ThemeEditorState>
    val state: StateFlow<ThemeEditorState>

    init {
        val base = customThemes.resolve(savedStateHandle.get<String>(ARG_BASE))
        val draft = ThemeDraft.from(base).let {
            if (base.id.startsWith(ThemeFile.ID_PREFIX)) it else it.copy(name = "My ${base.name}".take(32))
        }
        _state = MutableStateFlow(ThemeEditorState(base, draft))
        state = _state.asStateFlow()
        observePreviewImage()
    }

    /** Re-decodes the preview whenever the image or its blur changes. */
    private fun observePreviewImage() {
        viewModelScope.launch {
            combine(pickedImage, _state.map { it.draft.wallpaper?.blur }.distinctUntilChanged()) { picked, blur -> picked to blur }
                .collectLatest { (picked, blur) ->
                    val bitmap = if (blur == null) null else withContext(Dispatchers.IO) {
                        val loaded = if (picked != null) WallpaperImages.loadForDisplay(picked, blur)
                        else customThemes.wallpaperFile(_state.value.base)?.let { WallpaperImages.loadForDisplay(it, blur) }
                        loaded?.asImageBitmap()
                    }
                    _state.update { it.copy(wallpaperPreview = bitmap) }
                }
        }
    }

    /** Uses [image] as the theme's background photo (resized here; saved with the theme). */
    fun setImage(image: ByteArray) {
        if (_state.value.processingImage) return
        _state.update { it.copy(processingImage = true, error = null) }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { WallpaperImages.normalize(image) } }.fold(
                onSuccess = { jpeg ->
                    pickedImage.value = jpeg
                    _state.update {
                        // A first photo starts with the defaults (glows off over a photo).
                        val w = it.draft.wallpaper ?: EorTheme.Wallpaper()
                        it.copy(processingImage = false, draft = it.draft.copy(wallpaper = w, glows = w.glows))
                    }
                },
                onFailure = { e -> _state.update { it.copy(processingImage = false, error = e.message ?: "Couldn't use that image") } }
            )
        }
    }

    fun removeImage() {
        pickedImage.value = null
        _state.update {
            it.copy(draft = it.draft.copy(wallpaper = null, glows = it.base.darkGlows.isNotEmpty()), error = null)
        }
    }

    fun updateWallpaper(change: (EorTheme.Wallpaper) -> EorTheme.Wallpaper) = _state.update { s ->
        val w = s.draft.wallpaper ?: return@update s
        s.copy(draft = s.draft.copy(wallpaper = change(w)))
    }

    fun update(change: (ThemeDraft) -> ThemeDraft) =
        _state.update { it.copy(draft = change(it.draft), error = null) }

    /** Back to how the theme was when the editor opened (keeping the name being typed). */
    fun reset() {
        pickedImage.value = null
        _state.update { it.copy(draft = ThemeDraft.from(it.base).copy(name = it.draft.name), error = null) }
    }

    fun save() {
        val s = _state.value
        if (s.saving) return
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            runCatching { customThemes.saveEdited(s.base, s.draft.toTheme(s.base, s.base.id), pickedImage.value) }.fold(
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
