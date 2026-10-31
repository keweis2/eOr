package com.gamelaunch.frontend.ui.dualscreen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamelaunch.frontend.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class DualScreenPreferences @Inject constructor(settings: SettingsRepository) : ViewModel() {
    val image = settings.topScreenImage.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TopScreenImage.MARQUEE)
}
