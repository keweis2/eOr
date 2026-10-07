package com.gamelaunch.frontend.data.catalog

import android.content.Context
import android.util.Log
import com.gamelaunch.frontend.BuildConfig
import com.gamelaunch.frontend.domain.platform.PlatformDefinitions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.Properties
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Keeps the platform/emulator catalog current without an app release.
 *
 * The catalog lives in the eOr repo (catalog/catalog.json). A validated copy is cached in filesDir
 * and installed into [PlatformDefinitions] at startup by [loadCached] — synchronously, because the
 * ROM scanner deletes games whose platform it doesn't recognise, so it must never run against the
 * built-in catalog when a cached one with extra platforms exists. [refresh] then checks GitHub in the
 * background at most once a day (ETag, so an unchanged catalog costs one 304).
 */
@Singleton
class CatalogRepository @Inject constructor(
    @ApplicationContext context: Context,
    @Named("catalog") private val client: OkHttpClient
) {
    private val dir = File(context.filesDir, "catalog")
    private val cacheFile = File(dir, "catalog.json")
    private val metaFile = File(dir, "meta.properties")

    sealed interface RefreshResult {
        data object Skipped : RefreshResult
        data object Unchanged : RefreshResult
        data class Updated(val revision: Int) : RefreshResult
        data class Failed(val reason: String) : RefreshResult
    }

    /** Installs the cached catalog, if any. Call before anything scans ROMs. */
    fun loadCached() {
        val json = runCatching { cacheFile.takeIf { it.exists() }?.readText() }.getOrNull() ?: return
        when (val result = CatalogParser.parse(json, BuildConfig.VERSION_CODE)) {
            is CatalogParser.Result.Ok -> {
                PlatformDefinitions.install(result.catalog)
                Log.i(TAG, "Loaded cached catalog r${result.catalog.revision}")
            }
            // e.g. the app was downgraded below the cached catalog's minAppVersionCode.
            is CatalogParser.Result.Invalid -> {
                Log.w(TAG, "Ignoring cached catalog: ${result.reason}")
                clearCache()
            }
        }
    }

    suspend fun refresh(force: Boolean = false): RefreshResult = withContext(Dispatchers.IO) {
        val meta = readMeta()
        val lastCheck = meta.getProperty(KEY_CHECKED_AT)?.toLongOrNull() ?: 0L
        if (!force && System.currentTimeMillis() - lastCheck < CHECK_INTERVAL_MS) {
            return@withContext RefreshResult.Skipped
        }

        val request = Request.Builder().url(CATALOG_URL).apply {
            // Only send the ETag if we still have the body it describes.
            if (cacheFile.exists()) meta.getProperty(KEY_ETAG)?.let { header("If-None-Match", it) }
        }.build()

        val result = runCatching {
            client.newCall(request).execute().use { response ->
                if (response.code == 304) return@use RefreshResult.Unchanged
                if (!response.isSuccessful) return@use RefreshResult.Failed("HTTP ${response.code}")
                val source = response.body?.source() ?: return@use RefreshResult.Failed("empty body")
                if (source.request(MAX_BYTES + 1)) return@use RefreshResult.Failed("catalog too large")
                val json = source.readUtf8()

                when (val parsed = CatalogParser.parse(json, BuildConfig.VERSION_CODE)) {
                    is CatalogParser.Result.Invalid -> RefreshResult.Failed(parsed.reason)
                    is CatalogParser.Result.Ok -> {
                        if (parsed.rejected.isNotEmpty()) Log.w(TAG, "Catalog entries rejected: ${parsed.rejected}")
                        // A CDN edge can briefly serve an older copy; never step backwards.
                        if (parsed.catalog.revision < PlatformDefinitions.catalog.revision) {
                            return@use RefreshResult.Unchanged
                        }
                        writeAtomically(json)
                        response.header("ETag")?.let { meta.setProperty(KEY_ETAG, it) }
                            ?: meta.remove(KEY_ETAG)
                        PlatformDefinitions.install(parsed.catalog)
                        RefreshResult.Updated(parsed.catalog.revision)
                    }
                }
            }
        }.getOrElse { RefreshResult.Failed(it.message ?: it.javaClass.simpleName) }

        // Network errors don't count as a check, so the next launch retries.
        if (result !is RefreshResult.Failed) {
            meta.setProperty(KEY_CHECKED_AT, System.currentTimeMillis().toString())
            writeMeta(meta)
        }
        Log.i(TAG, "Catalog refresh: $result")
        result
    }

    private fun writeAtomically(json: String) {
        dir.mkdirs()
        val tmp = File(dir, "catalog.json.tmp")
        tmp.writeText(json)
        if (!tmp.renameTo(cacheFile)) {
            tmp.delete()
            error("could not replace cached catalog")
        }
    }

    private fun clearCache() {
        cacheFile.delete()
        metaFile.delete()
        PlatformDefinitions.reset()
    }

    private fun readMeta(): Properties = Properties().apply {
        runCatching { metaFile.takeIf { it.exists() }?.inputStream()?.use { load(it) } }
    }

    private fun writeMeta(meta: Properties) {
        runCatching {
            dir.mkdirs()
            metaFile.outputStream().use { meta.store(it, null) }
        }
    }

    private companion object {
        const val TAG = "Catalog"
        const val CATALOG_URL = "https://raw.githubusercontent.com/keweis2/eOr/main/catalog/catalog.json"
        const val MAX_BYTES = 1L * 1024 * 1024
        val CHECK_INTERVAL_MS = TimeUnit.HOURS.toMillis(24)
        const val KEY_ETAG = "etag"
        const val KEY_CHECKED_AT = "checkedAt"
    }
}
