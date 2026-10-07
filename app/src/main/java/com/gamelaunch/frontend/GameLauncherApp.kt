package com.gamelaunch.frontend

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.StrictMode
import androidx.core.content.ContextCompat
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.gamelaunch.frontend.data.catalog.CatalogRepository
import com.gamelaunch.frontend.data.playtime.PlaySessionTracker
import com.gamelaunch.frontend.data.preferences.AppDataStore
import com.gamelaunch.frontend.domain.platform.PlatformDefinitions
import com.gamelaunch.frontend.domain.repository.EmulatorRepository
import com.gamelaunch.frontend.image.BoxArtThumbnailInterceptor
import com.gamelaunch.frontend.image.BoxArtThumbnailStore
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class GameLauncherApp : Application(), ImageLoaderFactory {

    @Inject lateinit var appDataStore: AppDataStore
    @Inject lateinit var catalogRepository: CatalogRepository
    @Inject lateinit var emulatorRepository: EmulatorRepository
    @Inject lateinit var playSessionTracker: PlaySessionTracker

    override fun onCreate() {
        super.onCreate()
        // Emulators expect a raw file path / file:// URI to the ROM. On targetSdk >= 24 the
        // default VM policy throws FileUriExposedException when a file:// Uri crosses to another
        // app. Relaxing the VM policy (as RetroArch/Daijishō and other Android frontends do)
        // lets us hand the ROM path straight to each emulator, which then reads it with its own
        // storage permissions.
        StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().build())

        // Synchronous on purpose: the ROM scan (kicked off from MainActivity) deletes games whose
        // platform it can't detect, so a downloaded catalog's extra platforms must be in place first.
        // It's one small file read.
        catalogRepository.loadCached()

        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        watchScreenForPlaytime(appScope)
        // One-time: encrypt any secrets left in plaintext by installs that predate SecretCipher.
        appScope.launch {
            runCatching { appDataStore.migrateSecretsIfNeeded() }
        }
        // Pick up new platforms / emulator fixes; used from the next scan or launch onward.
        appScope.launch {
            val before = PlatformDefinitions.catalog
            if (catalogRepository.refresh() is CatalogRepository.RefreshResult.Updated) {
                runCatching {
                    // Core fixes reach existing mappings; new systems get an emulator (gaps only).
                    emulatorRepository.followCatalogCores(before, PlatformDefinitions.catalog)
                    emulatorRepository.assignMissing()
                }
            }
        }
    }

    /**
     * Pause play sessions while the screen is off, so a game left paused overnight doesn't count.
     * Screen on/off are only delivered to runtime-registered receivers, which live as long as
     * eOr's process — if that dies mid-game the tracker caps the unobserved stretch instead.
     */
    private fun watchScreenForPlaytime(scope: CoroutineScope) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> scope.launch { runCatching { playSessionTracker.onScreenOff() } }
                    Intent.ACTION_SCREEN_ON -> scope.launch { runCatching { playSessionTracker.onScreenOn() } }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    /**
     * Box art is shown in dense grids and carousels, so artwork loading has to feel instant. A
     * generous in-memory cache keeps recently-seen covers ready while scrolling, a persistent disk
     * cache means scraped/remote art is only ever fetched once, and RGB_565 halves bitmap memory so
     * more covers stay resident. Cache headers are ignored so cached art is never re-validated.
     */
    override fun newImageLoader(): ImageLoader {
        // Full covers are decoded compactly (see BOX_ART_TILE_PX), so a bigger memory cache holds
        // hundreds of them and a whole system stays resident — the fix for covers reloading when you
        // scroll away and back. Lite keeps its original 0.30 budget.
        val memoryPercent = if (BuildConfig.LOW_POWER) 0.30 else 0.40

        val builder = ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(memoryPercent)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(512L * 1024 * 1024)
                    .build()
            }
            .allowRgb565(true)
            .respectCacheHeaders(false)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            // Run the request pipeline (memory-cache lookup, size resolution, dispatch, result apply)
            // off the main thread. Coil defaults this to Dispatchers.Main.immediate, which means every
            // tile's load competes for main-thread time — and the full build always has a focused-card
            // idle animation cycling the main thread at ~60fps. Off-main, image loading is decoupled
            // from the animation and covers land together. Unchanged from the original on both builds.
            .interceptorDispatcher(Dispatchers.Default)
            .crossfade(120)

        if (!BuildConfig.LOW_POWER) {
            // Serve box-art tiles from tiny on-disk thumbnails instead of the 0.5–1.3 MB source PNGs.
            // This is the fix for the real bottleneck: reading/decoding a full ~1 MB cover off the SD
            // card for every tile. See BoxArtThumbnails. Lite stays on the plain decode path.
            builder.components { add(BoxArtThumbnailInterceptor(BoxArtThumbnailStore(this@GameLauncherApp))) }
        }

        return builder.build()
    }
}
