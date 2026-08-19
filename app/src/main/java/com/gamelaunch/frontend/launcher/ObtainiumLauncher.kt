package com.gamelaunch.frontend.launcher

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.gamelaunch.frontend.domain.model.PackApp
import com.gamelaunch.frontend.domain.repository.ObtainiumPackRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hands off to Obtainium (https://github.com/ImranR98/Obtainium) via its deep-link contract to
 * track/install emulators. Obtainium owns the actual downloads, version detection for HTML sources,
 * and background update notifications; eOr just curates which apps to hand over.
 *
 * Deep links parsed by Obtainium (host = action, data = path/`url` query):
 *  - `obtainium://apps/<urlEncoded JSON array>` — bulk import (shows a confirm dialog first)
 *  - `obtainium://add/<sourceUrl>` — open Add-App prefilled with a single source
 *  - `obtainium://refresh` — force an update check
 */
@Singleton
class ObtainiumLauncher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val packRepository: ObtainiumPackRepository
) {
    /** Whether any Obtainium flavor is installed. */
    fun isInstalled(): Boolean = OBTAINIUM_PACKAGES.any { pkg ->
        runCatching { context.packageManager.getPackageInfo(pkg, 0); true }.getOrDefault(false)
    }

    /**
     * Import [entries] into Obtainium (used both to track installed emulators and to install missing
     * essentials). Returns false when there's nothing to import or the deep link can't be handled —
     * in which case the caller should offer [openInstallPage].
     */
    suspend fun importApps(entries: List<PackApp>): Boolean {
        if (entries.isEmpty()) return false
        val json = packRepository.buildImportJson(entries)
        if (json.isBlank() || json == "[]") return false
        return startDeepLink("obtainium://apps/${Uri.encode(json)}")
    }

    /** Open Obtainium's Add-App screen prefilled with a single emulator's source URL. */
    fun addSingle(sourceUrl: String): Boolean =
        startDeepLink("obtainium://add/${Uri.encode(sourceUrl)}")

    /** Ask Obtainium to re-check all tracked apps for updates. */
    fun refresh(): Boolean = startDeepLink("obtainium://refresh")

    /** Launch Obtainium's main UI (falls back to the install page when it isn't installed). */
    fun open(): Boolean {
        if (!isInstalled()) return openInstallPage()
        val launch = OBTAINIUM_PACKAGES.firstNotNullOfOrNull {
            context.packageManager.getLaunchIntentForPackage(it)
        } ?: return openInstallPage()
        return runCatching {
            context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }.getOrDefault(false)
    }

    /** Send the user to Obtainium's repo homepage, which explains it and links its install options. */
    fun openInstallPage(): Boolean = runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(INSTALL_PAGE))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    }.getOrDefault(false)

    private fun startDeepLink(uri: String): Boolean = runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(context.packageManager) == null) return false
        context.startActivity(intent)
        true
    }.getOrDefault(false)

    companion object {
        private val OBTAINIUM_PACKAGES = listOf("dev.imranr.obtainium", "dev.imranr.obtainium.fdroid")
        private const val INSTALL_PAGE = "https://github.com/ImranR98/Obtainium"
    }
}
