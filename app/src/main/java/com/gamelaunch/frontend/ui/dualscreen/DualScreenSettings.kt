package com.gamelaunch.frontend.ui.dualscreen

import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import com.gamelaunch.frontend.ui.navigation.Screen
import com.gamelaunch.frontend.ui.navigation.isSettingsDestination
import com.gamelaunch.frontend.ui.navigation.backOrHome
import com.gamelaunch.frontend.ui.screen.settings.SettingsCategory
import com.gamelaunch.frontend.ui.screen.settings.SettingsIndexScreen
import com.gamelaunch.frontend.ui.screen.settings.SettingsViewModel

internal const val SETTINGS_SETUP_KEY = "settings_setup"

val LocalSettingsPanels = staticCompositionLocalOf<SettingsPanels?> { null }
val LocalSettingsControls = staticCompositionLocalOf { false }
val LocalSettingsPanelBack = staticCompositionLocalOf<(() -> Unit)?> { null }

/** A portal keeps destination ownership in the menu's navigation graph, while its View and
 * Android UI services (IME, dialogs, density) belong to the display rendering the controls. */
class SettingsPanels {
    var pane by mutableStateOf<SettingsPane?>(null)
        private set
    var controlsFocused by mutableStateOf(false)
        private set
    var selectedCategory by mutableStateOf(SettingsCategory.APPEARANCE)
        private set
    var categoryScrollState by mutableStateOf(ScrollState(0))
        private set
    var controlsView: ComposeView? = null
        private set
    private val categoryActivationKeys = mutableSetOf<Int>()
    private data class Modal(val view: View, val dismissOnBack: () -> Boolean, val dismiss: () -> Unit)
    private val modals = mutableListOf<Modal>()

    fun focusControls() { controlsFocused = true }
    fun focusCategories() { controlsFocused = false }

    fun selectCategory(category: SettingsCategory, edit: Boolean = controlsFocused) {
        selectedCategory = category
        controlsFocused = edit
    }

    internal fun attachControls(view: ComposeView) { controlsView = view }
    internal fun detachControls(view: ComposeView) {
        if (controlsView === view) controlsView = null
    }

    internal fun addModal(view: View, dismissOnBack: () -> Boolean = { true }, dismiss: () -> Unit) {
        modals.add(Modal(view, dismissOnBack, dismiss))
    }

    internal fun removeModal(view: View) {
        modals.removeAll { it.view === view }
    }

    fun show(pane: SettingsPane) { this.pane = pane }
    fun clear(pane: SettingsPane) {
        if (this.pane === pane) {
            this.pane = null
            controlsView = null
        }
    }
    /** Called when leaving the settings graph, losing the display, or destroying the host. */
    fun reset() {
        pane = null
        controlsView = null
        controlsFocused = false
        categoryActivationKeys.clear()
        modals.clear()
        selectedCategory = SettingsCategory.APPEARANCE
        categoryScrollState = ScrollState(0)
    }

    /** Back from a gesture and Back from a controller share IME-first behavior. */
    fun back(): Boolean {
        val active = pane ?: return false
        val modal = modals.lastOrNull()
        if (modal != null) {
            if (!dismissKeyboard(modal.view) && modal.dismissOnBack()) modal.dismiss()
        } else if (controlsFocused) {
            if (!dismissKeyboard()) active.back()
        } else active.close()
        return true
    }

    private fun dismissKeyboard(target: View? = controlsView): Boolean {
        val view = target ?: return false
        if (ViewCompat.getRootWindowInsets(view)
                ?.isVisible(WindowInsetsCompat.Type.ime()) != true) return false
        ViewCompat.getWindowInsetsController(view)
            ?.hide(WindowInsetsCompat.Type.ime())
        return true
    }

    fun dispatch(event: KeyEvent): Boolean {
        val active = pane ?: return false
        val code = event.keyCode
        val back = code == KeyEvent.KEYCODE_BUTTON_B || code == KeyEvent.KEYCODE_BACK
        val activate = code == KeyEvent.KEYCODE_BUTTON_A ||
            code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
        if (event.action == KeyEvent.ACTION_UP && categoryActivationKeys.remove(code)) return true
        // One physical press can change the receiving pane. Do not let its repeat enter
        // controls, activate a control, pop another nested route, or close all settings.
        if ((back || activate) && event.repeatCount > 0) return true
        modals.lastOrNull()?.let { modal ->
            if (back) {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    if (!dismissKeyboard(modal.view) && !modal.view.dispatchKeyEvent(event) &&
                        modal.dismissOnBack()) modal.dismiss()
                }
                return true
            }
            if (modal.view.isInTouchMode) modal.view.requestFocusFromTouch()
            // Modal input must never leak to the categories or controls behind it.
            return modal.view.dispatchKeyEvent(event) || activate || code in directionKeys
        }
        if (back) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                if (controlsFocused) {
                    if (dismissKeyboard()) return true
                    // A focused field/control may own Back. The scaffold handles the
                    // unconsumed event via LocalSettingsPanelBack after its children.
                    if (controlsView?.dispatchKeyEvent(event) == true) return true
                }
                back()
            }
            return true
        }
        if (!controlsFocused) {
            if (!activate && code !in directionKeys) return false
            if (event.action == KeyEvent.ACTION_DOWN) {
                when {
                    activate -> {
                        categoryActivationKeys.add(code)
                        active.selectCategory(selectedCategory, true)
                    }
                    code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN -> {
                        val step = if (code == KeyEvent.KEYCODE_DPAD_DOWN) 1 else -1
                        val index = (selectedCategory.ordinal + step).coerceIn(0, SettingsCategory.entries.lastIndex)
                        active.selectCategory(SettingsCategory.entries[index], false)
                    }
                }
            }
            return true
        }
        // Forwarding across windows does not automatically leave Android touch mode.
        controlsView?.let { if (it.isInTouchMode) it.requestFocusFromTouch() }
        val handled = controlsView?.dispatchKeyEvent(event) == true
        return handled || activate || code in directionKeys
    }

    private companion object {
        val directionKeys = setOf(
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
        )
    }
}

