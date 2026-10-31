package com.gamelaunch.frontend.launcher

import android.content.Context
import android.os.Bundle
import com.gamelaunch.frontend.ui.dualscreen.ExternalAppReturnActivity
import com.gamelaunch.frontend.ui.dualscreen.ExternalAppSessionState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** Owns the top-screen return host and artwork lifecycle for every external launch. */
@Singleton
class ExternalLaunchCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val displayPolicy: LaunchDisplayPolicy,
    private val externalAppSessionState: ExternalAppSessionState,
) {
    private val launchMutex = Mutex()

    suspend fun launch(
        platformId: String? = null,
        start: suspend (Bundle?) -> Result<Unit>,
    ): Result<Unit> = launchMutex.withLock {
        var returnActivity: ExternalAppReturnActivity? = null
        var launched = false
        try {
            val options = displayPolicy.options(platformId)
            if (options != null) returnActivity = ExternalAppReturnActivity.prepare(context, options)
            val result = start(options)
            launched = result.isSuccess
            if (launched && options != null) externalAppSessionState.begin()
            result
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        } finally {
            if (!launched) returnActivity?.finishAndRemoveTask()
        }
    }
}
