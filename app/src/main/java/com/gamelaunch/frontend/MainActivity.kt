package com.gamelaunch.frontend

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
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
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.navigation.compose.rememberNavController
import coil.imageLoader
import coil.request.ImageRequest
import com.gamelaunch.frontend.domain.platform.PlatformDefinitions
import com.gamelaunch.frontend.domain.platform.sortedBySystems
import com.gamelaunch.frontend.domain.repository.GameRepository
import com.gamelaunch.frontend.domain.repository.MediaRepository
import com.gamelaunch.frontend.domain.repository.SettingsRepository
import com.gamelaunch.frontend.domain.usecase.AppUpdate
import com.gamelaunch.frontend.domain.usecase.CheckForUpdateUseCase
import com.gamelaunch.frontend.ui.component.LoadingScreen
import com.gamelaunch.frontend.ui.component.UpdateBanner
import com.gamelaunch.frontend.ui.navigation.AppNavGraph
import com.gamelaunch.frontend.ui.navigation.Screen
import com.gamelaunch.frontend.ui.navigation.backOrHome
import com.gamelaunch.frontend.ui.theme.AppTheme
import com.gamelaunch.frontend.ui.theme.BackgroundBranding
import com.gamelaunch.frontend.ui.theme.BackgroundImageMode
import com.gamelaunch.frontend.ui.theme.NavyBg
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var gameRepository: GameRepository
    @Inject lateinit var mediaRepository: MediaRepository
    @Inject lateinit var checkForUpdateUseCase: CheckForUpdateUseCase
    @Inject lateinit var syncthingController: com.gamelaunch.frontend.data.sync.SyncthingController

    // Set when a newer GitHub release is found; drives the in-app update banner.
    private val updateState = mutableStateOf<AppUpdate?>(null)

    // The branded splash stays up until cold-start data is loaded and the first screen's box art
    // is warmed in Coil's cache, so Home appears populated instead of grey-then-crossfading in.
    // Compose-observable so the in-app loading screen can react, not just the system splash.
    private val splashReady = mutableStateOf(false)

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

        // No SplashScreen API: on API 31+ it forces the system splash (which doesn't render on some
        // devices, e.g. the Retroid) and suppresses the window's starting background. Instead the
        // activity theme's windowBackground (@drawable/splash_bg — logo on navy) covers the
        // cold-start gap on every device, and the in-app LoadingScreen covers artwork warm-up.
        warmFirstScreen()

        // Edge-to-edge + full immersive: hide status bar and nav bar
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()

        requestStoragePermissions()
        requestAllFilesAccessIfNeeded()
        checkForUpdate()
        startSaveSyncIfEnabled()

        setContent {
            val darkMode by settingsRepository.darkMode.collectAsState(initial = false)

            // User's optional branded background: decode the processed mask off the main thread,
            // re-decoding only when the path changes, and hand it to the theme for AmbientBackground.
            val bgEnabled by settingsRepository.backgroundImageEnabled.collectAsState(initial = false)
            val bgPath by settingsRepository.backgroundImagePath.collectAsState(initial = "")
            val bgMode by settingsRepository.backgroundImageMode.collectAsState(initial = "FILL")
            val bgOpacity by settingsRepository.backgroundImageOpacity.collectAsState(initial = 0.15f)
            val brandingMask by produceState<ImageBitmap?>(null, bgEnabled, bgPath) {
                value = when {
                    !bgEnabled -> null
                    // A user-picked image takes precedence…
                    bgPath.isNotBlank() -> withContext(Dispatchers.IO) {
                        runCatching { BitmapFactory.decodeFile(bgPath)?.asImageBitmap() }.getOrNull()
                    }
                    // …otherwise fall back to the eOr donkey silhouette as a branded default.
                    else -> withContext(Dispatchers.IO) { donkeySilhouetteMask() }
                }
            }
            val branding = BackgroundBranding(
                enabled = bgEnabled && brandingMask != null,
                mask    = brandingMask,
                mode    = runCatching { BackgroundImageMode.valueOf(bgMode) }
                    .getOrDefault(BackgroundImageMode.FILL),
                opacity = bgOpacity
            )

            AppTheme(darkMode = darkMode, branding = branding) {
                Box(Modifier.fillMaxSize()) {
                val navController = rememberNavController()
                // Use null as initial so NavHost isn't created until we know the real value.
                // With initial = true (old code) the NavHost always initialized at Settings,
                // because the DataStore emit arrives after the first Compose frame.
                val isFirstLaunch by settingsRepository.isFirstLaunch.collectAsState(initial = null)

                // Show the in-app loading screen until artwork is warmed (splashReady) and the launch
                // destination is known. Guarantees a visible loading state even when the OS skips the
                // system splash (e.g. eOr launched as the home app on cold boot).
                when {
                    !splashReady.value || isFirstLaunch == null -> {
                        LoadingScreen()
                    }
                    else -> {
                        val firstLaunch = isFirstLaunch == true
                        val startDestination =
                            if (firstLaunch) Screen.Onboarding.route else Screen.Home.route
                        AppNavGraph(
                            navController    = navController,
                            startDestination = startDestination
                        )
                        // System Back (the Retroid's B maps to it): pop the nav stack so the
                        // detail/settings screens return to where they came from. If the stack has
                        // nothing to pop — e.g. eOr was resumed on a sub-screen via a launcher
                        // intent (singleTask) — fall back to Home so Back never dead-ends. At the
                        // Home root nothing pops and we stay, instead of exiting.
                        // Focused screens (e.g. the home game grid) handle Back in their own
                        // onKeyEvent first, so this only runs when nothing else consumed it.
                        BackHandler { navController.backOrHome() }
                    }
                }

                updateState.value?.let { up ->
                    UpdateBanner(
                        versionName = up.versionName,
                        onOpen = {
                            runCatching {
                                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(up.releaseUrl)))
                            }
                            updateState.value = null
                        },
                        onDismiss = { updateState.value = null },
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                }
                }
            }
        }
    }

    /**
     * Warm the cold-start path behind the splash: resolve the launch destination and, for returning
     * users landing on Home, prefetch the box art of the first systems shown so the carousel/grid
     * renders with art already resident. Bounded by a hard timeout so a slow disk or huge library
     * never leaves the user staring at the logo.
     */
    private fun warmFirstScreen() {
        lifecycleScope.launch {
            withTimeoutOrNull(1200L) {
                if (!settingsRepository.isFirstLaunch.first()) {
                    prewarmFirstScreenArt()
                }
            }
            splashReady.value = true
        }
    }

    private suspend fun prewarmFirstScreenArt() {
        val ids = gameRepository.getDistinctPlatformIds().first()
        if (ids.isEmpty()) return
        val counts = gameRepository.getPlatformCounts().first()
        val sorts  = settingsRepository.systemSort.first()
        // Same ordering Home uses, so we warm the systems that actually appear first.
        val firstSystems = ids.sortedBySystems(
            sorts = sorts,
            displayName = { PlatformDefinitions.byId[it]?.displayName ?: it },
            gameCount   = { counts[it] ?: 0 }
        ).take(8)

        val paths = firstSystems
            .flatMap { mediaRepository.boxArtSampleForPlatform(it, 4) }
            .filter { it.isNotBlank() }
            .distinct()

        val loader = imageLoader
        coroutineScope {
            paths.map { path ->
                async {
                    runCatching {
                        loader.execute(
                            ImageRequest.Builder(this@MainActivity)
                                .data(File(path))
                                // Match AsyncGameArtwork's key so the warmed bitmap is a cache hit
                                // (and paints with no grey placeholder) when Home composes.
                                .memoryCacheKey(path)
                                .build()
                        )
                    }
                }
            }.awaitAll()
        }
    }

    /**
     * Rasterise the eOr donkey silhouette drawable into a square bitmap used as the default branded
     * background when the user enables a custom background without picking their own image. The
     * shape's alpha channel is what matters — [AmbientBackground] recolours it with the theme tint.
     */
    private fun donkeySilhouetteMask(): ImageBitmap? = runCatching {
        ContextCompat.getDrawable(this, R.drawable.ic_donkey_silhouette)
            ?.toBitmap(width = 512, height = 512)
            ?.asImageBitmap()
    }.getOrNull()

    /** If the user left Save Sync on, bring the Syncthing daemon back up when eOr launches. */
    private fun startSaveSyncIfEnabled() {
        lifecycleScope.launch {
            if (settingsRepository.saveSyncEnabled.first() && syncthingController.isSupported()) {
                com.gamelaunch.frontend.data.sync.SyncthingService.start(this@MainActivity)
            }
        }
    }

    /**
     * On launch, ask GitHub whether a newer release exists. If so, surface an in-app banner and —
     * once per new version — a system notification so users hear about it even outside the app.
     */
    private fun checkForUpdate() {
        lifecycleScope.launch {
            val update = checkForUpdateUseCase() ?: return@launch
            updateState.value = update
            val prefs = getSharedPreferences("app_updates", MODE_PRIVATE)
            if (prefs.getString("notified_version", null) != update.versionName) {
                notifyUpdate(update)
                prefs.edit().putString("notified_version", update.versionName).apply()
            }
        }
    }

    private fun notifyUpdate(update: AppUpdate) {
        val channelId = "app_updates"
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, "App updates", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        // Android 13+ requires the runtime POST_NOTIFICATIONS grant to post.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val pending = PendingIntent.getActivity(
            this, 0,
            Intent(Intent.ACTION_VIEW, Uri.parse(update.releaseUrl)),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_donkey_silhouette)
            .setContentTitle("Update available")
            .setContentText("eOr ${update.versionName} is available — tap to download")
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        nm.notify(1001, notification)
    }

    // Re-hide bars if Android temporarily shows them (e.g. swipe-from-edge)
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemBars()
        } else {
            // Reset joystick tracking when we lose focus (e.g. launching a game) so a stale
            // non-neutral axis value can't leave a synthesized DPAD direction "held" on return.
            lastAxisX = 0f; lastAxisY = 0f; lastHatX = 0f; lastHatY = 0f
        }
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
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.POST_NOTIFICATIONS
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
