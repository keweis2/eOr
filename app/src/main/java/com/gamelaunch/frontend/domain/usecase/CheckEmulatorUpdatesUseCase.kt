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
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Native emulator-update check: for each installed emulator the Obtainium pack tracks from GitHub
 * releases, read the latest release and report it when it's newer than the installed build. Mirrors
 * [CheckForUpdateUseCase], but for the user's emulators instead of eOr itself.
 *
 * Deliberately limited to GitHub-source, id-matched emulators with a readable installed version —
 * HTML-scraped, unmapped, or ambiguous ones are left to Obtainium (which owns full coverage and
 * background notifications), so eOr never shows a false "update available".
 */
@Singleton
class CheckEmulatorUpdatesUseCase @Inject constructor(
    private val packageManagerHelper: PackageManagerHelper,
    private val packRepository: ObtainiumPackRepository
) {
    private val client = OkHttpClient()

    // GitHub's anonymous API limit is 60 req/hr *per IP* — shared with Obtainium, which polls the
    // same repos. To avoid draining that budget (and tripping Obtainium's "rate limited" errors) we
    // (a) cache each repo's ETag and send If-None-Match so unchanged releases return 304, which does
    // NOT count against the limit, and (b) throttle full network sweeps. Singleton so the cache and
    // throttle are shared across every caller (launch banner + Settings card).
    private data class RepoCache(val etag: String?, val version: String?)
    private val repoCache = ConcurrentHashMap<String, RepoCache>()

    @Volatile private var lastSweepAt = 0L
    @Volatile private var lastResult: List<EmulatorUpdate> = emptyList()

    /** The most recent result, with no network call — safe to read on UI navigation. */
    fun cachedUpdates(): List<EmulatorUpdate> = lastResult

    /**
     * @param force bypass the sweep throttle (for an explicit user "Check for updates" tap). Even a
     * forced sweep is mostly free: unchanged repos answer 304 via their cached ETag.
     */
    suspend operator fun invoke(force: Boolean = false): List<EmulatorUpdate> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!force && now - lastSweepAt < SWEEP_THROTTLE_MS) return@withContext lastResult

        val installed = packageManagerHelper.getInstalledEmulators()
            .filter { it.isInstalled && !it.versionName.isNullOrBlank() }

        val result = installed.mapNotNull { emu ->
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
        lastSweepAt = now
        lastResult = result
        result
    }

    /** The latest stable release version for [entry], honoring the pack's version-extraction regex. */
    private fun latestGitHubVersion(entry: PackApp): String? = runCatching {
        val repoPath = gitHubRepoPath(entry.url) ?: return null
        val settings = runCatching { JSONObject(entry.additionalSettings) }.getOrNull()
        val includePrereleases = settings?.optBoolean("includePrereleases", false) ?: false
        val versionRegEx = settings?.optString("versionExtractionRegEx").orEmpty()

        val cached = repoCache[repoPath]
        val request = Request.Builder()
            .url("https://api.github.com/repos/$repoPath/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .apply { cached?.etag?.let { header("If-None-Match", it) } }
            .build()

        client.newCall(request).execute().use { resp ->
            // 304 = release unchanged since last check; free (not counted against the rate limit).
            if (resp.code == 304) return cached?.version
            // On any failure — including 403 when we're rate-limited — fall back to the last known
            // version rather than dropping the emulator from the list.
            if (!resp.isSuccessful) return cached?.version
            val etag = resp.header("ETag")
            val json = JSONObject(resp.body?.string() ?: return cached?.version)
            if (json.optBoolean("draft")) return null
            if (json.optBoolean("prerelease") && !includePrereleases) return null

            val tag = json.optString("tag_name").ifBlank { json.optString("name") }
            if (tag.isBlank()) return null
            val version = extractVersion(tag, versionRegEx) ?: tag.trimStart('v', 'V')
            repoCache[repoPath] = RepoCache(etag, version)
            version
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

    companion object {
        // Skip a fresh network sweep if one ran this recently (unless forced by an explicit tap).
        private const val SWEEP_THROTTLE_MS = 10 * 60 * 1000L
    }
}
