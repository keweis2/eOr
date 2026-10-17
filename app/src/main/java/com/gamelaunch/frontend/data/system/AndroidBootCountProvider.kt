package com.gamelaunch.frontend.data.system

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import com.gamelaunch.frontend.domain.lockedmode.UNKNOWN_BOOT_COUNT
import com.gamelaunch.frontend.domain.system.BootCountProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidBootCountProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : BootCountProvider {
    // BOOT_COUNT is Android 7+. The constant is inlined; on 6.0 the setting is absent and getInt throws,
    // which falls back to UNKNOWN_BOOT_COUNT.
    @SuppressLint("InlinedApi")
    override fun currentBootCount(): Int = runCatching {
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT)
    }.getOrDefault(UNKNOWN_BOOT_COUNT)
}
