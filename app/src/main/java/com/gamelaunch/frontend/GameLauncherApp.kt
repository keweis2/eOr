package com.gamelaunch.frontend

import android.app.Application
import android.os.StrictMode
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import dagger.hilt.android.HiltAndroidApp

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
            .crossfade(120)
            .build()
}
