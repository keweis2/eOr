package com.gamelaunch.frontend.domain.sync

import java.io.File

/**
 * Where each known Android emulator keeps its saves/states, and whether eOr can actually reach that
 * folder to sync it. This is the foundation of Save Sync: Syncthing can only sync folders eOr can
 * read, and Android 11+ blocks `Android/data/<pkg>/files` even with All-Files-Access (the SAF
 * loophole is closed on Android 13). So emulators fall into two storage classes.
 *
 * Paths/classifications are best-known defaults; several emulators let the user relocate their save
 * directory, so the resolver treats a missing folder as "needs setup" rather than an error.
 */
enum class SaveStorageClass {
    /** Under `/storage/emulated/0/…` — readable with All-Files-Access. Syncable. */
    SHARED,
    /** Under `Android/data/<pkg>/files/…` — blocked on Android 11+. Needs guidance. */
    APP_PRIVATE
}

enum class SyncReadiness {
    /** Save folder found in shared storage — can be added to Syncthing now. */
    READY,
    /** Installed, shared-storage emulator but no save folder found yet (no saves, or a custom dir). */
    NEEDS_SETUP,
    /** Saves live in Android/data and this OS version blocks access — needs user action. */
    BLOCKED
}

/** One emulator's save footprint. [saveDirs] are absolute shared-storage paths (empty for APP_PRIVATE). */
data class EmulatorSaveSpec(
    val packages: List<String>,
    val displayName: String,
    val storageClass: SaveStorageClass,
    val saveDirs: List<String> = emptyList(),
    /** Short guidance shown when the folder can't be auto-synced. */
    val note: String? = null
)

/** Resolved per-emulator status for the current device, ready to render + feed to Syncthing. */
data class EmulatorSyncStatus(
    val spec: EmulatorSaveSpec,
    val installedPackage: String,
    val readiness: SyncReadiness,
    /** Shared-storage dirs that actually exist (what we'd hand to Syncthing). */
    val syncableDirs: List<String>,
    val message: String
)

object SaveLocationRegistry {

    const val SHARED_ROOT = "/storage/emulated/0"

