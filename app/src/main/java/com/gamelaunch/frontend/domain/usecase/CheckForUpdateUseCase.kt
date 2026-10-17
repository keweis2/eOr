package com.gamelaunch.frontend.domain.usecase

import com.gamelaunch.frontend.util.LegacyTls
import android.content.Context
import com.gamelaunch.frontend.util.VersionCompare
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** A newer published GitHub release than the installed build. */
data class AppUpdate(val versionName: String, val releaseUrl: String)

/**
 * Outcome of an update check. Distinguishing [Failed] from [UpToDate] lets the caller retry a failed
 * check soon (a launch with no network yet, a GitHub rate-limit) instead of burning the full check
 * interval on it — the difference between an update banner that shows reliably and one that doesn't.
 */
sealed interface UpdateCheck {
    /** A newer stable release exists. */
    data class Available(val update: AppUpdate) : UpdateCheck
    /** The check completed and the installed build is current (or the latest is a draft/pre-release). */
    object UpToDate : UpdateCheck
    /** The check could not complete (no network, non-2xx response, malformed body). Retry sooner. */
    object Failed : UpdateCheck
}

/**
 * Checks the project's GitHub latest release and reports it when it's newer than the installed app
 * version. No backend required.
 *
 * Primary source is the plain web redirect `github.com/<repo>/releases/latest` → `.../tag/vX.Y.Z`:
 * it is NOT subject to the REST API's anonymous limit of 60 requests/hour **per IP**. That limit is
 * shared by every device on the user's network and by the emulator-update sweep (one call per
 * emulator) and Obtainium, so on a household with a few handhelds the API was routinely answering
 * 403 — the update banner then silently never appeared. The API is kept only as a fallback.
 * GitHub's "latest" already excludes drafts and pre-releases, so both sources agree.
 */
class CheckForUpdateUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val client = LegacyTls.builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    // For the redirect probe we want the Location header itself, not the page it points to.
    private val noRedirectClient = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    suspend operator fun invoke(): UpdateCheck = withContext(Dispatchers.IO) {
        val current = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: return@withContext UpdateCheck.Failed

        latestTagViaRedirect()?.let { tag -> return@withContext compare(tag, current, "$RELEASES_TAG_BASE$tag") }
        latestViaApi(current)
    }

    private fun compare(tag: String, current: String, url: String): UpdateCheck {
        val latest = tag.trimStart('v', 'V')
        return if (VersionCompare.isNewer(latest, current)) UpdateCheck.Available(AppUpdate(latest, url))
        else UpdateCheck.UpToDate
    }

    /** The latest release's tag from the web redirect, or null if it couldn't be read. */
    private fun latestTagViaRedirect(): String? = runCatching {
        val request = Request.Builder()
            .url(RELEASES_PAGE)
            .header("User-Agent", "eOr-update-check")
            .build()
        noRedirectClient.newCall(request).execute().use { resp ->
            if (!resp.isRedirect) return@use null
            tagFromReleaseLocation(resp.header("Location"))
        }
    }.getOrNull()

    private fun latestViaApi(current: String): UpdateCheck {
        val request = Request.Builder()
            .url(LATEST_RELEASE_URL)
            .header("Accept", "application/vnd.github+json")
            // GitHub requires a User-Agent; set an explicit one rather than relying on OkHttp's default.
            .header("User-Agent", "eOr-update-check")
            .build()

        return runCatching {
            client.newCall(request).execute().use { resp ->
                // A non-2xx (e.g. 403 rate-limit, 5xx) is a transient failure, not "up to date".
                if (!resp.isSuccessful) return@use UpdateCheck.Failed
                val body = resp.body?.string() ?: return@use UpdateCheck.Failed
                val json = JSONObject(body)
                // Ignore drafts / pre-releases — only ship stable versions to users.
                if (json.optBoolean("draft") || json.optBoolean("prerelease")) return@use UpdateCheck.UpToDate

                val tag = json.optString("tag_name").ifBlank { return@use UpdateCheck.UpToDate }
                compare(tag, current, json.optString("html_url").ifBlank { RELEASES_PAGE })
            }
        }.getOrDefault(UpdateCheck.Failed)
    }

    companion object {
        private const val REPO = "keweis2/eOr"
        private const val LATEST_RELEASE_URL = "https://api.github.com/repos/$REPO/releases/latest"
        private const val RELEASES_PAGE = "https://github.com/$REPO/releases/latest"
        private const val RELEASES_TAG_BASE = "https://github.com/$REPO/releases/tag/"

        /**
         * The tag from a `/releases/latest` redirect target (`…/releases/tag/v3.0.1` → `v3.0.1`).
         * Null when there's no tag in it — e.g. a repo with no releases redirects to `/releases`.
         */
        fun tagFromReleaseLocation(location: String?): String? {
            if (location.isNullOrBlank()) return null
            val tag = Regex("""/releases/tag/([^/?#]+)""").find(location)?.groupValues?.get(1) ?: return null
            return java.net.URLDecoder.decode(tag, "UTF-8").takeIf { it.isNotBlank() }
        }
    }
}
