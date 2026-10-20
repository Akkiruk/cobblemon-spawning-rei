package com.cobbledex.sync

import com.cobbledex.platform.PlatformHelper
import org.slf4j.LoggerFactory

/**
 * The client half of the sync: asks the server for its data, reassembles the answer and decodes it.
 * Failure at any point leaves [overlay] as it was and the mod on its local data - nothing here throws
 * to the caller, and nothing is sent unless the server registered the channel.
 *
 * Packet receivers run on the game thread; the only heavy steps, inflating and parsing, run on a worker.
 */
object ClientSync {
    private val log = LoggerFactory.getLogger("cobbledex-sync")

    enum class State { IDLE, NO_SERVER_SUPPORT, REQUESTED, RECEIVING, UNCHANGED, DONE, REFUSED, FAILED }

    /** A server that does not answer the request (cooldown, an old build, a busy tick) is given this long. */
    const val REQUEST_TIMEOUT_MS = 15_000L

    private var transport: SyncClientTransport? = null
    private val assembler = TransferAssembler()

    /** Saved copies of earlier transfers; null disables saving. */
    @Volatile
    var cache: SyncCache? = null

    /** Replaceable so tests can drive time. */
    internal var clock: () -> Long = System::currentTimeMillis
    internal var modsProvider: () -> Map<String, String> = { PlatformHelper.getLoadedMods() }
    private var requestedAt = 0L

    /** Bumped on every join/leave so a worker finishing for an old connection drops its result. */
    @Volatile
    private var connection = 0

    /** Bumped whenever newer data supersedes whatever a running decode is about to publish. */
    @Volatile
    private var epoch = 0

    /** Which server the saved copy belongs to, and the hash we offered it under. */
    @Volatile
    private var serverKey: String? = null

    @Volatile
    private var offeredHash = 0L

    /** Set from a worker when the saved copy we offered turned out to be unreadable; acted on from the tick. */
    @Volatile
    private var needsFreshRequest = false

    @Volatile
    var overlay: ServerOverlay? = null
        private set

    /**
     * Told when the server's data becomes available and again, with null, when it goes away. May run on a
     * worker thread. Set once by the client; the sync itself knows nothing about what consumes the data.
     */
    @Volatile
    var listener: ((ServerOverlay?) -> Unit)? = null

    @Volatile
    var state: State = State.IDLE
        private set

    @Volatile
    var detail: String = ""
        private set

    @Volatile
    var receivedBytes: Int = 0
        private set

    /** True while the data in use came from the saved copy and no fresh transfer has replaced it. */
    @Volatile
    var fromSavedCopy: Boolean = false
        private set

    fun install(transport: SyncClientTransport) {
        this.transport = transport
    }

    /**
     * Call once the client is in a world. [remoteServer] is false for singleplayer and for a world this
     * client is hosting itself; the local files already are the world's data there. [serverAddress]
     * names the server for the saved copy; null saves nothing.
     */
    fun onJoin(remoteServer: Boolean, serverAddress: String? = null) {
        connection++
        epoch++
        needsFreshRequest = false
        assembler.reset()
        dropOverlay()
        receivedBytes = 0
        fromSavedCopy = false
        offeredHash = 0L
        serverKey = serverAddress
        val t = transport
        if (!remoteServer || t == null) {
            state = State.IDLE
            detail = if (remoteServer) "no transport" else "local world"
            return
        }
        // The server's registration is the only thing that says it can answer. Without it we send nothing,
        // and a saved copy is not used either: the server may have dropped the mod since.
        if (!t.canSend(SyncRequestPayload.TYPE)) {
            state = State.NO_SERVER_SUPPORT
            detail = "server has no CobbleDex sync"
            log.info("CobbleDex sync: {}; using this client's own files", detail)
            return
        }
        try {
            val saved = serverAddress?.let { cache?.load(it) }
            sendRequest(t, saved?.hash ?: 0L)
            if (saved != null) decodeAsync(saved.compressed, saved.hash, savedCopy = true)
        } catch (e: Throwable) {
            state = State.FAILED
            detail = "could not send the request: ${e.message}"
            log.warn("CobbleDex sync: {}", detail)
        }
    }

    private fun sendRequest(t: SyncClientTransport, cachedHash: Long) {
        val body = SyncRequestBody.encode(modsProvider().map { (id, v) -> "$id@$v" })
        offeredHash = cachedHash
        t.send(SyncRequestPayload(SyncProtocol.VERSION, cachedHash, body))
        requestedAt = clock()
        state = State.REQUESTED
        detail = "asked the server"
    }

