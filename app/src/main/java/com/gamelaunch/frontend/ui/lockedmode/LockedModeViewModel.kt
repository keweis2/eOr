package com.gamelaunch.frontend.ui.lockedmode

import androidx.lifecycle.ViewModel
import com.gamelaunch.frontend.domain.lockedmode.LockedModeRepository
import com.gamelaunch.frontend.domain.lockedmode.LockedModeState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@HiltViewModel
class LockedModeViewModel @Inject constructor(
    private val repository: LockedModeRepository
) : ViewModel() {
    val state: Flow<LockedModeState> = repository.state

    suspend fun activate() = repository.activate()
    suspend fun unlock(pin: String) = repository.unlock(pin)
}
