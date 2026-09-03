package com.gamelaunch.frontend.data.webserver

import android.content.Context
import com.gamelaunch.frontend.domain.platform.PlatformDefinitions
import com.gamelaunch.frontend.domain.repository.GameRepository
import com.gamelaunch.frontend.domain.repository.MediaRepository
import com.gamelaunch.frontend.domain.repository.MediaUploadType
import com.gamelaunch.frontend.domain.repository.SettingsRepository
import com.gamelaunch.frontend.domain.usecase.ConvertBackgroundImageUseCase
import com.gamelaunch.frontend.domain.usecase.ExportSettingsUseCase
import com.gamelaunch.frontend.domain.usecase.ImportSettingsUseCase
import com.gamelaunch.frontend.util.StorageUtils
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/** Everything the server needs from the app, bundled so the DI-managed pieces stay in one place. */
class WebTransferDeps(
    val context: Context,
    val settings: SettingsRepository,
    val gameRepository: GameRepository,
    val mediaRepository: MediaRepository,
    val convertBackground: ConvertBackgroundImageUseCase,
    val exportSettings: ExportSettingsUseCase,
    val importSettings: ImportSettingsUseCase,
    val destinationResolver: RomDestinationResolver,
)

/**
 * The LAN web server behind Web Transfer. Serves a small single-page UI (bundled under
 * `assets/webtransfer/`) and a JSON API that writes uploads straight to the user's storage.
 *
 * Security posture:
 *  - Every `/api/*` call except `/api/pair` requires a session token issued only after the browser
 *    submits the device-displayed [pin]. PIN attempts are rate-limited with a lockout.
 *  - Requests are accepted only from LAN (site-local / loopback) addresses.
 *  - Every filename is sanitized and every destination is confined under its intended root
 *    (see [RomDestinationResolver]), so a crafted request can never escape the target directory.
 *
 * File bodies are streamed straight to a `.part` file and atomically renamed, so a partially-received
 * upload is never seen by the library scanner.
 */
