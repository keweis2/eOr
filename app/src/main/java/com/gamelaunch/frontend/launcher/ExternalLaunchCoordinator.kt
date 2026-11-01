package com.gamelaunch.frontend.launcher

import android.os.Bundle
import com.gamelaunch.frontend.ui.dualscreen.ExternalAppReturnActivity
import com.gamelaunch.frontend.ui.dualscreen.ExternalAppReturnHost
import com.gamelaunch.frontend.ui.dualscreen.ExternalAppSessionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** Owns per-screen return hosts and the artwork lifecycle for external launches. */
@Singleton
class ExternalLaunchCoordinator @Inject constructor(
    private val returnHost: ExternalAppReturnHost,
    private val displayPolicy: LaunchDisplayPolicy,
    private val externalAppSessionState: ExternalAppSessionState,
) {
    private val launchMutex = Mutex()

    suspend fun launch(
        platformId: String? = null,
        dimBottomScreen: Boolean = false,
        screen: LaunchScreen? = null,
        start: suspend (Bundle?) -> Result<Unit>,
    ): Result<Unit> = launchMutex.withLock {
        var returnActivity: ExternalAppReturnActivity? = null
        var launched = false
        try {
            val options = displayPolicy.options(platformId, screen)
            val onTop = options != null && screen != LaunchScreen.BOTTOM
            if (options != null && screen == LaunchScreen.BOTTOM) externalAppSessionState.keepAppListOnTop = true
            if (options != null) {
                returnActivity = returnHost.prepare(
                    options, returnToMenu = screen == LaunchScreen.BOTTOM,
                )
            }
            val result = start(options)
            launched = result.isSuccess
            if (launched && onTop) externalAppSessionState.begin(dimBottomScreen)
            result
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        } finally {
            if (!launched) {
                externalAppSessionState.keepAppListOnTop = false
                returnActivity?.finishAndRemoveTask()
            }
        }
    }
}
