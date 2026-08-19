package com.gamelaunch.frontend.domain.model

/**
 * One app entry from the Obtainium Emulation Pack
 * (https://github.com/RJNY/Obtainium-Emulation-Pack).
 *
 * [id] is the Android package name, [url] the source (GitHub repo or HTML page). [additionalSettings]
 * is kept as the raw stringified-JSON blob Obtainium expects verbatim on import — eOr passes it
 * through untouched for the `obtainium://apps/` deep link, and only parses selected fields (e.g.
 * versionExtractionRegEx) for its own native update checks.
 */
data class PackApp(
    val id: String,
    val url: String,
    val name: String,
    val author: String,
    val overrideSource: String,
    val preferredApkIndex: Int,
    val additionalSettings: String,
    val categories: List<String>
) {
    /** True when Obtainium tracks this app from GitHub releases (as opposed to HTML scraping). */
    val isGitHubSource: Boolean get() = overrideSource.equals("GitHub", ignoreCase = true)
}
