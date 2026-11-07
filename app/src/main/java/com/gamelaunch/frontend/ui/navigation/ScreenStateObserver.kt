package com.gamelaunch.frontend.ui.navigation

import com.gamelaunch.frontend.ui.screen.home.HomeUiState
import com.gamelaunch.frontend.ui.screen.detail.GameDetailUiState

interface ScreenStateObserver {
    fun onHomeState(state: HomeUiState)
    fun onDetailState(state: GameDetailUiState)
}
