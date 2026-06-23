package com.gamelaunch.frontend.domain.model

data class InstalledEmulator(
    val packageName: String,
    val displayName: String,
    val isInstalled: Boolean = true
)
