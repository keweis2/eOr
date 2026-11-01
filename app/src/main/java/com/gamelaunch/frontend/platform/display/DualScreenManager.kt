package com.gamelaunch.frontend.platform.display

import com.gamelaunch.frontend.ui.app.AppUiMode
import android.content.Context
import android.content.Intent
import android.app.ActivityOptions
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.Log
import android.view.Display
import androidx.activity.ComponentActivity
import com.gamelaunch.frontend.ui.dualscreen.ArtworkBus
import com.gamelaunch.frontend.ui.dualscreen.ArtworkActivity
import com.gamelaunch.frontend.ui.dualscreen.ArtworkPresentation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Detects a second physical display and drives eOr's dual-screen layout: the interactive menu on
 * the bottom panel, game artwork on the top panel (see [DualScreenDevices]).
 *
 * Owned by [ComponentActivity]. On single-screen devices it does nothing and [active] stays false,
 * so the app behaves exactly as before. It listens for hot-plug events (clamshell open/close, a
 * panel powering on/off) and re-evaluates on each.
 */
class DualScreenManager(
    private val activity: ComponentActivity,
    private val artworkBus: ArtworkBus
) {
    private val displayManager =
        activity.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

    private var presentation: ArtworkPresentation? = null
    private var primaryArtworkStarted = false

    private val _active = MutableStateFlow(false)
    /** True while artwork is being presented on a second screen. Drives `LocalDualScreenActive`. */
    val active: StateFlow<Boolean> = _active.asStateFlow()

    private val _uiMode = MutableStateFlow(AppUiMode.SINGLE_SCREEN)
    /** Interface selection survives temporary artwork suspension during gameplay. */
    val uiMode: StateFlow<AppUiMode> = _uiMode.asStateFlow()

    private var preferencesLoaded = false
    private var enabled = true
    private var swap = false
    private var started = false
    // While a game is on the top panel, keep the artwork overlay dismissed so it doesn't cover it.
    private var artworkSuspended = false

    private val listener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = refresh()
        override fun onDisplayRemoved(displayId: Int) = refresh()
        override fun onDisplayChanged(displayId: Int) = refresh()
    }

    fun start() {
        if (started || activity.isFinishing || activity.isDestroyed) return
        started = true
        displayManager.registerDisplayListener(listener, null)
        refresh()
    }

    fun stop() {
        if (started) {
            started = false
            runCatching { displayManager.unregisterDisplayListener(listener) }
        }
        dismiss()
    }

    /** Update placement preferences; the host moves the menu before calling [refresh]. */
    fun setPreferences(enabled: Boolean, swap: Boolean) {
        preferencesLoaded = true
        this.enabled = enabled
        this.swap = swap
    }

    /**
     * The display id the interactive Activity should run on. For the common
     * [DualScreenDevices.Layout.ARTWORK_ON_SECONDARY] case this is the default display (no move
     * needed). For [DualScreenDevices.Layout.MENU_ON_SECONDARY] (Thor) it's the secondary display,
     * so [com.gamelaunch.frontend.MainActivity] can relaunch itself there. Returns
     * [Display.DEFAULT_DISPLAY] when single-screen or disabled.
     */
    fun requiredMenuDisplayId(): Int {
        if (!enabled) return Display.DEFAULT_DISPLAY
        val secondary = secondaryDisplay() ?: return Display.DEFAULT_DISPLAY
        return when (effectiveLayout()) {
            DualScreenDevices.Layout.ARTWORK_ON_SECONDARY -> Display.DEFAULT_DISPLAY
            DualScreenDevices.Layout.MENU_ON_SECONDARY -> secondary.displayId
        }
    }

    private fun effectiveLayout(): DualScreenDevices.Layout =
        DualScreenDevices.layoutFor().let { if (swap) it.flipped() else it }

    /** The first powered-on display that isn't the default one, or null if there's only one screen. */
    private fun secondaryDisplay(): Display? =
        displayManager.displays.firstOrNull {
            it.displayId != Display.DEFAULT_DISPLAY && it.state == Display.STATE_ON
        }

    /**
     * Suspend/restore the artwork overlay. Called when a game launches on (or leaves) the top panel:
     * while suspended the Presentation is dismissed and won't be re-shown, so the game is visible.
     */
    fun setArtworkSuspended(suspended: Boolean) {
        if (artworkSuspended == suspended) return
        artworkSuspended = suspended
        if (started) refresh()
    }

    /** Re-evaluate which display (if any) should show artwork and (re)attach the Presentation. */
    fun refresh() {
        // A display event already queued before unregisterDisplayListener can arrive after stop.
        // Never let an outgoing host recreate a Presentation over its replacement's menu.
        if (!started || !preferencesLoaded || activity.isFinishing || activity.isDestroyed) return
        _uiMode.value = AppUiMode.resolve(enabled, secondaryDisplay() != null)
        // An Activity is already behind the emulator in the task stack. Keep it there so
        // exiting the emulator reveals it naturally; bottom-menu touches must not raise it.
        if (enabled && artworkSuspended && primaryArtworkStarted && secondaryDisplay() != null) return
        val secondary = if (enabled && !artworkSuspended) secondaryDisplay() else null
        if (secondary == null) {
            dismiss()
            _active.value = false
            return
        }

        // Where the artwork goes:
        //  - ARTWORK_ON_SECONDARY (RG DS): artwork on the secondary (top) panel; menu Activity stays.
        //  - MENU_ON_SECONDARY (Thor): the Activity is relaunched onto the secondary (bottom) panel
        //    by MainActivity, so artwork goes on the default (top) display.
        val artworkDisplay = when (effectiveLayout()) {
            DualScreenDevices.Layout.ARTWORK_ON_SECONDARY -> secondary
            DualScreenDevices.Layout.MENU_ON_SECONDARY ->
                displayManager.getDisplay(Display.DEFAULT_DISPLAY) ?: secondary
        }

        _active.value = showOn(artworkDisplay)
    }

    private fun showOn(display: Display): Boolean {
        if (display.displayId == Display.DEFAULT_DISPLAY && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ArtworkActivity.attachMenu(activity)
            if (primaryArtworkStarted) return _active.value
            dismiss()
            ArtworkActivity.attachMenu(activity)
            ArtworkActivity.observeHost { active ->
                _active.value = active
                if (!active) primaryArtworkStarted = false
            }
            return runCatching {
                val intent = Intent(activity, ArtworkActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                    putExtra(ArtworkActivity.EXTRA_REQUEST, ArtworkActivity.nextRequest())
                    putExtra(ArtworkActivity.EXTRA_MENU_DISPLAY, currentDisplayId(activity))
                }
                activity.startActivity(intent, ActivityOptions.makeBasic()
                    .setLaunchDisplayId(display.displayId).toBundle())
                primaryArtworkStarted = true
                false // ArtworkActivity confirms creation before reporting active.
            }.getOrElse {
                Log.w(TAG, "Failed to start primary artwork activity", it)
                false
            }
        }
        val current = presentation
        if (current != null && current.display?.displayId == display.displayId && current.isShowing) {
            return true
        }
        dismiss()
        val next = ArtworkPresentation(
            activity = activity,
            display = display,
            artworkBus = artworkBus
        )
        return runCatching { next.show() }
            .onSuccess { presentation = next }
            .onFailure { Log.w(TAG, "Failed to show artwork presentation on display ${display.displayId}", it) }
            .isSuccess
    }

    fun dismiss() {
        if (primaryArtworkStarted) ArtworkActivity.dismiss(activity)
        primaryArtworkStarted = false
        presentation?.let { runCatching { it.dismiss() } }
        presentation = null
        ArtworkActivity.detachMenu(activity)
        _active.value = false
    }

    companion object {
        private const val TAG = "DualScreenManager"

        /**
         * The physical **top** (artwork) panel's display id on a dual-screen device, or null if
         * there's no second screen. This is where single-screen games should launch. Mirrors
         * [refresh]'s artwork-display logic so it stays consistent. [swap] applies the manual override.
         */
        fun artworkDisplayId(context: Context, swap: Boolean): Int? =
            displayIds(context, swap)?.first

        /** The menu panel paired with [artworkDisplayId], or null on a single-screen device. */
        fun menuDisplayId(context: Context, swap: Boolean): Int? =
            displayIds(context, swap)?.second

        /** Artwork and menu display IDs, resolved together from the same physical layout. */
        private fun displayIds(context: Context, swap: Boolean): Pair<Int, Int>? {
            val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager ?: return null
            val secondary = dm.displays.firstOrNull {
                it.displayId != Display.DEFAULT_DISPLAY && it.state == Display.STATE_ON
            } ?: return null
            val layout = DualScreenDevices.layoutFor().let { if (swap) it.flipped() else it }
            return when (layout) {
                DualScreenDevices.Layout.ARTWORK_ON_SECONDARY -> secondary.displayId to Display.DEFAULT_DISPLAY
                DualScreenDevices.Layout.MENU_ON_SECONDARY -> Display.DEFAULT_DISPLAY to secondary.displayId
            }
        }

        /**
         * The display id the Activity currently occupies. Used by MainActivity to decide whether a
         * MENU_ON_SECONDARY relaunch is needed.
         */
        fun currentDisplayId(activity: ComponentActivity): Int =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                activity.display?.displayId ?: Display.DEFAULT_DISPLAY
            } else {
                @Suppress("DEPRECATION")
                activity.windowManager.defaultDisplay?.displayId ?: Display.DEFAULT_DISPLAY
            }
    }
}
