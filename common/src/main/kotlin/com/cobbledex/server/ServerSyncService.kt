package com.cobbledex.server

import com.cobbledex.sync.BuiltPayload
import com.cobbledex.sync.PayloadWriter
import com.cobbledex.sync.SourceFile
import com.cobbledex.sync.SyncChunkPayload
import com.cobbledex.sync.SyncHeaderPayload
import com.cobbledex.sync.SyncProtocol
import com.cobbledex.sync.SyncRequestBody
import com.cobbledex.sync.SyncRequestPayload
import com.cobbledex.sync.SyncServerTransport
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicReference

/**
 * The server half of the sync. Answers a request from a client that asked for it, with the server's
 * data files, and never sends anything unasked (docs/SERVER_COMPANION.md, "Hard rules").
 *
 * All methods except the build job run on the server thread. The transport and the executor are
 * injected so the whole behaviour is testable without a Minecraft server.
 *
 * @param serverMods installed mods on this server, `id` to `version`, used to skip data the client already has.
 */
class ServerSyncService(
    private val transport: SyncServerTransport,
    private val config: ServerSyncConfig,
    private val serverMods: Map<String, String>,
    private val buildExecutor: Executor,
    private val clock: () -> Long = System::currentTimeMillis,
    private val log: Logger = LoggerFactory.getLogger("cobbledex-sync"),
) {
    private class Transfer(val payload: BuiltPayload, val chunkBytes: Int, val chunkCount: Int) {
        var next = 0
    }

    private class Waiting(val player: UUID, val name: String, val cachedHash: Long)

    private class Finished(val key: String, val generation: Int, val payload: BuiltPayload?)

    @Volatile
    private var snapshot: List<SourceFile> = emptyList()

    /** Bumped with every new snapshot, so a payload built from an older one is never cached. */
    private var generation = 0

    /** A snapshot collected on another thread, applied on the next tick so server state stays single-threaded. */
    private val pendingSnapshot = AtomicReference<List<SourceFile>?>(null)

    private val cache = object : LinkedHashMap<String, BuiltPayload>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, BuiltPayload>) = size > MAX_CACHED_VARIANTS
    }
    private val building = HashMap<String, MutableList<Waiting>>()
    private val finished = ConcurrentLinkedQueue<Finished>()
    private val transfers = LinkedHashMap<UUID, Transfer>()
    private val lastRequest = HashMap<UUID, Long>()
    private val lastTransfer = HashMap<UUID, Long>()
    private var ticks = 0

    /** Counters for the log line and for tests. */
    var requestsServed = 0
        private set
    var requestsIgnored = 0
        private set
    var chunksSent = 0
        private set

    /** Replaces the data the server offers. Running transfers finish with the data they started with. */
    fun setSnapshot(files: List<SourceFile>) {
        snapshot = files
        generation++
        cache.clear()
        log.info("CobbleDex sync: {} data files ready to offer", files.size)
        run {
            val byPack = files.groupingBy { it.packId }.eachCount().entries.sortedByDescending { it.value }
            log.info("CobbleDex sync: files per source pack: {}", byPack.take(10).joinToString { "${it.key.takeLast(40)}=${it.value}" })
        }
    }

    /** Safe from any thread. */
    fun submitSnapshot(files: List<SourceFile>) {
        pendingSnapshot.set(files)
    }

    fun handleRequest(player: UUID, name: String, request: SyncRequestPayload) {
        // A client that cannot receive our packets must never be sent one - sending a big packet to a client that
        // does not know the channel is what kicked players before. canSend is that client's own registration.
        if (!transport.canSend(player, SyncChunkPayload.TYPE) || !transport.canSend(player, SyncHeaderPayload.TYPE)) {
            requestsIgnored++
            return
        }

        // Data is still being collected after a start or reload: say so without using up the player's cooldown.
        if (generation == 0) {
            transport.send(player, SyncHeaderPayload.refused())
            requestsIgnored++
            return
        }

        // Two limits. Any request, including one answered "unchanged" with a 21-byte packet, is spaced out so a
        // flood costs next to nothing; only a request that would start a transfer waits out the full cooldown
        // (checked in respond), so a quick rejoin with current data is still answered.
        val now = clock()
        val last = lastRequest[player]
        if (last != null && now - last < MIN_REQUEST_SPACING_MS) {
            requestsIgnored++
            return
        }
        lastRequest[player] = now

        if (request.protocol != SyncProtocol.VERSION || !config.enabled || (config.opsOnly && !transport.isOperator(player)) || snapshot.isEmpty()) {
            transport.send(player, SyncHeaderPayload.refused())
            requestsIgnored++
            return
        }

        val clientMods = SyncRequestBody.parse(request.body).orEmpty()
        val skipMods = serverMods.filter { (id, version) -> "$id@$version" in clientMods }.keys
        val key = skipMods.sorted().joinToString(",")
        val waiting = Waiting(player, name, request.cachedHash)

        val ready = cache[key]
        if (ready != null) {
            respond(waiting, ready)
            return
        }
        val pending = building[key]
        if (pending != null) {
            pending.add(waiting)
            return
        }
        building[key] = mutableListOf(waiting)
        val files = snapshot
        val builtFrom = generation
        buildExecutor.execute {
            val built = try {
                PayloadWriter.write(
                    files,
                    skip = { f -> packMod(f.packId) in skipMods },
                    exclude = { f, json -> config.excludesFile(f.id) || config.excludesSpecies(speciesNameOf(json)) },
                    skippedMods = skipMods,
                    origin = { f -> PackNames.display(f.packId, serverMods.keys) },
                )
            } catch (e: Throwable) {
                log.warn("CobbleDex sync: building the payload failed: {}", e.toString())
                null
            }
            finished.add(Finished(key, builtFrom, built))
        }
    }

    /** Called every server tick. */
    fun tick() {
        if (++ticks % PRUNE_EVERY_TICKS == 0) {
            val cutoff = clock() - REMEMBER_TRANSFERS_MS
            lastTransfer.values.removeIf { it < cutoff }
        }
        pendingSnapshot.getAndSet(null)?.let { setSnapshot(it) }
        while (true) {
            val done = finished.poll() ?: break
            val waiters = building.remove(done.key).orEmpty()
            val payload = done.payload
            if (payload == null) {
                waiters.forEach { transport.send(it.player, SyncHeaderPayload.refused()) }
                continue
            }
            if (done.generation == generation) cache[done.key] = payload
            log.info(
                "CobbleDex sync: built payload '{}' - {} files, {} skipped, {} invalid, {} KB compressed ({} KB raw)",
                done.key.ifEmpty { "<nothing skipped>" }, payload.files, payload.skipped, payload.invalid,
                payload.compressed.size / 1024, payload.rawBytes / 1024,
            )
            waiters.forEach { respond(it, payload) }
        }

        var budget = MAX_CHUNKS_PER_TICK
        val it = transfers.entries.iterator()
        while (it.hasNext() && budget > 0) {
            val (player, transfer) = it.next()
            var perTransfer = MAX_CHUNKS_PER_TRANSFER_TICK
            while (perTransfer > 0 && budget > 0 && transfer.next < transfer.chunkCount) {
                if (!sendChunk(player, transfer)) {
                    transfer.next = transfer.chunkCount
                    break
                }
                perTransfer--
                budget--
            }
            if (transfer.next >= transfer.chunkCount) it.remove()
        }
    }

    fun onDisconnect(player: UUID) {
        transfers.remove(player)
        lastRequest.remove(player)
        // lastTransfer stays: a player who reconnects in a loop must not get a fresh transfer every time.
    }

    fun activeTransfers(): Int = transfers.size

    private fun respond(w: Waiting, payload: BuiltPayload) {
        if (payload.hash == w.cachedHash) {
            transport.send(w.player, SyncHeaderPayload.unchanged(payload.hash))
            requestsServed++
            return
        }
        val lastSent = lastTransfer[w.player]
        if (lastSent != null && clock() - lastSent < config.cooldownMs) {
            transport.send(w.player, SyncHeaderPayload.refused())
            requestsIgnored++
            return
        }
        if (payload.compressed.size > config.maxPayloadBytes || payload.compressed.isEmpty()) {
            log.warn("CobbleDex sync: payload of {} KB is over the configured limit; refusing {}", payload.compressed.size / 1024, w.name)
            transport.send(w.player, SyncHeaderPayload.refused())
            requestsIgnored++
            return
        }
        val chunkBytes = config.chunkBytes
        val chunks = payload.chunkCountFor(chunkBytes)
        if (!transport.send(w.player, SyncHeaderPayload(SyncProtocol.STATUS_INCOMING, payload.hash, chunks, payload.compressed.size, payload.rawBytes))) {
            return
        }
        transfers[w.player] = Transfer(payload, chunkBytes, chunks)
        lastTransfer[w.player] = clock()
        requestsServed++
        log.info("CobbleDex sync: sending {} KB in {} chunks to {}", payload.compressed.size / 1024, chunks, w.name)
    }

    /** False when the chunk could not be sent and the transfer should stop. */
    private fun sendChunk(player: UUID, t: Transfer): Boolean {
        if (!transport.canSend(player, SyncChunkPayload.TYPE)) return false
        val data = t.payload.chunk(t.next, t.chunkBytes)
        // The constructor refuses anything the wire could not carry, so an oversized chunk throws here, on the
        // server thread before anything is sent, instead of in the network encoder where it would kick the player.
        val packet = try {
            SyncChunkPayload(t.next, data)
        } catch (e: IllegalArgumentException) {
            log.warn("CobbleDex sync: refusing to send an oversized chunk: {}", e.message)
            return false
        }
        if (!transport.send(player, packet)) return false
        t.next++
        chunksSent++
        return true
    }

    /**
     * The installed mod a resource pack stands for, or null for a datapack. Fabric names a mod's pack with its bare
     * mod id (`cobblemon`) and NeoForge as `mod/<id>`; datapacks always carry a prefix (`file/...`, `openloader/...`),
     * so an id that is exactly an installed mod's cannot be a datapack.
     */
    private fun packMod(packId: String): String? {
        val id = packId.removePrefix("mod/")
        return if (id in serverMods) id else null
    }

    private fun speciesNameOf(json: com.google.gson.JsonObject): String? {
        val e = json.get("name") ?: json.get("target") ?: return null
        return if (e.isJsonPrimitive) e.asString else null
    }

    private companion object {
        const val MIN_REQUEST_SPACING_MS = 2_000L
        const val PRUNE_EVERY_TICKS = 6_000
        const val REMEMBER_TRANSFERS_MS = 60L * 60 * 1000
        const val MAX_CACHED_VARIANTS = 8
        const val MAX_CHUNKS_PER_TICK = 6
        const val MAX_CHUNKS_PER_TRANSFER_TICK = 2
    }
}
