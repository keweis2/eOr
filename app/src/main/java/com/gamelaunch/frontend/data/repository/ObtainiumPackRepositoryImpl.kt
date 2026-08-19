package com.gamelaunch.frontend.data.repository

import android.content.Context
import com.gamelaunch.frontend.domain.model.PackApp
import com.gamelaunch.frontend.domain.repository.ObtainiumPackRepository
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ObtainiumPackRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : ObtainiumPackRepository {

    private val client = OkHttpClient()
    private val loadMutex = Mutex()

    /** Raw Obtainium app objects, keyed by pack id. Preserved verbatim for faithful re-export. */
    @Volatile private var rawById: Map<String, JsonObject>? = null
    @Volatile private var cached: List<PackApp>? = null

    override suspend fun getPack(): List<PackApp> {
        cached?.let { return it }
        return loadMutex.withLock {
            cached?.let { return it }
            val raw = withContext(Dispatchers.IO) { fetchRemote() ?: loadBundled() }
            val byId = raw.associateBy { it.get("id").asString }
            val apps = byId.values.map { it.toPackApp() }
            rawById = byId
            cached = apps
            apps
        }
    }

    override suspend fun entryForPackage(packageName: String): PackApp? {
        val apps = getPack()
        val targetId = PACKAGE_OVERRIDES[packageName] ?: packageName
        return apps.firstOrNull { it.id == targetId }
    }

    override suspend fun buildImportJson(entries: List<PackApp>): String {
        getPack() // ensure rawById is populated
        val raw = rawById.orEmpty()
        val array = JsonArray()
        entries.forEach { entry -> raw[entry.id]?.let { array.add(it) } }
        return array.toString()
    }

    override suspend fun missingEssentials(installedPackages: Set<String>): List<PackApp> {
        // An essential is "present" if its own package, or an eOr variant that overrides to the same
        // pack id, is installed.
        val installedPackIds = installedPackages.map { PACKAGE_OVERRIDES[it] ?: it }.toSet()
        return ESSENTIAL_PACKAGES
            .mapNotNull { entryForPackage(it) }
            .distinctBy { it.id }
            .filterNot { it.id in installedPackIds }
    }

    /** Fetch the latest pack from GitHub; null on any failure so the caller falls back to the asset. */
    private fun fetchRemote(): List<JsonObject>? = runCatching {
        val request = Request.Builder().url(REMOTE_URL).build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val body = resp.body?.string() ?: return null
            parseApps(body)
        }
    }.getOrNull()

    private fun loadBundled(): List<JsonObject> = runCatching {
        context.assets.open(BUNDLED_ASSET).bufferedReader().use { parseApps(it.readText()) }
    }.getOrDefault(emptyList())

    private fun parseApps(json: String): List<JsonObject> {
        val root = JsonParser.parseString(json).asJsonObject
        val apps = root.getAsJsonArray("apps") ?: return emptyList()
        return apps.mapNotNull { el ->
            (el as? JsonObject)?.takeIf { it.has("id") && it.get("id").isJsonPrimitive }
        }
    }

    private fun JsonObject.optString(key: String, default: String = ""): String =
        get(key)?.takeIf { it.isJsonPrimitive }?.asString ?: default

    private fun JsonObject.toPackApp(): PackApp = PackApp(
        id = optString("id"),
        url = optString("url"),
        name = optString("name"),
        author = optString("author"),
        overrideSource = optString("overrideSource"),
        preferredApkIndex = get("preferredApkIndex")?.takeIf { it.isJsonPrimitive }?.asInt ?: 0,
        additionalSettings = optString("additionalSettings"),
        categories = getAsJsonArray("categories")?.mapNotNull { it.asString } ?: emptyList()
    )

    companion object {
        private const val REMOTE_URL =
            "https://raw.githubusercontent.com/RJNY/Obtainium-Emulation-Pack/main/obtainium-emulation-pack-latest.json"
        private const val BUNDLED_ASSET = "obtainium-emulation-pack.json"

        /**
         * eOr package ids that differ from the pack's tracking id (or where a variant should track
         * the mainline app for update awareness). Keep small and explicit.
         */
        private val PACKAGE_OVERRIDES: Map<String, String> = mapOf(
            "org.pegasus_frontend.pegasus" to "org.pegasus_frontend.android",
            "org.pegasus_frontend.Pegasus" to "org.pegasus_frontend.android",
            "org.ppsspp.ppssppgold" to "org.ppsspp.ppsspp"
        )

        /**
         * Recommended standalone emulators to offer during onboarding, one per major platform. Only
         * emulators the pack actually tracks are listed (RetroArch, the universal retro fallback, is
         * Play-Store distributed and not in the pack). Curated deliberately rather than derived.
         */
        private val ESSENTIAL_PACKAGES: List<String> = listOf(
            "com.github.stenzek.duckstation", // PS1
            "xyz.aethersx2.android",          // PS2
            "org.ppsspp.ppsspp",              // PSP
            "me.magnum.melonds",              // NDS
            "org.azahar_emu.azahar",          // 3DS
            "org.dolphinemu.dolphinemu",      // GameCube / Wii
            "info.cemu.cemu",                 // Wii U
            "dev.eden.eden_emulator",         // Switch
            "com.flycast.emulator",           // Dreamcast
            "org.vita3k.emulator"             // PS Vita
        )
    }
}
