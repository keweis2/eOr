package com.gamelaunch.frontend.domain.usecase

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject

/** A newer published GitHub release than the installed build. */
data class AppUpdate(val versionName: String, val releaseUrl: String)

/**
 * Checks the project's GitHub "latest release" and reports it when it's newer than the installed
 * app version. No backend required — it's a plain read of the public Releases API on app launch.
 */
class CheckForUpdateUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val client = OkHttpClient()

    suspend operator fun invoke(): AppUpdate? = withContext(Dispatchers.IO) {
        runCatching {
            val current = context.packageManager
                .getPackageInfo(context.packageName, 0).versionName ?: return@runCatching null

            val request = Request.Builder()
                .url(LATEST_RELEASE_URL)
                .header("Accept", "application/vnd.github+json")
                .build()

            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                val body = resp.body?.string() ?: return@runCatching null
                val json = JSONObject(body)
                // Ignore drafts / pre-releases — only ship stable versions to users.
                if (json.optBoolean("draft") || json.optBoolean("prerelease")) return@runCatching null

                val tag = json.optString("tag_name").ifBlank { return@runCatching null }
                val latest = tag.trimStart('v', 'V')
                if (!isNewer(latest, current)) return@runCatching null

                val url = json.optString("html_url").ifBlank { RELEASES_PAGE }
                AppUpdate(latest, url)
            }
        }.getOrNull()
    }

    /** Numeric, dot-separated version compare (e.g. "1.5.0" > "1.4.0"); non-numeric parts ignored. */
    private fun isNewer(latest: String, current: String): Boolean {
        val l = latest.split('.', '-').mapNotNull { it.toIntOrNull() }
        val c = current.split('.', '-').mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(l.size, c.size)) {
            val a = l.getOrElse(i) { 0 }
            val b = c.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    companion object {
        private const val REPO = "keweis2/eOr"
        private const val LATEST_RELEASE_URL = "https://api.github.com/repos/$REPO/releases/latest"
        private const val RELEASES_PAGE = "https://github.com/$REPO/releases/latest"
    }
}
