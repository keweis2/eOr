package com.gamelaunch.frontend.ui.dualscreen

import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/** Read existing root insets without replacing Compose's inset listeners. */
internal class SettingsKeyboard {
    var menu: View? = null
    var controls: View? = null
    var modal: View? = null

    fun isVisible(): Boolean = listOfNotNull(menu, controls, modal).any {
        it.isAttachedToWindow &&
            ViewCompat.getRootWindowInsets(it)?.isVisible(WindowInsetsCompat.Type.ime()) == true
    }

    fun dismiss(): Boolean {
        if (!isVisible()) return false
        // The IME can be on another display, but its connection/token belongs to the editor.
        val editor = listOfNotNull(modal, controls).firstOrNull { it.isAttachedToWindow }
            ?: return false
        val token = editor.windowToken ?: return false
        editor.context.getSystemService(InputMethodManager::class.java)
            ?.hideSoftInputFromWindow(token, 0)
        return true
    }

    fun clear() {
        dismiss()
        menu = null
        controls = null
        modal = null
    }
}
