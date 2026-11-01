package com.gamelaunch.frontend.launcher

import android.app.ActivityOptions
import android.content.Context
import android.os.Build
import android.os.Bundle
import com.gamelaunch.frontend.domain.repository.SettingsRepository
import com.gamelaunch.frontend.platform.display.DualScreenManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** Shared display selection for apps and games; null preserves Android's normal routing. */
class LaunchDisplayPolicy @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
) {
    suspend fun options(platformId: String? = null, screen: LaunchScreen? = null): Bundle? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        if (!settingsRepository.dualScreenEnabled.first()) return null
        if (screen == null && !settingsRepository.gameLaunchOnTop.first()) return null
        val swap = settingsRepository.dualScreenSwap.first()
        val displayId = when (screen) {
            LaunchScreen.BOTTOM -> DualScreenManager.menuDisplayId(context, swap)
            LaunchScreen.TOP, null -> DualScreenManager.artworkDisplayId(context, swap)
        } ?: return null
        return ActivityOptions.makeBasic().setLaunchDisplayId(displayId).toBundle()
    }
}
