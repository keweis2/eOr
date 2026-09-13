package com.gamelaunch.frontend.domain.usecase

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
 * Checks the project's GitHub "latest release" and reports it when it's newer than the installed
 * app version. No backend required — it's a plain read of the public Releases API on app launch.
 */
class CheckForUpdateUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend operator fun invoke(): UpdateCheck = withContext(Dispatchers.IO) {
        val current = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: return@withContext UpdateCheck.Failed

        val request = Request.Builder()
            .url(LATEST_RELEASE_URL)
            .header("Accept", "application/vnd.github+json")
            // GitHub requires a User-Agent; set an explicit one rather than relying on OkHttp's default.
            .header("User-Agent", "eOr-update-check")
            .build()

        runCatching {
            client.newCall(request).execute().use { resp ->
                // A non-2xx (e.g. 403 rate-limit, 5xx) is a transient failure, not "up to date".
                if (!resp.isSuccessful) return@use UpdateCheck.Failed
                val body = resp.body?.string() ?: return@use UpdateCheck.Failed
                val json = JSONObject(body)
                // Ignore drafts / pre-releases — only ship stable versions to users.
                if (json.optBoolean("draft") || json.optBoolean("prerelease")) return@use UpdateCheck.UpToDate

                val tag = json.optString("tag_name").ifBlank { return@use UpdateCheck.UpToDate }
                val latest = tag.trimStart('v', 'V')
                if (!VersionCompare.isNewer(latest, current)) return@use UpdateCheck.UpToDate

                val url = json.optString("html_url").ifBlank { RELEASES_PAGE }
                UpdateCheck.Available(AppUpdate(latest, url))
            }
        }.getOrDefault(UpdateCheck.Failed)
    }

    companion object {
        private const val REPO = "keweis2/eOr"
        private const val LATEST_RELEASE_URL = "https://api.github.com/repos/$REPO/releases/latest"
        private const val RELEASES_PAGE = "https://github.com/$REPO/releases/latest"
    }
}
