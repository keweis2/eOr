package com.gamelaunch.frontend

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.SystemClock
import android.provider.Settings
import android.view.InputDevice
import android.view.KeyEvent as AndroidKeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.gamelaunch.frontend.domain.repository.SettingsRepository
import com.gamelaunch.frontend.ui.navigation.AppNavGraph
import com.gamelaunch.frontend.ui.navigation.Screen
import com.gamelaunch.frontend.ui.theme.AppTheme
import com.gamelaunch.frontend.ui.theme.NavyBg
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository

    // Track last axis values so we only fire once per threshold crossing
    private var lastAxisX   = 0f
    private var lastAxisY   = 0f
    private var lastHatX    = 0f
    private var lastHatY    = 0f

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* storage permissions handled — ROM folder picker and all-files access are the fallback */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-edge + full immersive: hide status bar and nav bar
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()

        requestStoragePermissions()
        requestAllFilesAccessIfNeeded()

        setContent {
            val darkMode by settingsRepository.darkMode.collectAsState(initial = false)
            AppTheme(darkMode = darkMode) {
                val navController = rememberNavController()
                // Use null as initial so NavHost isn't created until we know the real value.
                // With initial = true (old code) the NavHost always initialized at Settings,
                // because the DataStore emit arrives after the first Compose frame.
                val isFirstLaunch by settingsRepository.isFirstLaunch.collectAsState(initial = null)

                when (val firstLaunch = isFirstLaunch) {
                    null -> {
                        // DataStore hasn't emitted yet — show blank background for ~1 frame
                        Box(Modifier.fillMaxSize().background(NavyBg))
                    }
                    else -> {
                        val startDestination =
                            if (firstLaunch) Screen.Settings.route else Screen.Home.route
                        AppNavGraph(
                            navController    = navController,
                            startDestination = startDestination
                        )
                        // System Back (the Retroid's B maps to it): pop the nav stack so the
                        // detail/settings screens return to where they came from. At the launcher
                        // root nothing pops, so we consume it and stay instead of exiting.
                        // Focused screens (e.g. the home game grid) handle Back in their own
                        // onKeyEvent first, so this only runs when nothing else consumed it.
                        BackHandler { navController.popBackStack() }
                    }
                }
            }
        }
    }

    // Re-hide bars if Android temporarily shows them (e.g. swipe-from-edge)
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }


    /**
     * Convert left-stick and D-pad hat axis motion to discrete DPAD key events so
     * every screen's onKeyEvent handler gets a unified input stream regardless of
     * whether the user uses the physical D-pad or the analog stick.
     */
    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        if (ev.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
            && ev.action == MotionEvent.ACTION_MOVE) {

            val axisX = ev.getAxisValue(MotionEvent.AXIS_X)
            val axisY = ev.getAxisValue(MotionEvent.AXIS_Y)
            val hatX  = ev.getAxisValue(MotionEvent.AXIS_HAT_X)
            val hatY  = ev.getAxisValue(MotionEvent.AXIS_HAT_Y)
            val dead  = 0.5f

            injectIfCrossed(axisX, lastAxisX, dead, AndroidKeyEvent.KEYCODE_DPAD_RIGHT, AndroidKeyEvent.KEYCODE_DPAD_LEFT)
            injectIfCrossed(axisY, lastAxisY, dead, AndroidKeyEvent.KEYCODE_DPAD_DOWN,  AndroidKeyEvent.KEYCODE_DPAD_UP)
            injectIfCrossed(hatX,  lastHatX,  dead, AndroidKeyEvent.KEYCODE_DPAD_RIGHT, AndroidKeyEvent.KEYCODE_DPAD_LEFT)
            injectIfCrossed(hatY,  lastHatY,  dead, AndroidKeyEvent.KEYCODE_DPAD_DOWN,  AndroidKeyEvent.KEYCODE_DPAD_UP)

            lastAxisX = axisX; lastAxisY = axisY
            lastHatX  = hatX;  lastHatY  = hatY
            return true
        }
        return super.dispatchGenericMotionEvent(ev)
    }

    /**
     * Emit DPAD key events for an axis as it crosses the dead zone. Critically this also injects
     * ACTION_UP when the axis returns to (or passes through) neutral, so a released D-pad/stick
     * produces a real KeyUp — without it the hold-to-scroll repeat would never be cancelled and the
     * UI would scroll forever. A direction flip (e.g. right → left) releases the old direction and
     * presses the new one in the same step.
     */
    private fun injectIfCrossed(cur: Float, prev: Float, dead: Float, posCode: Int, negCode: Int) {
        val curDir  = if (cur  > dead) 1 else if (cur  < -dead) -1 else 0
        val prevDir = if (prev > dead) 1 else if (prev < -dead) -1 else 0
        if (curDir == prevDir) return

        val now = SystemClock.uptimeMillis()
        fun send(action: Int, dir: Int) {
            val code = if (dir > 0) posCode else negCode
            dispatchKeyEvent(AndroidKeyEvent(now, now, action, code, 0))
        }
        // Release whatever direction was held, then press the new one (either may be neutral).
        if (prevDir != 0) send(AndroidKeyEvent.ACTION_UP, prevDir)
        if (curDir  != 0) send(AndroidKeyEvent.ACTION_DOWN, curDir)
    }

    private fun requestStoragePermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        permissionLauncher.launch(permissions)
    }

    /**
     * On Android 11+ (API 30), direct file access to SD cards requires
     * MANAGE_EXTERNAL_STORAGE ("All files access"). We send the user to the
     * system settings page once if it hasn't been granted yet.
     */
    private fun requestAllFilesAccessIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            !Environment.isExternalStorageManager()
        ) {
            runCatching {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:$packageName")
                    }
                )
            }.onFailure {
                // Fallback for devices that don't support the per-app page
                runCatching {
                    startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                }
            }
        }
    }
}
