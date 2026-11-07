package com.gamelaunch.frontend.ui.screen.detail

import android.view.ViewTreeObserver
import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalView

/** Restores page input focus without polling or taking focus from descendants. */
@Composable
internal fun rememberDetailFocusModifier(contentReady: Boolean): Modifier {
    val focusRequester = remember { FocusRequester() }
    var hasFocus by remember { mutableStateOf(false) }
    val view = LocalView.current
    var windowFocused by remember(view) { mutableStateOf(view.hasWindowFocus()) }
    DisposableEffect(view) {
        val observer = view.viewTreeObserver
        val listener = ViewTreeObserver.OnWindowFocusChangeListener { windowFocused = it }
        observer.addOnWindowFocusChangeListener(listener)
        // Compose can be attached after the window already gained focus.
        windowFocused = view.hasWindowFocus()
        onDispose {
            if (observer.isAlive) observer.removeOnWindowFocusChangeListener(listener)
        }
    }
    LaunchedEffect(contentReady, windowFocused, hasFocus) {
        if (contentReady && windowFocused && !hasFocus) {
            focusRequester.requestFocus()
        }
    }
    return Modifier
        .focusRequester(focusRequester)
        .onFocusChanged { hasFocus = it.hasFocus }
        .focusable()
}
