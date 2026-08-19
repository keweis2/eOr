package com.gamelaunch.frontend.domain.model

data class InstalledEmulator(
    val packageName: String,
    val displayName: String,
    val isInstalled: Boolean = true,
    /** Installed version name (from PackageInfo), null when not installed or unavailable. */
    val versionName: String? = null
)
