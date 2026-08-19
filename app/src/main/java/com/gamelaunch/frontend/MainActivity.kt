package com.gamelaunch.frontend

import android.Manifest
import android.app.ActivityOptions
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.view.Display
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.rememberNavController
import coil.imageLoader
import coil.request.ImageRequest
import com.gamelaunch.frontend.domain.lockedmode.LockedModeRepository
import com.gamelaunch.frontend.domain.platform.PlatformDefinitions
import com.gamelaunch.frontend.domain.platform.sortedBySystems
import com.gamelaunch.frontend.domain.repository.GameRepository
import com.gamelaunch.frontend.domain.repository.MediaRepository
import com.gamelaunch.frontend.domain.repository.SettingsRepository
import com.gamelaunch.frontend.domain.usecase.AppUpdate
import com.gamelaunch.frontend.domain.usecase.CheckForUpdateUseCase
import com.gamelaunch.frontend.ui.component.LoadingScreen
import com.gamelaunch.frontend.ui.component.UpdateBanner
import com.gamelaunch.frontend.platform.display.DualScreenManager
import com.gamelaunch.frontend.ui.dualscreen.ArtworkBus
import com.gamelaunch.frontend.ui.dualscreen.GameSessionState
import com.gamelaunch.frontend.ui.dualscreen.LocalDualScreenActive
import com.gamelaunch.frontend.ui.dualscreen.LocalGameSessionActive
import com.gamelaunch.frontend.ui.perf.LocalReduceMotion
import com.gamelaunch.frontend.ui.perf.PerformanceState
import com.gamelaunch.frontend.ui.navigation.AppNavGraph
import com.gamelaunch.frontend.ui.navigation.Screen
import com.gamelaunch.frontend.ui.navigation.backOrHome
import com.gamelaunch.frontend.ui.systemui.SystemNavigationLockHost
import com.gamelaunch.frontend.ui.theme.AppTheme
import com.gamelaunch.frontend.ui.theme.BackgroundBranding
import com.gamelaunch.frontend.ui.theme.CardColorConfig
import com.gamelaunch.frontend.ui.theme.CardColorScheme
import com.gamelaunch.frontend.ui.theme.LocalCardColorScheme
import com.gamelaunch.frontend.ui.theme.BackgroundImageMode
import com.gamelaunch.frontend.ui.theme.NavyBg
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var gameRepository: GameRepository
    @Inject lateinit var mediaRepository: MediaRepository
    @Inject lateinit var lockedModeRepository: LockedModeRepository
    @Inject lateinit var checkForUpdateUseCase: CheckForUpdateUseCase
    @Inject lateinit var launchLibraryScanner: com.gamelaunch.frontend.domain.usecase.LaunchLibraryScanner
    @Inject lateinit var syncthingController: com.gamelaunch.frontend.data.sync.SyncthingController
    @Inject lateinit var syncEngineManager: com.gamelaunch.frontend.data.sync.SyncEngineManager
    @Inject lateinit var friendRepository: com.gamelaunch.frontend.domain.repository.FriendRepository
    @Inject lateinit var pendingFriendLink: com.gamelaunch.frontend.domain.friends.PendingFriendLink
    @Inject lateinit var artworkBus: ArtworkBus
    @Inject lateinit var performanceState: PerformanceState
    @Inject lateinit var gameSessionState: GameSessionState

    // True after a game was launched on the top panel and eOr lost focus to it; the next focus
    // regain means the user quit back to eOr, so we restore the artwork screen.
    private var awaitingGameReturn = false

    // Drives the second (artwork) screen on dual-screen handhelds; a no-op on single-screen devices.
    private lateinit var dualScreenManager: DualScreenManager

    // Set when a newer GitHub release is found; drives the in-app update banner.
    private val updateState = mutableStateOf<AppUpdate?>(null)
    // Monotonic timestamp of the last update check. Foreground checks are throttled so returning to
    // the app re-checks GitHub without hammering its API (unauthenticated: 60 req/hr) during
    // frequent game-launch/return cycles. 0 = never checked, so the first check always runs.
    private var lastUpdateCheckMs = 0L

    // MainActivity is singleTask, so pressing the system Home button while eOr is already running
    // delivers a new MAIN/HOME intent instead of recreating the Activity. Incrementing this value
    // lets Compose reset its otherwise-preserved navigation stack to the library root.
    private val homeNavigationRequest = mutableIntStateOf(0)

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

        dualScreenManager = DualScreenManager(this, artworkBus)

        // On top-primary dual-screen devices (e.g. AYN Thor) the interactive menu belongs on the
        // *secondary* display, so relaunch there once before any UI setup. This is a no-op on the
        // Anbernic RG DS and on every single-screen device (requiredMenuDisplayId() == DEFAULT_DISPLAY),
        // and a one-shot intent guard prevents any relaunch loop.
        if (relaunchOnMenuDisplayIfNeeded()) return

        observeDualScreenPreferences()

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
        // Update check runs from onStart() so it fires on every return to the foreground, not just
        // cold start — otherwise an update that lands while the app is open is only noticed after a
        // force-close and reopen.
        startSaveSyncIfEnabled()
        startLibraryRefreshWhileForeground()
        handleFriendDeepLink(intent)

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

            val dualScreenActive by dualScreenManager.active.collectAsState()
            val reduceMotion by performanceState.reduced.collectAsState()
            val gameSessionActive by gameSessionState.launchedOnTop.collectAsState()

            // Home-card colour scheme (rainbow / black & white / monochrome) — read by tileColor().
            val cardScheme by settingsRepository.cardColorScheme
                .collectAsState(initial = CardColorScheme.RAINBOW)
            val cardMonoArgb by settingsRepository.cardMonoColor
                .collectAsState(initial = 0xFF3E7BFF.toInt())
            val cardColorConfig = remember(cardScheme, cardMonoArgb) {
                CardColorConfig(scheme = cardScheme, monochromeSeed = Color(cardMonoArgb))
            }

            AppTheme(darkMode = darkMode, branding = branding) {
              CompositionLocalProvider(
                  LocalDualScreenActive provides dualScreenActive,
                  LocalReduceMotion provides reduceMotion,
                  LocalGameSessionActive provides gameSessionActive,
                  LocalCardColorScheme provides cardColorConfig
              ) {
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
                        LaunchedEffect(navController, homeNavigationRequest.intValue) {
                            if (homeNavigationRequest.intValue > 0) {
                                // Don't yank a first-launch user out of onboarding, and don't
                                // navigate when already at the library root.
                                val current = navController.currentDestination?.route
                                if (current != Screen.Home.route &&
                                    current != Screen.Onboarding.route
                                ) {
                                    navController.navigate(Screen.Home.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            inclusive = true
                                        }
                                    }
                                }
                                // Consume the request so a later recomposition that re-subscribes
                                // this effect can't replay the navigation.
                                homeNavigationRequest.intValue = 0
                            }
                        }
                        // Tell the artwork (top) screen when we're in the Settings area, so it shows
                        // a gear instead of the last game's art. Driven here (not by HomeViewModel)
                        // because HomeViewModel's selection stream doesn't know about navigation.
                        LaunchedEffect(navController) {
                            navController.currentBackStackEntryFlow.collect { entry ->
                                val route = entry.destination.route
                                artworkBus.setSettingsActive(
                                    route == Screen.Settings.route ||
                                    route == Screen.EmulatorConfig.route
                                )
                            }
                        }
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

                // Dual-screen: while a game is running on the top panel the bottom stays resumed
                // (Android multi-resume), so dim it down to ~18% brightness (a 0.82 black scrim) to
                // push focus to the game up top. gameSessionActive is only ever set for a top-panel
                // launch, so it already implies dual-screen — gate on it alone (an out-of-sync
                // dualScreenActive would otherwise suppress the dim). Fades in/out so returning to
                // eOr (gameSessionState.end()) restores the menu smoothly.
                val bottomDim by animateFloatAsState(
                    targetValue = if (gameSessionActive) 0.82f else 0f,
                    animationSpec = tween(durationMillis = 300),
                    label = "bottomScreenDim"
                )
                if (bottomDim > 0f) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = bottomDim))
                    )
                }

                SystemNavigationLockHost()
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
        val locked = lockedModeRepository.isLocked()
        val ids = gameRepository.getDistinctPlatformIds(locked).first()
        if (ids.isEmpty()) return
        val counts = gameRepository.getPlatformCounts(locked).first()
        val sorts  = settingsRepository.systemSort.first()
        // Same ordering Home uses, so we warm the systems that actually appear first.
        val firstSystems = ids.sortedBySystems(
            sorts = sorts,
            displayName = { PlatformDefinitions.byId[it]?.displayName ?: it },
            gameCount   = { counts[it] ?: 0 }
        ).take(8)

        val paths = firstSystems
            .flatMap { mediaRepository.boxArtSampleForPlatform(it, 4, locked) }
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

    /** Feed the persisted dual-screen prefs (enable + manual swap) and dark-mode into the manager. */
    private fun observeDualScreenPreferences() {
        lifecycleScope.launch {
            combine(
                settingsRepository.dualScreenEnabled,
                settingsRepository.dualScreenSwap
            ) { enabled, swap -> enabled to swap }
                .collect { (enabled, swap) -> dualScreenManager.setPreferences(enabled, swap) }
        }
        lifecycleScope.launch {
            settingsRepository.darkMode.collect { artworkBus.setDarkMode(it) }
        }
        // "Run lighter" signal: the lite build (LOW_POWER) targets low-power chipsets (RK3568/RK3566
        // and similar) and is always reduced; the full build reduces only when the user enables
        // Performance mode. Deliberately NOT tied to dual-screen presence — a dual-screen device is
        // not necessarily low-powered (e.g. AYN Thor), and the RG DS already runs the lite build.
        lifecycleScope.launch {
            settingsRepository.performanceMode
                .collect { pref -> performanceState.set(BuildConfig.LOW_POWER || pref) }
        }
        // Hide the top artwork overlay while a game is running on the top panel so it doesn't cover
        // the game; it's restored when the user returns (see onWindowFocusChanged).
        lifecycleScope.launch {
            gameSessionState.launchedOnTop.collect { onTop ->
                dualScreenManager.setArtworkSuspended(onTop)
            }
        }
    }

    /**
     * MENU_ON_SECONDARY devices (e.g. AYN Thor): move the interactive Activity onto the secondary
     * (bottom) display once, so the menu ends up on the bottom panel and artwork on the top. Returns
     * true if a relaunch was started (the caller must then abort the rest of onCreate).
     *
     * Safe by construction: [DualScreenManager.requiredMenuDisplayId] returns DEFAULT_DISPLAY on the
     * RG DS and on every single-screen device, and a one-shot intent extra prevents any relaunch
     * loop. NOTE: implemented but not yet validated on real Thor hardware.
     */
    private fun relaunchOnMenuDisplayIfNeeded(): Boolean {
        if (intent.getBooleanExtra(EXTRA_DS_RELAUNCHED, false)) return false
        val target = dualScreenManager.requiredMenuDisplayId()
        if (target == Display.DEFAULT_DISPLAY) return false
        if (DualScreenManager.currentDisplayId(this) == target) return false
        return runCatching {
            val options = ActivityOptions.makeBasic().setLaunchDisplayId(target)
            val relaunch = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                putExtra(EXTRA_DS_RELAUNCHED, true)
            }
            startActivity(relaunch, options.toBundle())
            finish()
            true
        }.getOrDefault(false)
    }

    /** If the user left Save Sync on, bring the Syncthing daemon back up when eOr launches. */
    private fun startSaveSyncIfEnabled() {
        lifecycleScope.launch {
            // Starts the daemon if Save Sync OR Friends is enabled; then bring friends up to date.
            syncEngineManager.refresh()
            if (settingsRepository.friendsEnabled.first() && syncthingController.isSupported()) {
                runCatching { friendRepository.publishMyProfile() }
                runCatching { friendRepository.refreshFriends() }
            }
        }
    }

    /** Parse an incoming eor://friend/... deep link and stage it for a user-confirmed add. */
    private fun handleFriendDeepLink(intent: Intent?) {
        val data = intent?.data ?: return
        if (intent.action != Intent.ACTION_VIEW) return
        if (!data.scheme.equals(com.gamelaunch.frontend.domain.friends.FriendCode.SCHEME, ignoreCase = true)) return
        com.gamelaunch.frontend.domain.friends.FriendCode.parse(data.toString())?.let { pendingFriendLink.offer(it) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleFriendDeepLink(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            homeNavigationRequest.intValue++
        }
    }

    /**
     * On launch, ask GitHub whether a newer release exists. If so, surface an in-app banner and —
     * once per new version — a system notification so users hear about it even outside the app.
     */
    override fun onStart() {
        super.onStart()
        // Re-check for updates whenever the app comes to the foreground (cold start included), so a
        // release published while the app is open/backgrounded surfaces without a force-close.
        checkForUpdate()
        // Attach the artwork screen (if a second display is present). Skipped on the finishing
        // instance during a MENU_ON_SECONDARY relaunch.
        if (::dualScreenManager.isInitialized && !isFinishing) dualScreenManager.start()
    }

    override fun onStop() {
        super.onStop()
        // Release the second screen while eOr is backgrounded (e.g. a game/emulator launched) so it
        // can take over the top panel; re-attached in onStart.
        if (::dualScreenManager.isInitialized) dualScreenManager.stop()
    }

    /**
     * Keep the library in sync with FTP/SD-card additions while this Activity is visible.
     *
     * Full build polls every [FOREGROUND_LIBRARY_SCAN_INTERVAL_MS] so uploads appear live without
     * leaving Home. The lite build (LOW_POWER — weak RK356x chipsets) scans once per foreground
     * entry instead: continuous polling isn't worth the wakeups there, and additions are still
     * picked up on the next launch or resume.
     */
    private fun startLibraryRefreshWhileForeground() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                if (BuildConfig.LOW_POWER) {
                    launchLibraryScanner.scan()
                } else {
                    while (isActive) {
                        launchLibraryScanner.scan()
                        // The probe does no hashing or DB writes unless it finds a new path. Thirty
                        // seconds keeps FTP additions responsive without continuously walking the card.
                        delay(FOREGROUND_LIBRARY_SCAN_INTERVAL_MS)
                    }
                }
            }
        }
    }

    private fun checkForUpdate() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastUpdateCheckMs < UPDATE_CHECK_INTERVAL_MS) return
        lastUpdateCheckMs = now
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
            // Returned from a game that was on the top panel → restore the artwork screen.
            if (awaitingGameReturn) {
                awaitingGameReturn = false
                gameSessionState.end()
            }
        } else {
            // Reset joystick tracking when we lose focus (e.g. launching a game) so a stale
            // non-neutral axis value can't leave a synthesized DPAD direction "held" on return.
            lastAxisX = 0f; lastAxisY = 0f; lastHatX = 0f; lastHatY = 0f
            // If a game was just placed on the top panel, this focus-loss is it taking over — arm
            // the return detector so the next focus regain restores the artwork.
            if (gameSessionState.launchedOnTop.value) awaitingGameReturn = true
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

    companion object {
        private const val FOREGROUND_LIBRARY_SCAN_INTERVAL_MS = 30_000L
        // Minimum gap between foreground update checks. Long enough to spare GitHub's API during
        // rapid game-launch/return cycles, short enough to notice a new release soon after returning.
        private const val UPDATE_CHECK_INTERVAL_MS = 15 * 60 * 1000L
        // One-shot guard so a MENU_ON_SECONDARY relaunch (Thor) can never loop.
        private const val EXTRA_DS_RELAUNCHED = "dual_screen_relaunched"
    }
}
