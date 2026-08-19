package com.gamelaunch.frontend.domain.usecase

import com.gamelaunch.frontend.domain.model.EmulatorUpdate
import com.gamelaunch.frontend.domain.model.PackApp
import com.gamelaunch.frontend.domain.repository.ObtainiumPackRepository
import com.gamelaunch.frontend.launcher.PackageManagerHelper
import com.gamelaunch.frontend.util.VersionCompare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject

/**
 * Native emulator-update check: for each installed emulator the Obtainium pack tracks from GitHub
 * releases, read the latest release and report it when it's newer than the installed build. Mirrors
 * [CheckForUpdateUseCase], but for the user's emulators instead of eOr itself.
 *
 * Deliberately limited to GitHub-source, id-matched emulators with a readable installed version —
 * HTML-scraped, unmapped, or ambiguous ones are left to Obtainium (which owns full coverage and
 * background notifications), so eOr never shows a false "update available".
 */
class CheckEmulatorUpdatesUseCase @Inject constructor(
    private val packageManagerHelper: PackageManagerHelper,
    private val packRepository: ObtainiumPackRepository
) {
    private val client = OkHttpClient()

    suspend operator fun invoke(): List<EmulatorUpdate> = withContext(Dispatchers.IO) {
        val installed = packageManagerHelper.getInstalledEmulators()
            .filter { it.isInstalled && !it.versionName.isNullOrBlank() }

        installed.mapNotNull { emu ->
            val entry = packRepository.entryForPackage(emu.packageName) ?: return@mapNotNull null
            if (!entry.isGitHubSource) return@mapNotNull null
            val latest = latestGitHubVersion(entry) ?: return@mapNotNull null
            val current = emu.versionName ?: return@mapNotNull null
            if (!VersionCompare.isNewer(latest, current)) return@mapNotNull null
            EmulatorUpdate(
                packageName = emu.packageName,
                displayName = emu.displayName,
                installedVersion = current,
                latestVersion = latest,
                sourceUrl = entry.url
            )
        }
    }

    /** The latest stable release version for [entry], honoring the pack's version-extraction regex. */
    private fun latestGitHubVersion(entry: PackApp): String? = runCatching {
        val repoPath = gitHubRepoPath(entry.url) ?: return null
        val settings = runCatching { JSONObject(entry.additionalSettings) }.getOrNull()
        val includePrereleases = settings?.optBoolean("includePrereleases", false) ?: false
        val versionRegEx = settings?.optString("versionExtractionRegEx").orEmpty()

        val request = Request.Builder()
            .url("https://api.github.com/repos/$repoPath/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .build()

        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val json = JSONObject(resp.body?.string() ?: return null)
            if (json.optBoolean("draft")) return null
            if (json.optBoolean("prerelease") && !includePrereleases) return null

            val tag = json.optString("tag_name").ifBlank { json.optString("name") }
            if (tag.isBlank()) return null
            extractVersion(tag, versionRegEx) ?: tag.trimStart('v', 'V')
        }
    }.getOrNull()

    /** Apply the pack's versionExtractionRegEx (if any); returns the first capture group or match. */
    private fun extractVersion(raw: String, regex: String): String? {
        if (regex.isBlank()) return null
        return runCatching {
            val match = Regex(regex).find(raw) ?: return null
            (match.groupValues.getOrNull(1)?.takeIf { it.isNotBlank() } ?: match.value)
                .trimStart('v', 'V')
        }.getOrNull()
    }

    /** "owner/repo" from a GitHub URL, or null if the URL isn't a github.com repo. */
    private fun gitHubRepoPath(url: String): String? {
        val match = Regex("""github\.com/([^/]+)/([^/#?]+)""").find(url) ?: return null
        val (owner, repo) = match.destructured
        return "$owner/${repo.removeSuffix(".git")}"
    }
}
