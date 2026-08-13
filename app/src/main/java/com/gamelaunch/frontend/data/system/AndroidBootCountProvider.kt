package com.gamelaunch.frontend.data.system

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
    override fun currentBootCount(): Int = runCatching {
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT)
    }.getOrDefault(UNKNOWN_BOOT_COUNT)
}
