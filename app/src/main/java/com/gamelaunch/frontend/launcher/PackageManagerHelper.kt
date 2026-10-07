package com.gamelaunch.frontend.launcher

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import com.gamelaunch.frontend.domain.model.InstalledApp
import com.gamelaunch.frontend.domain.model.InstalledEmulator
import com.gamelaunch.frontend.domain.platform.BuiltInEmulators
import com.gamelaunch.frontend.domain.platform.PlatformDefinitions
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PackageManagerHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val retroArchPackages: Set<String> get() = BuiltInEmulators.RETROARCH_PACKAGES

    /** Packages eOr treats as emulators/launchers — excluded from the Android games scan. */
    val emulatorPackages: Set<String> get() = PlatformDefinitions.catalog.emulatorPackages

    // Ordered preference list per platform — first installed entry wins during auto-detect.
    val platformEmulatorPriority: Map<String, List<String>>
        get() = PlatformDefinitions.catalog.emulatorPriority


    fun getInstalledEmulators(): List<InstalledEmulator> {
        val pm = context.packageManager
        return PlatformDefinitions.catalog.emulators.map { emulator ->
            val info = runCatching { pm.getPackageInfo(emulator.packageName, 0) }.getOrNull()
            InstalledEmulator(
                packageName = emulator.packageName,
                displayName = emulator.displayName,
                isInstalled = info != null,
                versionName = info?.versionName
            )
        }
    }

    /** Installed version name for [packageName], or null when it isn't installed. */
    fun getPackageVersionName(packageName: String): String? = runCatching {
        context.packageManager.getPackageInfo(packageName, 0).versionName
    }.getOrNull()

    fun isPackageInstalled(packageName: String): Boolean = runCatching {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    }.getOrDefault(false)

    /** All user-launchable apps (anything with a LAUNCHER activity), minus this launcher itself. */
    fun getInstalledApps(): List<InstalledApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .asSequence()
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { it != context.packageName }
            .map { pkg ->
                val label = runCatching {
                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                }.getOrDefault(pkg)
                InstalledApp(packageName = pkg, label = label)
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    /** Loads an app's launcher icon. Returns null if the package can't be resolved. */
    fun getAppIcon(packageName: String): Drawable? = runCatching {
        context.packageManager.getApplicationIcon(packageName)
    }.getOrNull()

    /** Launches an installed app by package name. */
    fun launchApp(packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent); true }.getOrDefault(false)
    }
}
