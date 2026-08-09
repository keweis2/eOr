package com.gamelaunch.frontend.domain.usecase

import com.gamelaunch.frontend.domain.lockedmode.LockedModeAppRepository
import com.gamelaunch.frontend.domain.lockedmode.LockedModeRepository
import com.gamelaunch.frontend.launcher.PackageManagerHelper
import javax.inject.Inject

class LaunchAppUseCase @Inject constructor(
    private val lockedModeRepository: LockedModeRepository,
    private val lockedModeAppRepository: LockedModeAppRepository,
    private val packageManagerHelper: PackageManagerHelper
) {
    suspend operator fun invoke(packageName: String): Result<Unit> = runCatching {
        if (lockedModeRepository.isLocked() && !lockedModeAppRepository.isAllowed(packageName)) {
            error("App is not available in Locked Mode")
        }
        check(packageManagerHelper.launchApp(packageName)) { "App could not be launched" }
    }
}
