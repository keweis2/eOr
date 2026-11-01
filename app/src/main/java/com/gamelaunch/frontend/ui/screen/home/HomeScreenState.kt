package com.gamelaunch.frontend.ui.screen.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.gamelaunch.frontend.domain.model.GameSort

/** Composition-owned focus and overlays. Only navigation cursors survive recreation. */
internal class HomeScreenState {
    var systemFocusIndex by mutableIntStateOf(0)
    var appFocusIndex by mutableIntStateOf(0)
    var gridFocusIndex by mutableIntStateOf(0)
    var recentFocusIndex by mutableIntStateOf(0)
    var favFocusIndex by mutableIntStateOf(0)
    var gridPageSize by mutableIntStateOf(0)
    var optionsFocusIndex by mutableIntStateOf(0)
    var showGameOptions by mutableStateOf(false)
    var showLockConfirm by mutableStateOf(false)
    var showUnlock by mutableStateOf(false)
    var unlockError by mutableStateOf<String?>(null)
    var isFocused by mutableStateOf(false)

    val focusRequester = FocusRequester()
    var heldDirection by mutableStateOf<Key?>(null)
        private set
    private var repeatJob: Job? = null

    fun stopRepeat() {
        repeatJob?.cancel()
        repeatJob = null
        heldDirection = null
    }

    fun startHold(scope: CoroutineScope, key: Key, move: (Key) -> Boolean) {
        stopRepeat()
        heldDirection = key
        repeatJob = scope.launch {
            delay(320)
            while (isActive) {
                if (!move(key)) break
                delay(75)
            }
        }
    }

    companion object {
        val Saver = listSaver<HomeScreenState, Int>(
            save = { listOf(it.systemFocusIndex, it.appFocusIndex, it.gridFocusIndex, it.recentFocusIndex, it.favFocusIndex) },
            restore = { HomeScreenState().apply {
                systemFocusIndex = it[0]
                appFocusIndex = it[1]
                gridFocusIndex = it[2]
                recentFocusIndex = it[3]
                favFocusIndex = it[4]
            } },
        )
    }
}

@Composable
internal fun rememberHomeScreenState(): HomeScreenState =
    rememberSaveable(saver = HomeScreenState.Saver) { HomeScreenState() }

internal data class HomeActions(
    val enterSystem: (String) -> Unit,
    val exitToSystems: () -> Unit,
    val focusSystem: (String) -> Unit,
    val onGameSelected: (Int) -> Unit,
    val toggleMute: () -> Unit,
    val selectTopTab: (TopTab) -> Unit,
    val setGameGridColumns: (Int) -> Unit,
    val setGameSort: (GameSort) -> Unit,
    val refreshApps: () -> Unit,
    val launchApp: (String) -> Unit,
)
