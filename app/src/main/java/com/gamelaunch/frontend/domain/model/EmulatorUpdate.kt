package com.gamelaunch.frontend.domain.model

/** A newer published release than the installed build of an emulator eOr can natively check. */
data class EmulatorUpdate(
    val packageName: String,
    val displayName: String,
    val installedVersion: String?,
    val latestVersion: String,
    val sourceUrl: String
)
