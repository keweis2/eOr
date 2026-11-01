package com.gamelaunch.frontend.ui.dualscreen

import android.app.ActivityOptions
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import com.gamelaunch.frontend.MainActivity
import com.gamelaunch.frontend.ui.screen.home.AppsViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.lang.ref.WeakReference
import javax.inject.Inject

/** The primary panel cannot host a Presentation; give its passive artwork a separate task. */
@AndroidEntryPoint
class ArtworkActivity : ComponentActivity() {
    @Inject lateinit var artworkBus: ArtworkBus
    private val appsViewModel: AppsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getIntExtra(EXTRA_REQUEST, -1) != request) {
            finishAndRemoveTask()
            return
        }
        current = WeakReference(this)
        onBackPressedDispatcher.addCallback(this) {
            val menu = activeMenu()
            if (menu != null) menu.onBackPressedDispatcher.onBackPressed()
            else showMenu()
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        // Keep the artwork immersive while the menu owns controller input on the other panel.
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        val panels = (menuActivity?.get() as? MainActivity)?.settingsPanels
        setContent { ArtworkScreen(artworkBus, appsViewModel, panels) }
        showMenu()
    }

    private fun showMenu() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startActivity(Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }, ActivityOptions.makeBasic().setLaunchDisplayId(
                intent.getIntExtra(EXTRA_MENU_DISPLAY, 0)
            ).toBundle())
        }
    }

    override fun onStart() {
        super.onStart()
        if (current?.get() === this) hostStarted?.invoke(true)
    }

    // Only forward input while the menu is visible. Dispatching Back to a stopped menu
    // bypasses its lifecycle-bound Compose handlers and can finish its task.
    private fun activeMenu(): ComponentActivity? = menuActivity?.get()?.takeIf {
        it.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
    }

    // Some firmware keeps controller focus on this display while the menu is visible below.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        activeMenu()?.dispatchKeyEvent(event) ?: super.dispatchKeyEvent(event)

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean =
        activeMenu()?.dispatchGenericMotionEvent(event) ?: super.dispatchGenericMotionEvent(event)

    override fun onDestroy() {
        if (current?.get() === this) {
            current = null
            // A replacement menu may already have requested its own artwork host.
            if (intent.getIntExtra(EXTRA_REQUEST, -1) == request) {
                hostStarted?.invoke(false)
                menuActivity = null
                hostStarted = null
            }
        }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_REQUEST = "artwork_request"
        private var request = 0
        fun nextRequest(): Int = ++request

        const val EXTRA_MENU_DISPLAY = "menu_display"
        private var hostStarted: ((Boolean) -> Unit)? = null
        fun observeHost(callback: (Boolean) -> Unit) { hostStarted = callback }

        private var current: WeakReference<ArtworkActivity>? = null
        private var menuActivity: WeakReference<ComponentActivity>? = null

        fun attachMenu(activity: ComponentActivity) {
            menuActivity = WeakReference(activity)
        }

        fun detachMenu(activity: ComponentActivity) {
            if (menuActivity?.get() === activity) {
                menuActivity = null
                hostStarted = null
            }
        }

        fun dismiss(menu: ComponentActivity) {
            // A late onStop from the previous menu must not dismiss the new menu's artwork.
            if (menuActivity?.get() !== menu) return
            request++ // Invalidate any launch still waiting for onCreate.
            current?.get()?.finishAndRemoveTask()
            current = null
        }
    }
}
