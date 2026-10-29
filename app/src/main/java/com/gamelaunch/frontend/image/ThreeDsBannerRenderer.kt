package com.gamelaunch.frontend.image

import android.os.Build
import android.util.Log
import androidx.annotation.Keep
import javax.inject.Inject
import javax.inject.Singleton

/** Renders one banner offscreen on the device GPU. Call only from a background dispatcher. */
@Keep
@Singleton
class ThreeDsBannerRenderer @Inject constructor() {
    private val available by lazy {
        Build.VERSION.SDK_INT >= 24 && runCatching { System.loadLibrary("eor_banner") }.isSuccess
    }

    @Synchronized
    fun render(graphics: ByteArray): ByteArray? {
        if (!available || graphics.size > 0x80000) return null
        return runCatching { nativeRender(graphics) }
            .onSuccess { if (it != null) Log.i("ThreeDsBanner", "Rendered banner on device (${it.size} PNG bytes)") }
            .onFailure { Log.w("ThreeDsBanner", "Device could not render banner", it) }
            .getOrNull()
    }

    private external fun nativeRender(graphics: ByteArray): ByteArray?
}
