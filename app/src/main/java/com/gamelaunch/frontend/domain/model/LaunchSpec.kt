package com.gamelaunch.frontend.domain.model

/** An emulator app eOr recognises, shown in Configure Emulators and excluded from the Android games scan. */
data class KnownEmulator(
    val packageName: String,
    val displayName: String
)

/** How to hand a ROM to a specific standalone emulator. */
data class LaunchSpec(
    val activity: String,                  // fully-qualified activity to launch explicitly
    val romExtraKey: String? = null,       // pass ROM path via this String extra; else as data URI
    val action: String = ACTION_VIEW,
    val mimeType: String? = null,
    val romUriMode: RomUriMode = RomUriMode.FILE
) {
    companion object {
        // Mirrors android.content.Intent constants so the catalog stays plain Kotlin (JVM-testable).
        const val ACTION_VIEW = "android.intent.action.VIEW"
        const val ACTION_MAIN = "android.intent.action.MAIN"
    }
}

/** How the ROM is supplied: its raw file URI, or a FileProvider URI with temporary read access. */
enum class RomUriMode { FILE, CONTENT }