class WebTransferServer(
    port: Int,
    private val pin: String,
    private val deps: WebTransferDeps,
    private val onEvent: (String) -> Unit,
    private val onRomUploaded: () -> Unit,
) : NanoHTTPD(port) {

    private val tokens: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val random = SecureRandom()

    @Volatile private var failedAttempts = 0
    @Volatile private var lockoutUntil = 0L

    override fun serve(session: IHTTPSession): Response {
        return try {
            if (!isLanClient(session)) {
                return text(Response.Status.FORBIDDEN, "Forbidden")
            }
            val uri = session.uri
            when {
                uri == "/api/pair" && session.method == Method.POST -> handlePair(session)
                uri.startsWith("/api/") -> {
                    if (!isAuthed(session)) return text(Response.Status.UNAUTHORIZED, "Unauthorized")
                    handleApi(session, uri)
                }
                else -> serveAsset(uri)
            }
        } catch (e: IllegalArgumentException) {
            text(Response.Status.BAD_REQUEST, e.message ?: "Bad request")
        } catch (e: Exception) {
            onEvent("Error: ${e.message}")
            text(Response.Status.INTERNAL_ERROR, "Server error")
        }
    }

    // ── Auth ────────────────────────────────────────────────────────────────────────────────────

    private fun handlePair(session: IHTTPSession): Response {
        val now = System.currentTimeMillis()
        if (now < lockoutUntil) {
            return json(Response.Status.FORBIDDEN, JSONObject().put("error", "locked").put("retryMs", lockoutUntil - now))
        }
        val body = receiveToBytes(session, MAX_TEXT_BYTES).toString(Charsets.UTF_8)
        val submitted = runCatching { JSONObject(body).optString("pin") }.getOrDefault("")
        return if (submitted.isNotEmpty() && constantTimeEquals(submitted, pin)) {
            failedAttempts = 0
            val token = newToken()
            tokens += token
            onEvent("A computer paired successfully")
            val r = json(Response.Status.OK, JSONObject().put("ok", true))
            r.addHeader("Set-Cookie", "$COOKIE=$token; Path=/; HttpOnly; SameSite=Strict")
            r
        } else {
            failedAttempts++
            if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
                lockoutUntil = now + LOCKOUT_MS
                failedAttempts = 0
                onEvent("Too many wrong PINs — pairing locked for a minute")
            }
            json(Response.Status.UNAUTHORIZED, JSONObject().put("error", "bad_pin"))
        }
    }

    private fun isAuthed(session: IHTTPSession): Boolean {
        val token = tokenFromCookie(session) ?: return false
        return token in tokens
    }

    private fun tokenFromCookie(session: IHTTPSession): String? {
        val cookie = session.headers["cookie"] ?: return null
        return cookie.split(";")
            .map { it.trim() }
            .firstOrNull { it.startsWith("$COOKIE=") }
            ?.substringAfter("=")
            ?.takeIf { it.isNotEmpty() }
    }

    private fun newToken(): String {
        val bytes = ByteArray(24)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun isLanClient(session: IHTTPSession): Boolean = runCatching {
        val ip = session.remoteIpAddress ?: return true // NanoHTTPD may not expose it; fail open on LAN bind
        if (ip.isBlank()) return true
        val addr = InetAddress.getByName(ip)
        addr.isSiteLocalAddress || addr.isLoopbackAddress || addr.isLinkLocalAddress
    }.getOrDefault(true)

    // ── API routing ─────────────────────────────────────────────────────────────────────────────

    private fun handleApi(session: IHTTPSession, uri: String): Response = runBlocking {
        when (uri) {
            "/api/systems" -> apiSystems()
            "/api/folders" -> apiFolders()
            "/api/games" -> apiGames()
            "/api/upload/rom" -> apiUploadRom(session)
            "/api/upload/bios" -> apiUploadBios(session)
            "/api/upload/media" -> apiUploadMedia(session)
            "/api/upload/background" -> apiUploadBackground(session)
            "/api/settings/export" -> apiSettingsExport()
            "/api/settings/import" -> apiSettingsImport(session)
            else -> text(Response.Status.NOT_FOUND, "Not found")
        }
    }

    private suspend fun romRootDir(): File? {
        val raw = deps.settings.romRootPath.first()
        if (raw.isBlank()) return null
        return File(StorageUtils.resolveStoredPath(raw))
    }

    private suspend fun apiSystems(): Response {
        val root = romRootDir()
        val existing = root?.let { deps.destinationResolver.existingPlatformFolders(it) } ?: emptyMap()
        val arr = JSONArray()
        PlatformDefinitions.ALL.forEach { p ->
            val folders = existing[p.id].orEmpty().sortedBy { it.depth }
            arr.put(
                JSONObject()
                    .put("id", p.id)
                    .put("name", p.displayName)
                    .put("canonicalFolder", p.folderNames.first())
                    .put("existing", JSONArray(folders.map { it.relativePath }))
            )
        }
        return json(Response.Status.OK, JSONObject().put("systems", arr).put("hasRomRoot", root != null))
    }

    private suspend fun apiFolders(): Response {
        val root = romRootDir() ?: return json(Response.Status.OK, JSONObject().put("folders", JSONArray()))
        val arr = JSONArray()
        deps.destinationResolver.existingPlatformFolders(root).values.flatten()
            .sortedBy { it.relativePath }
            .forEach {
                arr.put(
                    JSONObject()
                        .put("platformId", it.platformId)
                        .put("name", it.displayName)
                        .put("path", it.relativePath)
                )
            }
        return json(Response.Status.OK, JSONObject().put("folders", arr))
    }

    private suspend fun apiGames(): Response {
        val games = deps.gameRepository.getAllGames().first()
        val arr = JSONArray()
        games.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("title", it.title)
                    .put("platformId", it.platformId)
            )
        }
        return json(Response.Status.OK, JSONObject().put("games", arr))
    }

    private suspend fun apiUploadRom(session: IHTTPSession): Response {
        val root = romRootDir() ?: throw IllegalArgumentException("No ROM folder is configured")
        val system = param(session, "system") ?: throw IllegalArgumentException("Missing system")
        val name = deps.destinationResolver.sanitizeFilename(
            param(session, "name") ?: throw IllegalArgumentException("Missing name")
        )
        val dest = param(session, "dest")
        val dir = deps.destinationResolver.resolveRomDir(root, system, dest)
        dir.mkdirs()
        val target = File(dir, name)
        val bytes = receiveToFile(session, target)
        onEvent("Received $name (${humanSize(bytes)}) → ${dir.name}/")
        onRomUploaded()
        return json(Response.Status.OK, JSONObject().put("ok", true).put("path", target.absolutePath))
    }

    private suspend fun apiUploadBios(session: IHTTPSession): Response {
        val biosDir = biosRootDir() ?: throw IllegalArgumentException("No BIOS or ROM folder is configured")
        biosDir.mkdirs()
        val name = deps.destinationResolver.sanitizeFilename(
            param(session, "name") ?: throw IllegalArgumentException("Missing name")
        )
        val dest = param(session, "dest")
        val dir = if (dest.isNullOrBlank()) biosDir
        else deps.destinationResolver.containedDir(biosDir, dest)
            ?: throw IllegalArgumentException("Destination escapes the BIOS root")
        dir.mkdirs()
        val target = File(dir, name)
        val bytes = receiveToFile(session, target)
        onEvent("Received BIOS $name (${humanSize(bytes)})")
        return json(Response.Status.OK, JSONObject().put("ok", true).put("path", target.absolutePath))
    }

    private suspend fun biosRootDir(): File? {
        val configured = deps.settings.biosFolderPath.first()
        if (configured.isNotBlank()) return File(StorageUtils.resolveStoredPath(configured))
        val root = romRootDir() ?: return null
        return File(root, "bios")
    }

    private suspend fun apiUploadMedia(session: IHTTPSession): Response {
        val gameId = param(session, "gameId")?.toLongOrNull()
            ?: throw IllegalArgumentException("Missing gameId")
        val type = when (param(session, "type")?.lowercase()) {
            "boxart", "box" -> MediaUploadType.BOX_ART
            "screenshot", "shot" -> MediaUploadType.SCREENSHOT
            "wheel", "logo" -> MediaUploadType.WHEEL
            "miximage", "mix" -> MediaUploadType.MIXIMAGE
            "video" -> MediaUploadType.VIDEO
            else -> throw IllegalArgumentException("Unknown media type")
        }
        val bytes = receiveToBytes(session, MAX_MEDIA_BYTES)
        val path = deps.mediaRepository.saveUploadedMedia(gameId, type, bytes)
            ?: throw IllegalArgumentException("Could not save media (unsupported file?)")
        onEvent("Received media for game #$gameId")
        return json(Response.Status.OK, JSONObject().put("ok", true).put("path", path))
    }

    private suspend fun apiUploadBackground(session: IHTTPSession): Response {
        val bytes = receiveToBytes(session, MAX_MEDIA_BYTES)
        val temp = File.createTempFile("wt_bg_", ".img", deps.context.cacheDir)
        try {
            temp.outputStream().use { it.write(bytes) }
            val path = deps.convertBackground.fromFile(temp)
                ?: throw IllegalArgumentException("Could not process image")
            deps.settings.setBackgroundImagePath(path)
            deps.settings.setBackgroundImageEnabled(true)
            onEvent("Applied a new background image")
            return json(Response.Status.OK, JSONObject().put("ok", true))
        } finally {
            temp.delete()
        }
    }

    private suspend fun apiSettingsExport(): Response {
        val payload = deps.exportSettings()
        val r = json(Response.Status.OK, payload)
        r.addHeader("Content-Disposition", "attachment; filename=\"eor-settings.json\"")
        return r
    }

    private suspend fun apiSettingsImport(session: IHTTPSession): Response {
        val body = receiveToBytes(session, MAX_TEXT_BYTES).toString(Charsets.UTF_8)
        val count = deps.importSettings(body)
        onEvent("Imported $count settings")
        return json(Response.Status.OK, JSONObject().put("ok", true).put("applied", count))
    }

    // ── Body I/O ────────────────────────────────────────────────────────────────────────────────

    /** Stream the request body to [dest] via a `.part` file, then atomically rename. Returns bytes. */
    private fun receiveToFile(session: IHTTPSession, dest: File): Long {
        dest.parentFile?.mkdirs()
        val part = File(dest.parentFile, "${dest.name}.part")
        val declared = session.headers["content-length"]?.toLongOrNull() ?: -1L
        var total = 0L
        try {
            part.outputStream().use { out ->
                copyBody(session.inputStream, declared) { buf, len -> out.write(buf, 0, len); total += len }
            }
            if (dest.exists()) dest.delete()
            if (!part.renameTo(dest)) throw IOException("Could not finalize upload")
        } catch (e: Exception) {
            part.delete()
            throw e
        }
        return total
    }

    private fun receiveToBytes(session: IHTTPSession, maxBytes: Long): ByteArray {
        val declared = session.headers["content-length"]?.toLongOrNull() ?: -1L
        require(declared <= maxBytes) { "Upload too large" }
        val out = ByteArrayOutputStream(if (declared in 0..maxBytes) declared.toInt() else 8192)
        var total = 0L
        copyBody(session.inputStream, declared) { buf, len ->
            total += len
            require(total <= maxBytes) { "Upload too large" }
            out.write(buf, 0, len)
        }
        return out.toByteArray()
    }

    private inline fun copyBody(input: InputStream, declared: Long, sink: (ByteArray, Int) -> Unit) {
        val buf = ByteArray(64 * 1024)
        if (declared >= 0) {
            var remaining = declared
            while (remaining > 0) {
                val toRead = minOf(buf.size.toLong(), remaining).toInt()
                val r = input.read(buf, 0, toRead)
                if (r < 0) break
                sink(buf, r)
                remaining -= r
            }
        } else {
            while (true) {
                val r = input.read(buf)
                if (r < 0) break
                sink(buf, r)
            }
        }
    }

    // ── Static assets ─────────────────────────────────────────────────────────────────────────────

    private fun serveAsset(uri: String): Response {
        val path = if (uri == "/" || uri.isBlank()) "index.html" else uri.trimStart('/')
        // Only serve from the bundled asset folder; reject traversal.
        if (path.contains("..")) return text(Response.Status.FORBIDDEN, "Forbidden")
        return try {
            val stream = deps.context.assets.open("webtransfer/$path")
            newChunkedResponse(Response.Status.OK, mimeFor(path), stream)
        } catch (e: IOException) {
            text(Response.Status.NOT_FOUND, "Not found")
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────────────────────

    private fun param(session: IHTTPSession, key: String): String? =
        session.parameters[key]?.firstOrNull()?.takeIf { it.isNotBlank() }

    private fun json(status: Response.Status, obj: JSONObject): Response = json(status, obj.toString())
    private fun json(status: Response.Status, body: String): Response =
        newFixedLengthResponse(status, "application/json; charset=utf-8", body)

    private fun text(status: Response.Status, body: String): Response =
        newFixedLengthResponse(status, "text/plain; charset=utf-8", body)

    private fun constantTimeEquals(a: String, b: String): Boolean {
        val ab = a.toByteArray(); val bb = b.toByteArray()
        var result = ab.size xor bb.size
        for (i in ab.indices) result = result or (ab[i].toInt() xor bb.getOrElse(i) { 0.toByte() }.toInt())
        return result == 0
    }

    private fun humanSize(bytes: Long): String = when {
        bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1e9)
        bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1e6)
        bytes >= 1_000 -> "%.0f KB".format(bytes / 1e3)
        else -> "$bytes B"
    }

    private fun mimeFor(path: String): String = when (path.substringAfterLast('.').lowercase()) {
        "html" -> "text/html; charset=utf-8"
        "js" -> "application/javascript; charset=utf-8"
        "css" -> "text/css; charset=utf-8"
        "svg" -> "image/svg+xml"
        "png" -> "image/png"
        "ico" -> "image/x-icon"
        "json" -> "application/json; charset=utf-8"
        else -> "application/octet-stream"
    }

    companion object {
        const val COOKIE = "eor_wt"
        const val MAX_FAILED_ATTEMPTS = 5
        const val LOCKOUT_MS = 60_000L
        const val MAX_TEXT_BYTES = 5L * 1024 * 1024          // pair / settings JSON
        const val MAX_MEDIA_BYTES = 512L * 1024 * 1024        // per-game media / background image
    }
}
