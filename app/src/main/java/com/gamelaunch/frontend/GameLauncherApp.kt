package com.gamelaunch.frontend

import android.app.Application
import android.os.StrictMode
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.Dispatchers

@HiltAndroidApp
class GameLauncherApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        // Emulators expect a raw file path / file:// URI to the ROM. On targetSdk >= 24 the
        // default VM policy throws FileUriExposedException when a file:// Uri crosses to another
        // app. Relaxing the VM policy (as RetroArch/Daijishō and other Android frontends do)
        // lets us hand the ROM path straight to each emulator, which then reads it with its own
        // storage permissions.
        StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().build())
    }

    /**
     * Box art is shown in dense grids and carousels, so artwork loading has to feel instant. A
     * generous in-memory cache keeps recently-seen covers ready while scrolling, a persistent disk
     * cache means scraped/remote art is only ever fetched once, and RGB_565 halves bitmap memory so
     * more covers stay resident. Cache headers are ignored so cached art is never re-validated.
     */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.30)
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
            // idle animation cycling the main thread at ~60fps. That throttling made covers trickle in
            // one-at-a-time on full while the lite build (no idle animation) applied them all at once.
            // Off-main, image loading is decoupled from the animation and covers land together on both.
            .interceptorDispatcher(Dispatchers.Default)
            .crossfade(120)
            .build()
}
