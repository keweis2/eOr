package com.gamelaunch.frontend.data.webserver

import android.content.Context
import com.gamelaunch.frontend.domain.repository.GameRepository
import com.gamelaunch.frontend.domain.repository.MediaRepository
import com.gamelaunch.frontend.domain.repository.SettingsRepository
import com.gamelaunch.frontend.domain.usecase.ConvertBackgroundImageUseCase
import com.gamelaunch.frontend.domain.usecase.ExportSettingsUseCase
import com.gamelaunch.frontend.domain.usecase.ImportSettingsUseCase
import com.gamelaunch.frontend.domain.usecase.ScanRomsUseCase
import com.gamelaunch.frontend.systemui.preferredLocalAddress
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.InetAddress
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/** Observable state of the Web Transfer server for the settings UI. */
data class WebTransferState(
    val running: Boolean = false,
    val ip: String = "",
    val port: Int = 0,
    val pin: String = "",
    /** Recent activity lines (newest first), shown as a small transfer log. */
    val log: List<String> = emptyList(),
) {
    val url: String get() = if (running && ip.isNotBlank()) "http://$ip:$port" else ""
}

/**
 * Single owner of the Web Transfer server's run-state (mirrors `SyncEngineManager`). The UI persists
 * the enabled flag then calls [start]/[stop], which drive the foreground [WebTransferService]; the
 * service in turn calls [onServiceStart]/[onServiceStop] which actually bind and release the socket.
 */
@Singleton
class WebTransferManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val gameRepository: GameRepository,
    private val mediaRepository: MediaRepository,
    private val convertBackground: ConvertBackgroundImageUseCase,
    private val exportSettings: ExportSettingsUseCase,
    private val importSettings: ImportSettingsUseCase,
    private val destinationResolver: RomDestinationResolver,
    private val scanRomsUseCase: ScanRomsUseCase,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val random = SecureRandom()

    private val _state = MutableStateFlow(WebTransferState())
    val state: StateFlow<WebTransferState> = _state

    @Volatile private var server: WebTransferServer? = null
    @Volatile private var rescanJob: Job? = null

    /** Ask the OS to start the foreground service (which then calls [onServiceStart]). */
    fun start() = WebTransferService.start(context)

    /** Stop the foreground service (which then calls [onServiceStop]). */
    fun stop() = WebTransferService.stop(context)

    @Synchronized
    fun onServiceStart() {
        if (server != null) return
        val port = runCatching {
            kotlinx.coroutines.runBlocking { settings.webTransferPort.first() }
        }.getOrDefault(0).let { if (it in 1..65535) it else DEFAULT_PORT }
        val pin = generatePin()
        val deps = WebTransferDeps(
            context = context,
            settings = settings,
            gameRepository = gameRepository,
            mediaRepository = mediaRepository,
            convertBackground = convertBackground,
            exportSettings = exportSettings,
            importSettings = importSettings,
            destinationResolver = destinationResolver,
        )
        // Bind to the actual LAN interface (or loopback when off Wi‑Fi) rather than 0.0.0.0, so the
        // server is never reachable on interfaces the user didn't intend. Only advertise a URL when
        // there's a real LAN address to reach it on.
        val localAddr = runCatching { preferredLocalAddress() }.getOrNull()
        val onLan = localAddr != null && !localAddr.isLoopbackAddress
        val bindHost = localAddr?.hostAddress
            ?: InetAddress.getLoopbackAddress().hostAddress
            ?: "127.0.0.1"
        val srv = WebTransferServer(
            bindHost = bindHost,
            port = port,
            pin = pin,
            deps = deps,
            onEvent = ::pushLog,
            onRomUploaded = ::scheduleRescan,
        )
        srv.start(SOCKET_READ_TIMEOUT, false)
        server = srv
        _state.value = WebTransferState(
            running = true,
            ip = if (onLan) bindHost else "",
            port = port,
            pin = pin,
            log = if (onLan) emptyList()
            else listOf("Not connected to Wi‑Fi — join a network to get a transfer link"),
        )
    }

    @Synchronized
    fun onServiceStop() {
        rescanJob?.cancel()
        rescanJob = null
        runCatching { server?.stop() }
        server = null
        _state.value = WebTransferState()
    }

    private fun pushLog(line: String) {
        _state.update { it.copy(log = (listOf(line) + it.log).take(MAX_LOG_LINES)) }
    }

    /**
     * Debounced library rescan after ROM uploads. Waits out the scanner's quiet period so a still-
     * arriving file isn't imported half-written, then runs the same hashing scan the app uses.
     */
    private fun scheduleRescan() {
        rescanJob?.cancel()
        rescanJob = scope.launch {
            delay(RESCAN_DEBOUNCE_MS)
            val root = settings.romRootPath.first()
            if (root.isNotBlank()) {
                runCatching { scanRomsUseCase(root).collect { } }
                pushLog("Library rescanned")
            }
        }
    }

    private fun generatePin(): String = "%06d".format(random.nextInt(1_000_000))

    private companion object {
        const val DEFAULT_PORT = 8080
        const val SOCKET_READ_TIMEOUT = 60_000
        const val MAX_LOG_LINES = 30
        // Slightly longer than the scanner's own quiet period so a completed upload is stable on disk.
        const val RESCAN_DEBOUNCE_MS = 12_000L
    }
}
