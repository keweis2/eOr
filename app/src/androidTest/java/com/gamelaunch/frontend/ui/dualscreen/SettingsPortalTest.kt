package com.gamelaunch.frontend.ui.dualscreen

import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.gamelaunch.frontend.ui.navigation.Screen
import android.app.Presentation
import android.graphics.SurfaceTexture
import android.view.Display
import android.view.Surface
import android.content.Context
import android.hardware.display.DisplayManager
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import android.os.ParcelFileDescriptor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.gamelaunch.frontend.ui.screen.settings.rememberDpadEditableField
import com.gamelaunch.frontend.ui.dualscreen.SettingsDialog as Dialog
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Exercises a real second window and Compose portal, rather than mocked panel callbacks. */
class SettingsPortalTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val panels = SettingsPanels()
    private val page = mutableStateOf<String?>("Appearance")
    private val dialog = mutableStateOf(false)
    private val dismissOnBack = mutableStateOf(true)
    private var presentation: Presentation? = null
    private var activations = 0
    private var menuView: android.view.View? = null
    private var dialogView: android.view.View? = null
    private val input = mutableStateOf("")

    private fun open(displayOverride: Display? = null) {
        val manager = rule.activity.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val display = displayOverride ?: manager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION).firstOrNull()
        assumeTrue("Requires a second display", display != null)
        rule.setContent {
          CompositionLocalProvider(LocalSettingsPanels provides panels) {
            val menu = LocalView.current
            DisposableEffect(menu) {
                menuView = menu
                panels.attachMenu(menu)
                onDispose { panels.detachMenu(menu); menuView = null }
            }
            val name = page.value
            if (name != null) key(name) {
              val parent = rememberCompositionContext()
              val pane = remember(parent) {
                SettingsPane(
                    parent, back = { panels.focusCategories() }, close = { panels.reset() },
                    selectCategory = { _, edit -> if (edit) panels.focusControls() else panels.focusCategories() },
                ) {
                    Column {
                        Text(name)
                        val field = rememberDpadEditableField()
                        TextField(input.value, { input.value = it }, label = { Text("Input") },
                            readOnly = field.readOnly, modifier = field.modifier)
                        Button(onClick = { activations++ }) { Text("Activate") }
                        Button(onClick = { dialog.value = true }) { Text("Open dialog") }
                        if (dialog.value) {
                            Dialog(onDismissRequest = { dialog.value = false },
                                properties = androidx.compose.ui.window.DialogProperties(
                                    dismissOnBackPress = dismissOnBack.value,
                                )) {
                                val view = LocalView.current
                                DisposableEffect(view) {
                                    dialogView = view
                                    onDispose { dialogView = null }
                                }
                                Column {
                                    val field = rememberDpadEditableField()
                                    TextField(input.value, { input.value = it }, label = { Text("Dialog input") },
                                        readOnly = field.readOnly, modifier = field.modifier)
                                    Button(onClick = { dialog.value = false }) { Text("Dismiss dialog") }
                                }
                            }
                        }
                    }
                }
              }
              SideEffect { panels.show(pane) }
            }
            Text("Categories")
          }
        }
        rule.runOnIdle {
            presentation = Presentation(rule.activity, display!!).apply {
                setOnDismissListener { panels.reset() }
                val view = ComposeView(SettingsDisplayContext(rule.activity, context, display!!)).apply {
                    setViewTreeLifecycleOwner(rule.activity)
                    setViewTreeViewModelStoreOwner(rule.activity)
                    setViewTreeSavedStateRegistryOwner(rule.activity)
                    setContent { panels.pane?.let { SettingsControls(panels, it) } }
                }
                setContentView(view)
                show()
                window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
            }
        }
        rule.waitUntil(5_000) { panels.controlsView != null }
    }

    @Test fun leavingSettingsReleasesInputAndReturningRestoresControls() {
        lateinit var navController: androidx.navigation.NavHostController
        rule.setContent {
            navController = androidx.navigation.compose.rememberNavController()
            SettingsPanelHost(navController, panels, enabled = true)
            androidx.navigation.compose.NavHost(navController, startDestination = Screen.Settings.route) {
                navigation(startDestination = Screen.SettingsGames.route, route = Screen.Settings.route) {
                    composable(Screen.SettingsGames.route) { entry ->
                        val parent = rememberCompositionContext()
                        val pane = remember(parent) {
                            SettingsPane(parent, back = {}, close = {}, selectCategory = { _, _ -> }) {
                                Text("Settings controls")
                            }
                        }
                        SideEffect {
                            if (navController.currentBackStackEntry === entry) panels.show(pane)
                        }
                    }
                    composable(Screen.EmulatorConfig.route) { Text("Emulator configuration") }
                    composable(Screen.LockedModeGames.route) { Text("Allowed games") }
                    composable(Screen.LockedModeApps.route) { Text("Allowed apps") }
                }
                composable(Screen.Scan.route) { Text("Scan destination") }
                composable(Screen.ScrapeProgress.route) { Text("Scrape destination") }
            }
            panels.pane?.let { SettingsControls(panels, it) }
        }
        try {
            for (route in listOf(Screen.Scan.route, Screen.ScrapeProgress.route())) {
                rule.onNodeWithText("Settings controls").assertExists()
                val oldView = rule.runOnIdle { panels.controlsView!! }
                rule.runOnIdle {
                    panels.focusControls()
                    navController.navigate(route)
                    // Navigation clears routing synchronously, before another key arrives.
                    assertNull(panels.pane)
                    assertFalse(panels.dispatch(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_B)))
                    assertFalse(panels.dispatch(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN)))
                    assertFalse(panels.back())
                    assertNotNull(navController.getBackStackEntry(Screen.Settings.route))
                }
                rule.waitForIdle()
                rule.onNodeWithText("Settings controls").assertDoesNotExist()
                rule.runOnIdle {
                    assertNull(panels.controlsView)
                    assertFalse(oldView.isAttachedToWindow)
                    navController.popBackStack()
                }
                rule.onNodeWithText("Settings controls").assertExists()
                rule.runOnIdle { assertNotNull(panels.controlsView) }
            }
            // Nested settings destinations retain the portal through graph membership.
            for (route in listOf(Screen.EmulatorConfig.route, Screen.LockedModeGames.route, Screen.LockedModeApps.route)) {
                rule.runOnIdle {
                    val pane = panels.pane
                    navController.navigate(route)
                    assertSame(pane, panels.pane)
                    navController.popBackStack()
                }
                rule.waitForIdle()
            }
        } catch (failure: Throwable) {
            saveKeyboardDiagnostics()
            throw failure
        } finally {
            rule.runOnIdle { panels.reset() }
        }
    }

    @Test fun hostReleasesSessionOnDisplayLossAndDisposal() {
        val enabled = mutableStateOf(true)
        val mounted = mutableStateOf(true)
        rule.setContent {
            if (mounted.value) {
                val navController = androidx.navigation.compose.rememberNavController()
                SettingsPanelHost(navController, panels, enabled.value)
                androidx.navigation.compose.NavHost(navController, startDestination = Screen.Settings.route) {
                    navigation(startDestination = Screen.SettingsGames.route, route = Screen.Settings.route) {
                        composable(Screen.SettingsGames.route) {
                            val parent = rememberCompositionContext()
                            val pane = remember(parent) {
                                SettingsPane(parent, back = {}, close = {}, selectCategory = { _, _ -> }) {
                                    Text("Hosted controls")
                                }
                            }
                            if (enabled.value) SideEffect { panels.show(pane) }
                        }
                    }
                }
                panels.pane?.let { SettingsControls(panels, it) }
            }
        }
        rule.onNodeWithText("Hosted controls").assertExists()
        val originalView = rule.runOnIdle { panels.controlsView!! }
        rule.runOnIdle { enabled.value = false }
        rule.waitForIdle()
        rule.runOnIdle {
            assertNull(panels.pane)
            assertNull(panels.controlsView)
            assertFalse(originalView.isAttachedToWindow)
            assertFalse(panels.back())
            enabled.value = true
        }
        rule.onNodeWithText("Hosted controls").assertExists()
        val replacementView = rule.runOnIdle { panels.controlsView!! }
        rule.runOnIdle { mounted.value = false }
        rule.waitForIdle()
        rule.runOnIdle {
            assertNull(panels.pane)
            assertNull(panels.controlsView)
            assertFalse(replacementView.isAttachedToWindow)
            assertFalse(panels.dispatch(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_B)))
        }
    }

    @Test fun presentationDialogHonorsBackPolicyAndItsUpdates() {
        dismissOnBack.value = false
        open()
        try {
            rule.onNodeWithText("Open dialog").performClick()
            rule.onNodeWithText("Dismiss dialog").assertExists()
            rule.runOnIdle {
                assertTrue(panels.dispatch(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_B)))
                assertTrue(panels.dispatch(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_B)))
                assertTrue(panels.dispatch(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK)))
                assertTrue(panels.back())
                assertTrue(dialog.value)
                assertNotNull(panels.pane)
                dismissOnBack.value = true
            }
            rule.waitForIdle()
            rule.runOnIdle { assertTrue(panels.back()) }
            rule.waitForIdle()
            rule.runOnIdle {
                assertFalse(dialog.value)
                assertNotNull(panels.pane)
            }
        } catch (failure: Throwable) {
            saveKeyboardDiagnostics()
            throw failure
        } finally {
            rule.runOnIdle { panels.reset(); presentation?.dismiss() }
        }
    }

    @Test fun replacementDialogAndDisplayRemovalReleaseThePortal() {
        open()
        try {
            rule.onNodeWithText("Appearance").assertExists()
            rule.onNodeWithText("Activate").performClick()
            rule.runOnIdle { assertEquals(1, activations) }
            rule.onNodeWithText("Open dialog").performClick()
            rule.onNodeWithText("Dismiss dialog").assertExists().performClick()
            rule.runOnIdle { assertFalse(dialog.value) }
            rule.onNodeWithText("Open dialog").performClick()
            rule.onNodeWithText("Dismiss dialog").assertExists()
            rule.runOnIdle {
                panels.focusCategories()
                assertTrue(panels.dispatch(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_B)))
            }
            rule.waitForIdle()
            rule.runOnIdle {
                assertFalse(dialog.value)
                assertNotNull(panels.pane)
            }
            val controlsView = rule.runOnIdle { panels.controlsView }
            // Navigation may dispose the outgoing category a frame before composing its
            // replacement. The graph still owns the portal during this handoff.
            rule.runOnIdle { page.value = null }
            rule.waitForIdle()
            rule.runOnIdle {
                assertNotNull("The portal stays visible between destinations", panels.pane)
                assertSame(controlsView, panels.controlsView)
                assertTrue(controlsView!!.isAttachedToWindow)
            }
            rule.runOnIdle { page.value = "Media" }
            rule.onNodeWithText("Media").assertExists()
            rule.onNodeWithText("Appearance").assertDoesNotExist()
            rule.runOnIdle {
                assertSame("Category switches keep the controls View attached", controlsView, panels.controlsView)
                assertTrue(controlsView!!.isAttachedToWindow)
            }
            rule.waitUntil(5_000) { panels.controlsView != null }
            rule.runOnIdle {
                panels.focusControls()
                panels.dispatch(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_B, 0))
                assertFalse(panels.controlsFocused)
                panels.dispatch(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_B, 1))
                assertNotNull(panels.pane)
                // The host resets the session when the display goes away.
                panels.reset()
                presentation?.dismiss()
            }
            rule.waitForIdle()
            rule.runOnIdle { assertNull(panels.pane); assertNull(panels.controlsView) }
        } catch (failure: Throwable) {
            saveKeyboardDiagnostics()
            throw failure
        } finally {
            rule.runOnIdle { panels.reset(); presentation?.dismiss() }
        }
    }
    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
    ).use { it.readBytes().toString(Charsets.UTF_8) }

    private fun saveKeyboardDiagnostics() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = java.io.File(context.getExternalFilesDir(null), "ime-diagnostics").apply { mkdirs() }
        for (service in listOf("window", "input_method", "display")) {
            java.io.File(directory, "$service.txt").writeText(shell("dumpsys $service"))
        }
    }

    private fun imeDisplays(): List<Int> {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val info = automation.serviceInfo
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        automation.serviceInfo = info
        val windows = automation.windowsOnAllDisplays
        return (0 until windows.size()).flatMap { i ->
            windows.valueAt(i).filter { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
                .map { windows.keyAt(i) }
        }
    }

    private fun showKeyboard(label: String) {
        val node = rule.onNodeWithText(label).fetchSemanticsNode()
        val center = node.boundsInWindow.center
        val displayId = rule.runOnIdle { panels.controlsView!!.display.displayId }
        val offset = rule.runOnIdle {
            val root = (dialogView ?: panels.controlsView!!).rootView
            val screen = IntArray(2).also { root.getLocationOnScreen(it) }
            val window = IntArray(2).also { root.getLocationInWindow(it) }
            intArrayOf(screen[0] - window[0], screen[1] - window[1])
        }
        shell("input -d $displayId tap ${(center.x + offset[0]).toInt()} ${(center.y + offset[1]).toInt()}")
        rule.waitUntil(5_000) {
            rule.runOnIdle { (dialogView ?: panels.controlsView!!).rootView.hasWindowFocus() }
        }
        rule.waitUntil(10_000) { imeDisplays().isNotEmpty() }
        val expected = InstrumentationRegistry.getArguments().getString("imeDisplay")?.toInt()
        if (expected != null) assertEquals(listOf(expected), imeDisplays())
        if (expected == Display.DEFAULT_DISPLAY) rule.runOnIdle {
            assertTrue("The menu window must expose fallback IME visibility",
                ViewCompat.getRootWindowInsets(menuView!!)?.isVisible(WindowInsetsCompat.Type.ime()) == true)
        }
        shell("input -d $displayId text x")
        rule.waitUntil(5_000) { input.value.endsWith("x") }
    }

    private fun pressBack(kind: Int) = rule.runOnIdle {
        when (kind) {
            0 -> {
                assertTrue(panels.dispatch(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_B)))
                assertTrue(panels.dispatch(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_B)))
            }
            1 -> {
                assertTrue(panels.dispatch(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK)))
                assertTrue(panels.dispatch(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK)))
            }
            else -> assertTrue(panels.back()) // The application's system-Back callback.
        }
    }

    @Test fun BackDismissesTheKeyboardBeforeLeavingControls() {
        open()
        try {
            for (kind in 0..2) {
                rule.runOnIdle { panels.focusControls(); input.value = "" }
                showKeyboard("Input")
                pressBack(kind)
                rule.waitUntil(5_000) { imeDisplays().isEmpty() }
                rule.runOnIdle {
                    assertTrue(panels.controlsFocused)
                    assertNotNull(panels.pane)
                    assertEquals(com.gamelaunch.frontend.ui.screen.settings.SettingsCategory.APPEARANCE, panels.selectedCategory)
                }
                pressBack(kind)
                rule.runOnIdle { assertFalse(panels.controlsFocused); assertNotNull(panels.pane) }
            }
        } catch (failure: Throwable) {
            saveKeyboardDiagnostics()
            throw failure
        } finally {
            rule.runOnIdle { panels.reset(); presentation?.dismiss() }
        }
    }

    @Test fun dialogKeyboardDismissalHonorsBothBackPolicies() {
        open()
        try {
            for (policy in listOf(true, false)) for (kind in 0..2) {
                rule.runOnIdle { dismissOnBack.value = policy; input.value = ""; panels.focusControls() }
                rule.onNodeWithText("Open dialog").performClick()
                showKeyboard("Dialog input")
                pressBack(kind)
                rule.waitUntil(5_000) { imeDisplays().isEmpty() }
                rule.runOnIdle { assertTrue(dialog.value); assertTrue(panels.controlsFocused) }
                pressBack(kind)
                rule.runOnIdle { assertEquals(!policy, dialog.value) }
                if (!policy) rule.onNodeWithText("Dismiss dialog").performClick()
            }
        } catch (failure: Throwable) {
            saveKeyboardDiagnostics()
            throw failure
        } finally {
            rule.runOnIdle { panels.reset(); presentation?.dismiss() }
        }
    }

    @Test fun removingEditorDisplayWhileTypingReleasesKeyboardWindows() {
        assumeTrue("Host must remove the emulator display after the ready marker",
            InstrumentationRegistry.getArguments().getString("removeDisplay") == "true")
        open()
        try {
            rule.runOnIdle { panels.focusControls() }
            showKeyboard("Input")
            val view = rule.runOnIdle { panels.controlsView!! }
            android.util.Log.i("EorImeTest", "REMOVE_EDITOR_DISPLAY_READY")
            rule.waitUntil(20_000) { panels.pane == null && panels.controlsView == null }
            rule.waitUntil(5_000) { imeDisplays().isEmpty() }
            rule.runOnIdle { assertFalse(view.isAttachedToWindow); assertFalse(panels.back()) }
        } catch (failure: Throwable) {
            saveKeyboardDiagnostics()
            throw failure
        } finally {
            rule.runOnIdle { panels.reset(); presentation?.dismiss() }
        }
    }

    @Test fun leavingSettingsWhileEditingReleasesKeyboardWindows() {
        open()
        try {
            rule.runOnIdle { panels.focusControls() }
            showKeyboard("Input")
            rule.runOnIdle { panels.reset() }
            rule.waitUntil(5_000) { imeDisplays().isEmpty() }
            rule.runOnIdle {
                assertNull(panels.controlsView)
                assertFalse(panels.back())
            }
        } catch (failure: Throwable) {
            saveKeyboardDiagnostics()
            throw failure
        } finally {
            rule.runOnIdle { panels.reset(); presentation?.dismiss() }
        }
    }

    @Test fun activityAlertReceivesControllerBackBeforeTheCategories() {
        rule.setContent {
            CompositionLocalProvider(LocalSettingsPanels provides panels) {
                val parent = rememberCompositionContext()
                val pane = remember(parent) {
                    SettingsPane(parent, back = {}, close = { panels.reset() }, selectCategory = { _, _ -> }) {
                        Button(onClick = { dialog.value = true }) { Text("Open alert") }
                        if (dialog.value) {
                            SettingsAlertDialog(
                                onDismissRequest = { dialog.value = false },
                                confirmButton = { Button(onClick = { dialog.value = false }) { Text("Confirm") } },
                                text = { Text("Activity dialog") },
                            )
                        }
                    }
                }
                DisposableEffect(pane) {
                    panels.show(pane)
                    onDispose { panels.clear(pane) }
                }
                SettingsControls(panels, pane)
            }
        }
        try {
            rule.onNodeWithText("Open alert").performClick()
            rule.onNodeWithText("Activity dialog").assertExists()
            rule.runOnIdle {
                panels.focusCategories()
                assertTrue(panels.dispatch(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_B)))
            }
            rule.waitForIdle()
            rule.runOnIdle {
                assertFalse(dialog.value)
                assertNotNull(panels.pane)
            }
        } catch (failure: Throwable) {
            saveKeyboardDiagnostics()
            throw failure
        } finally {
            rule.runOnIdle { panels.reset() }
        }
    }

    @Test fun removingTheDisplayReleasesItsControls() {
        val manager = rule.activity.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val texture = SurfaceTexture(false).apply { setDefaultBufferSize(800, 600) }
        val surface = Surface(texture)
        val display = manager.createVirtualDisplay(
            "Settings portal removal test", 800, 600, 160, surface,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION,
        )
        try {
            open(display.display)
            rule.onNodeWithText("Appearance").assertExists()
            rule.runOnIdle { display.release() }
            rule.waitUntil(5_000) { panels.pane == null && panels.controlsView == null }
            rule.onNodeWithText("Appearance").assertDoesNotExist()
        } catch (failure: Throwable) {
            saveKeyboardDiagnostics()
            throw failure
        } finally {
            rule.runOnIdle { panels.reset(); presentation?.dismiss(); display.release() }
            surface.release()
            texture.release()
        }
    }

}