class SettingsPane(
    val parent: CompositionContext,
    val back: () -> Unit,
    val close: () -> Unit,
    val selectCategory: (SettingsCategory, Boolean) -> Unit,
    val content: @Composable () -> Unit,
)

/** Own the portal session in one place. Navigation invalidates input synchronously;
 * display loss, host destruction and composition disposal release the same session.
 * Category destinations only publish content; they do not own session cleanup. */
@Composable
internal fun SettingsPanelHost(
    navController: NavHostController,
    panels: SettingsPanels,
    enabled: Boolean,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(navController, panels, enabled, lifecycle) {
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            if (!enabled || !destination.isSettingsDestination) {
                panels.reset()
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY) panels.reset()
        }
        if (!enabled) panels.reset()
        lifecycle.addObserver(observer)
        navController.addOnDestinationChangedListener(listener)
        onDispose {
            navController.removeOnDestinationChangedListener(listener)
            lifecycle.removeObserver(observer)
            panels.reset()
        }
    }
}

@Composable
fun DualScreenSettingsDestination(
    navController: NavHostController,
    content: @Composable () -> Unit,
) {
    val panels = LocalSettingsPanels.current
    if (panels == null || !LocalDualScreenActive.current) {
        content()
        return
    }
    val settingsEntry = remember(navController) {
        runCatching { navController.getBackStackEntry(Screen.Settings.route) }.getOrNull()
    }
    if (settingsEntry == null) {
        content()
        return
    }
    val viewModel: SettingsViewModel = hiltViewModel(settingsEntry)
    val entry = LocalViewModelStoreOwner.current
        as? NavBackStackEntry
    val route = entry?.destination?.route
    val category = SettingsCategory.entries.firstOrNull { it.route == route }
    val parent = rememberCompositionContext()
    val latestContent = rememberUpdatedState(content)
    val setup = settingsEntry.savedStateHandle.get<Boolean>(SETTINGS_SETUP_KEY) == true
    val close = {
        val navigateHome = {
            navController.navigate(Screen.Home.route) {
                popUpTo(Screen.Settings.route) { inclusive = true }
                launchSingleTop = true
            }
        }
        if (setup) viewModel.completeSetup(navigateHome) else navigateHome()
    }
    val selectCategory: (SettingsCategory, Boolean) -> Unit = { selected, edit ->
        panels.selectCategory(selected, edit)
        if (route != selected.route) {
            navController.navigate(selected.route) {
                popUpTo(Screen.SettingsIndex.route)
                launchSingleTop = true
            }
        }
    }
    val pane = remember(parent) {
        SettingsPane(
            parent = parent,
            back = { if (category != null) panels.focusCategories() else navController.backOrHome() },
            close = close,
            selectCategory = selectCategory,
        ) {
            CompositionLocalProvider(
                LocalSettingsControls provides true,
                LocalSettingsPanelBack provides
                    if (category != null) ({ panels.focusCategories() }) else null,
            ) { latestContent.value() }
        }
    }
    // SettingsPanelHost owns the portal's lifetime across destination replacements.
    // Destination disposal can precede the next destination's composition by a frame;
    // withdrawing here would briefly reveal the artwork gear and detach the controls View.
    SideEffect {
        // Read the controller directly at commit time. A newly composed destination's
        // flow collector starts at null and can miss the navigation emission that created it.
        if (navController.currentBackStackEntry === entry) {
            if (category != null) panels.selectCategory(category)
            panels.show(pane)
        }
    }
    SettingsIndexScreen(
        onBack = if (setup) null else close,
        onGoToLibrary = {
            navController.navigate(Screen.Home.route) {
                popUpTo(Screen.Settings.route) { inclusive = true }
                launchSingleTop = true
            }
        },
        onOpenCategory = { selectCategory(it, true) },
        viewModel = viewModel,
        selectedCategory = panels.selectedCategory,
        categoryScrollState = panels.categoryScrollState,
        focusCategories = !panels.controlsFocused,
        onCategoriesTouched = { panels.focusCategories() },
    )
}

@Composable
internal fun SettingsControls(panels: SettingsPanels, pane: SettingsPane) {
    LaunchedEffect(panels.controlsFocused, pane) {
        if (panels.controlsFocused) panels.controlsView?.requestFocusFromTouch()
    }
    val currentPane by rememberUpdatedState(pane)
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            ComposeView(context).apply {
                setParentCompositionContext(pane.parent)
                // Focus belongs to the inner AndroidComposeView, which the scaffold makes
                // focusable in touch mode. A focusable ViewGroup here would swallow keys.
                descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
                setContent {
                    Box(Modifier.fillMaxSize().pointerInput(panels) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                if (event.changes.any { it.pressed }) {
                                    panels.focusControls()
                                }
                            }
                        }
                    }) { currentPane.content() }
                }
                panels.attachControls(this)
            }
        },
        update = { view ->
            // Keep the display's View attached across categories. The parent context still
            // follows the destination so lifecycle, ViewModels and saved state stay scoped.
            view.setParentCompositionContext(pane.parent)
            panels.attachControls(view)
        },
        onRelease = { view ->
            panels.detachControls(view)
            view.disposeComposition()
        },
    )
}