    fun onLeave() {
        connection++
        epoch++
        needsFreshRequest = false
        assembler.reset()
        dropOverlay()
        fromSavedCopy = false
        state = State.IDLE
        detail = ""
    }

    fun onHeader(h: SyncHeaderPayload) {
        try {
            when (h.status) {
                SyncProtocol.STATUS_UNCHANGED -> {
                    // Only meaningful if we offered a copy, and the same one the server is vouching for.
                    if (offeredHash == 0L || h.hash != offeredHash) fail("the server said 'unchanged' for data we never offered")
                    else {
                        state = State.UNCHANGED
                        detail = "server data unchanged (saved copy is current)"
                        log.info("CobbleDex sync: {}", detail)
                    }
                }
                SyncProtocol.STATUS_REFUSED -> {
                    state = State.REFUSED
                    detail = "server declined" + if (overlay != null) " (keeping the saved copy)" else ""
                }
                SyncProtocol.STATUS_INCOMING -> {
                    val problem = assembler.begin(h)
                    if (problem != null) fail("header rejected: $problem")
                    else {
                        state = State.RECEIVING
                        detail = "${h.chunkCount} chunks, ${h.compressedBytes / 1024} KB"
                    }
                }
                else -> fail("unknown header status ${h.status}")
            }
        } catch (e: Exception) {
            fail("header handler failed: ${e.message}")
        }
    }

    fun onChunk(c: SyncChunkPayload) {
        try {
            when (val outcome = assembler.accept(c)) {
                TransferAssembler.Outcome.Waiting, TransferAssembler.Outcome.Ignored -> Unit
                is TransferAssembler.Outcome.Failed -> fail(outcome.reason)
                is TransferAssembler.Outcome.Complete -> {
                    receivedBytes = outcome.compressed.size
                    decodeAsync(outcome.compressed, outcome.header.hash, savedCopy = false)
                }
            }
        } catch (e: Exception) {
            fail("chunk handler failed: ${e.message}")
        }
    }

    /** Called from the client tick to drop a transfer that stalled or a request nobody answered. */
    fun tick() {
        if (needsFreshRequest) {
            needsFreshRequest = false
            val t = transport
            if (t != null && overlay == null) {
                try { sendRequest(t, 0L) } catch (e: Throwable) { fail("could not send the request: ${e.message}") }
            }
        }
        if (assembler.expireIfStalled()) fail("transfer timed out")
        if (state == State.REQUESTED && clock() - requestedAt > REQUEST_TIMEOUT_MS) fail("server did not answer")
    }

    private fun decodeAsync(compressed: ByteArray, hash: Long, savedCopy: Boolean) {
        val forConnection = connection
        val myEpoch = ++epoch
        Thread({
            val decoded = OverlayDecoder.decode(compressed, hash)
            // A newer join, or newer data from the server, supersedes this result.
            if (forConnection != connection || myEpoch != epoch) return@Thread
            if (decoded == null) {
                if (savedCopy) {
                    serverKey?.let { cache?.delete(it) }
                    log.info("CobbleDex sync: the saved server data could not be read; it was discarded and the server will be asked again")
                    // The server may already have been told we hold it ("unchanged"); ask again without the hash.
                    needsFreshRequest = true
                } else {
                    fail("payload could not be decoded")
                }
                return@Thread
            }
            overlay = decoded
            fromSavedCopy = savedCopy
            if (!savedCopy) {
                state = State.DONE
                detail = "${decoded.fileCount} files from the server"
                log.info("CobbleDex sync: received {} files ({} KB)", decoded.fileCount, compressed.size / 1024)
                serverKey?.let { cache?.store(it, compressed, hash) }
            } else {
                log.info("CobbleDex sync: using the saved copy of the server's data ({} files)", decoded.fileCount)
            }
            notifyListener(decoded)
        }, "CobbleDex-SyncDecode").apply { isDaemon = true }.start()
    }

    private fun dropOverlay() {
        if (overlay != null) {
            overlay = null
            notifyListener(null)
        }
    }

    private fun notifyListener(value: ServerOverlay?) {
        try {
            listener?.invoke(value)
        } catch (e: Throwable) {
            log.warn("CobbleDex sync: the data listener failed: {}", e.toString())
        }
    }

    private fun fail(reason: String) {
        assembler.reset()
        state = State.FAILED
        detail = reason
        log.warn("CobbleDex sync: {}; staying on {}", reason, if (overlay != null) "the data already received" else "local data")
    }
}
