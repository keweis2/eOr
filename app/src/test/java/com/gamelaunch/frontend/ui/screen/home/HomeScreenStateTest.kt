package com.gamelaunch.frontend.ui.screen.home

import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.input.key.Key
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeScreenStateTest {
    @Test fun `restoration retains cursors and clears transient overlays and focus`() {
        val state = HomeScreenState().apply {
            systemFocusIndex = 1
            appFocusIndex = 2
            gridFocusIndex = 3
            recentFocusIndex = 4
            favFocusIndex = 5
            showUnlock = true
            unlockError = "Incorrect PIN"
            showGameOptions = true
            isFocused = true
        }
        val saved = with(HomeScreenState.Saver) {
            SaverScope { true }.save(state)
        }
        val restored = HomeScreenState.Saver.restore(requireNotNull(saved))!!
        assertEquals(listOf(1, 2, 3, 4, 5), listOf(
            restored.systemFocusIndex, restored.appFocusIndex, restored.gridFocusIndex,
            restored.recentFocusIndex, restored.favFocusIndex,
        ))
        assertFalse(restored.showUnlock)
        assertNull(restored.unlockError)
        assertFalse(restored.showGameOptions)
        assertFalse(restored.isFocused)
    }

    @Test fun `repeat waits for hold threshold and stops when released`() = runTest {
        val state = HomeScreenState()
        var moves = 0
        state.startHold(this, Key.DirectionRight) { moves++; true }
        advanceTimeBy(319)
        assertEquals(0, moves)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, moves)
        advanceTimeBy(75)
        runCurrent()
        assertEquals(2, moves)
        state.stopRepeat()
        advanceTimeBy(1000)
        assertEquals(2, moves)
        assertNull(state.heldDirection)
    }

    @Test fun `changing held direction cancels previous repeat`() = runTest {
        val state = HomeScreenState()
        val moves = mutableListOf<Key>()
        state.startHold(this, Key.DirectionRight) { moves.add(it); true }
        advanceTimeBy(200)
        state.startHold(this, Key.DirectionLeft) { moves.add(it); false }
        advanceTimeBy(500)
        assertEquals(listOf(Key.DirectionLeft), moves)
        state.stopRepeat()
    }
}