    /** Curated from eOr's existing emulator catalog (EmulatorLauncher / PackageManagerHelper). */
    val specs: List<EmulatorSaveSpec> = listOf(
        // ── Shared storage (syncable) ───────────────────────────────────────
        EmulatorSaveSpec(
            packages = listOf("com.retroarch.aarch64", "org.libretro.retroarch"),
            displayName = "RetroArch",
            storageClass = SaveStorageClass.SHARED,
            saveDirs = listOf("$SHARED_ROOT/RetroArch/saves", "$SHARED_ROOT/RetroArch/states"),
            note = "Uses RetroArch's Save/State directories (Settings → Directory)."
        ),
        EmulatorSaveSpec(
            packages = listOf("org.ppsspp.ppsspp", "org.ppsspp.ppssppgold"),
            displayName = "PPSSPP",
            storageClass = SaveStorageClass.SHARED,
            saveDirs = listOf("$SHARED_ROOT/PSP/SAVEDATA", "$SHARED_ROOT/PSP/PPSSPP_STATE")
        ),
        EmulatorSaveSpec(
            packages = listOf("com.dsemu.drastic"),
            displayName = "DraStic",
            storageClass = SaveStorageClass.SHARED,
            saveDirs = listOf("$SHARED_ROOT/DraStic/backup", "$SHARED_ROOT/DraStic/savestates")
        ),
        EmulatorSaveSpec(
            packages = listOf("com.github.stenzek.duckstation"),
            displayName = "DuckStation",
            storageClass = SaveStorageClass.SHARED,
            // Location is user-chosen; only reachable if "Game Data → External" is set.
            saveDirs = emptyList(),
            note = "Set Settings → Data → Game Data Storage to External, then add that folder."
        ),

        // ── App-private (blocked on Android 11+) ────────────────────────────
        EmulatorSaveSpec(
            packages = listOf("org.dolphinemu.dolphinemu"),
            displayName = "Dolphin",
            storageClass = SaveStorageClass.APP_PRIVATE,
            note = "Saves in Android/data. Use Dolphin's export, or a shared user folder if configured."
        ),
        EmulatorSaveSpec(
            packages = listOf("xyz.aethersx2.android", "xyz.trizle.nethersx2", "net.play.ptmk.ps2"),
            displayName = "AetherSX2 / NetherSX2",
            storageClass = SaveStorageClass.APP_PRIVATE,
            note = "Memcards in Android/data — use the emulator's Transfer Data export."
        ),
        EmulatorSaveSpec(
            packages = listOf("org.citra.emu"),
            displayName = "Citra",
            storageClass = SaveStorageClass.APP_PRIVATE,
            note = "Saves in Android/data — not directly syncable on this Android version."
        ),
        EmulatorSaveSpec(
            packages = listOf("me.magnum.melonds"),
            displayName = "melonDS",
            storageClass = SaveStorageClass.APP_PRIVATE,
            note = "Saves in Android/data — not directly syncable on this Android version."
        ),
        EmulatorSaveSpec(
            packages = listOf("org.mupen64plusae.v3.fzurita.pro", "org.mupen64plusae.v3.fzurita"),
            displayName = "Mupen64Plus FZ",
            storageClass = SaveStorageClass.APP_PRIVATE,
            note = "Default game-data is Android/data; point it at a shared folder to sync."
        ),
        EmulatorSaveSpec(
            packages = listOf("io.recompiled.redream"),
            displayName = "Redream",
            storageClass = SaveStorageClass.APP_PRIVATE,
            note = "Saves in Android/data — not directly syncable on this Android version."
        ),
        EmulatorSaveSpec(
            packages = listOf("org.devmiyax.yabasanshioro2.pro", "org.devmiyax.yabasanshioro2"),
            displayName = "Yaba Sanshiro 2",
            storageClass = SaveStorageClass.APP_PRIVATE,
            note = "Saves in Android/data — not directly syncable on this Android version."
        ),
        EmulatorSaveSpec(
            packages = listOf("dev.eden.eden_emulator", "dev.eden.emulator", "org.yuzu.yuzu_emu", "org.sudachi.sudachi_emu"),
            displayName = "Switch (Yuzu-derived)",
            storageClass = SaveStorageClass.APP_PRIVATE,
            note = "Saves in Android/data — not directly syncable on this Android version."
        ),
    )

    fun specForPackage(pkg: String): EmulatorSaveSpec? = specs.firstOrNull { pkg in it.packages }

    /**
     * Resolve every installed known emulator to a sync status for this device.
     *
     * @param installedPackages package names currently installed (from PackageManagerHelper).
     * @param appDataBlocked true on Android 11+ where Android/data is unreachable.
     * @param dirExists injectable existence check (defaults to the real filesystem) — keeps this pure/testable.
     */
    fun resolve(
        installedPackages: Set<String>,
        appDataBlocked: Boolean,
        dirExists: (String) -> Boolean = { File(it).isDirectory }
    ): List<EmulatorSyncStatus> = specs.mapNotNull { spec ->
        val pkg = spec.packages.firstOrNull { it in installedPackages } ?: return@mapNotNull null
        when (spec.storageClass) {
            SaveStorageClass.SHARED -> {
                val present = spec.saveDirs.filter(dirExists)
                if (present.isNotEmpty()) {
                    EmulatorSyncStatus(spec, pkg, SyncReadiness.READY, present, "Ready to sync")
                } else {
                    EmulatorSyncStatus(
                        spec, pkg, SyncReadiness.NEEDS_SETUP, emptyList(),
                        spec.note ?: "No save folder found yet."
                    )
                }
            }
            SaveStorageClass.APP_PRIVATE -> {
                val readiness = if (appDataBlocked) SyncReadiness.BLOCKED else SyncReadiness.READY
                EmulatorSyncStatus(
                    spec, pkg, readiness, emptyList(),
                    spec.note ?: "Saves stored in Android/data."
                )
            }
        }
    }
}
