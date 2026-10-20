package com.cobbledex.sync

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.stream.JsonWriter
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import java.io.ByteArrayOutputStream
import java.io.FilterOutputStream
import java.io.OutputStreamWriter
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream

/** Server-side door to the loader's networking. Implemented once per loader. */
interface SyncServerTransport {
    /** True only if this player's client registered [type]; every send is gated on it. */
    fun canSend(player: UUID, type: CustomPacketPayload.Type<*>): Boolean

    /** False when the player is no longer connected. */
    fun send(player: UUID, payload: CustomPacketPayload): Boolean

    fun isOperator(player: UUID): Boolean
}

/** Client-side door to the loader's networking. */
interface SyncClientTransport {
    /** True only if the server we are connected to registered [type]. */
    fun canSend(type: CustomPacketPayload.Type<*>): Boolean

    fun send(payload: CustomPacketPayload)
}

/** 64-bit content hash of a compressed payload. */
object SyncHash {
    fun of(bytes: ByteArray): Long {
        val d = MessageDigest.getInstance("SHA-256").digest(bytes)
        var h = 0L
        for (i in 0 until 8) h = (h shl 8) or (d[i].toLong() and 0xFF)
        return h
    }
}

/** What the client tells the server about itself: the mods it has, as `id@version`. */
object SyncRequestBody {
    private val gson = Gson()

    /** Keeps the list under [SyncProtocol.MAX_REQUEST_BODY_BYTES] by sending fewer mods, never an oversized body. */
    fun encode(mods: Collection<String>): ByteArray {
        val sorted = mods.sorted()
        var count = sorted.size
        while (true) {
            val arr = JsonArray()
            sorted.take(count).forEach { arr.add(it) }
            val bytes = gson.toJson(JsonObject().also { it.add("mods", arr) }).toByteArray(Charsets.UTF_8)
            if (bytes.size <= SyncProtocol.MAX_REQUEST_BODY_BYTES || count == 0) return bytes
            count = count / 2
        }
    }

    /** Null for anything that is not a request body of ours. Never throws. */
    fun parse(bytes: ByteArray): Set<String>? = try {
        val obj = JsonParser.parseString(String(bytes, Charsets.UTF_8)).asJsonObject
        obj.getAsJsonArray("mods")?.mapNotNull { e -> if (e.isJsonPrimitive) e.asString else null }?.toSet() ?: emptySet()
    } catch (_: Exception) {
        null
    }
}

/** One data file as the server found it. */
class SourceFile(val category: String, val id: String, val packId: String, val text: String)

/** The compressed document plus what the header needs to describe it. */
class BuiltPayload(
    val compressed: ByteArray,
    val rawBytes: Int,
    val hash: Long,
    val files: Int,
    val skipped: Int,
    val invalid: Int,
) {
    fun chunkCountFor(chunkBytes: Int): Int = (compressed.size + chunkBytes - 1) / chunkBytes

    fun chunk(index: Int, chunkBytes: Int): ByteArray {
        val from = index * chunkBytes
        val to = minOf(compressed.size, from + chunkBytes)
        return compressed.copyOfRange(from, to)
    }
}

/**
 * Writes `{ "v": 1, "categories": { <category>: { "<ns>:<path>": <json> } } }` as one raw-deflate
 * stream. One shared stream compresses far better than per-file (measured: 0.84 MB for everything
 * against 3.5 MB), and reading each file through a JSON parser means one broken file is dropped
 * instead of breaking the whole document.
 */
object PayloadWriter {
    private val gson = Gson()

    /**
     * [skip] decides per file, e.g. "this client already has that mod's data". [skippedMods] are the mod ids
     * whose files were left out for that reason, written into the document so the client knows its own copies
     * of those mods match the server's. [origin] names where a species came from, for the "Added by" line.
     */
    fun write(
        files: List<SourceFile>,
        skip: (SourceFile) -> Boolean,
        exclude: (SourceFile, JsonObject) -> Boolean = { _, _ -> false },
        skippedMods: Collection<String> = emptyList(),
        origin: (SourceFile) -> String? = { null },
    ): BuiltPayload {
        val sink = ByteArrayOutputStream()
        var raw = 0
        var written = 0
        var skipped = 0
        var invalid = 0

        val deflater = Deflater(Deflater.BEST_COMPRESSION, true)
        // Counts the uncompressed UTF-8 bytes, which is what the client's inflate limit is about.
        val counted = object : FilterOutputStream(DeflaterOutputStream(sink, deflater)) {
            override fun write(b: Int) {
                raw++
                out.write(b)
            }

            override fun write(b: ByteArray, off: Int, len: Int) {
                raw += len
                out.write(b, off, len)
            }
        }

        val origins = LinkedHashMap<String, String>()
        JsonWriter(OutputStreamWriter(counted, Charsets.UTF_8)).use { w ->
            w.beginObject()
            w.name("v").value(SyncProtocol.VERSION)
            w.name("mods").beginArray()
            skippedMods.sorted().forEach { w.value(it) }
            w.endArray()
            w.name("categories").beginObject()
            val byCategory = files.groupBy { it.category }
            for (category in SyncProtocol.CATEGORIES) {
                w.name(category).beginObject()
                for (f in byCategory[category].orEmpty()) {
                    if (skip(f)) { skipped++; continue }
                    val parsed = try {
                        JsonParser.parseString(f.text)
                    } catch (_: Exception) {
                        null
                    }
                    if (parsed == null || !parsed.isJsonObject) { invalid++; continue }
                    if (exclude(f, parsed.asJsonObject)) { skipped++; continue }
                    w.name(f.id)
                    gson.toJson(parsed, w)
                    written++
                    if (category == "species") origin(f)?.let { origins[f.id] = it }
                }
                w.endObject()
            }
            w.endObject()
            w.name("origins").beginObject()
            origins.forEach { (id, name) -> w.name(id).value(name) }
            w.endObject()
            w.endObject()
        }
        deflater.end()

        val bytes = sink.toByteArray()
        return BuiltPayload(bytes, raw, SyncHash.of(bytes), written, skipped, invalid)
    }
}

/** What the client holds after a successful transfer. */
class ServerOverlay(
    val hash: Long,
    val categories: Map<String, Map<String, JsonObject>>,
    /** Mods whose data the server left out because the client's copy is the same version. */
    val serverMods: Set<String> = emptySet(),
    /** Species file id to the pack or mod it came from. */
    val origins: Map<String, String> = emptyMap(),
) {
    val fileCount: Int get() = categories.values.sumOf { it.size }
}

object OverlayDecoder {
    /**
     * Inflates and parses a payload. Null on anything wrong: a bad stream, more than [maxRaw]
     * inflated bytes (a decompression bomb), the wrong version, or invalid JSON. Never throws.
     */
    fun decode(compressed: ByteArray, hash: Long, maxRaw: Int = SyncProtocol.MAX_RAW_BYTES): ServerOverlay? = try {
        val inflater = Inflater(true)
        val text = InflaterInputStream(compressed.inputStream(), inflater).use { stream ->
            val out = ByteArrayOutputStream()
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = stream.read(buf)
                if (n < 0) break
                if (out.size() + n > maxRaw) return null
                out.write(buf, 0, n)
            }
            out.toString(Charsets.UTF_8)
        }
        val root = JsonParser.parseString(text).asJsonObject
        if (root.get("v")?.asInt != SyncProtocol.VERSION) null
        else {
            val cats = LinkedHashMap<String, Map<String, JsonObject>>()
            val categories = root.getAsJsonObject("categories")
            for (category in SyncProtocol.CATEGORIES) {
                val entries = LinkedHashMap<String, JsonObject>()
                categories.getAsJsonObject(category)?.entrySet()?.forEach { (id, v) ->
                    if (v.isJsonObject) entries[id] = v.asJsonObject
                }
                cats[category] = entries
            }
            val mods = root.getAsJsonArray("mods")?.mapNotNull { if (it.isJsonPrimitive) it.asString else null }?.toSet().orEmpty()
            val origins = LinkedHashMap<String, String>()
            root.getAsJsonObject("origins")?.entrySet()?.forEach { (id, v) -> if (v.isJsonPrimitive) origins[id] = v.asString }
            ServerOverlay(hash, cats, mods, origins)
        }
    } catch (_: Exception) {
        null
    }
}

/**
 * Reassembles one transfer on the client. Strict on purpose: chunks must arrive in order, sizes must
 * add up exactly and the hash must match; any deviation drops the whole transfer and the client
 * falls back to its local data.
 */
class TransferAssembler(private val clock: () -> Long = System::currentTimeMillis) {

    sealed interface Outcome {
        data object Waiting : Outcome
        data class Complete(val compressed: ByteArray, val header: SyncHeaderPayload) : Outcome
        data class Failed(val reason: String) : Outcome
        data object Ignored : Outcome
    }

    private var header: SyncHeaderPayload? = null
    private var buffer: ByteArrayOutputStream? = null
    private var nextIndex = 0
    private var startedAt = 0L

    val active: Boolean get() = header != null

    /** Validates what the server claims before reserving anything. Returns a failure reason or null. */
    fun begin(h: SyncHeaderPayload): String? {
        reset()
        if (h.status != SyncProtocol.STATUS_INCOMING) return "not an incoming transfer"
        if (h.chunkCount !in 1..SyncProtocol.MAX_CHUNKS) return "chunk count ${h.chunkCount} out of range"
        if (h.compressedBytes !in 1..SyncProtocol.MAX_COMPRESSED_BYTES) return "compressed size ${h.compressedBytes} out of range"
        if (h.rawBytes !in 1..SyncProtocol.MAX_RAW_BYTES) return "raw size ${h.rawBytes} out of range"
        if (h.chunkCount.toLong() * SyncProtocol.MAX_CHUNK_BYTES < h.compressedBytes) return "chunk count too small for ${h.compressedBytes} bytes"
        if (h.chunkCount > h.compressedBytes) return "more chunks than bytes"
        header = h
        buffer = ByteArrayOutputStream(h.compressedBytes)
        nextIndex = 0
        startedAt = clock()
        return null
    }

    fun accept(chunk: SyncChunkPayload): Outcome {
        val h = header ?: return Outcome.Ignored
        if (clock() - startedAt > SyncProtocol.TRANSFER_TIMEOUT_MS) return fail("timed out")
        if (chunk.index == SyncChunkPayload.INDEX_MALFORMED) return fail("malformed chunk")
        if (chunk.index == SyncChunkPayload.INDEX_OVERSIZE) return fail("oversized chunk")
        if (chunk.index != nextIndex) return fail("chunk ${chunk.index} arrived, expected $nextIndex")
        val buf = buffer!!
        if (buf.size().toLong() + chunk.data.size > h.compressedBytes) return fail("more data than the header announced")
        buf.write(chunk.data)
        nextIndex++
        if (nextIndex < h.chunkCount) return Outcome.Waiting

        val bytes = buf.toByteArray()
        if (bytes.size != h.compressedBytes) return fail("got ${bytes.size} bytes, header said ${h.compressedBytes}")
        if (SyncHash.of(bytes) != h.hash) return fail("hash mismatch")
        reset()
        return Outcome.Complete(bytes, h)
    }

    /** Called from a tick; drops a transfer that stalled. Returns true if it did. */
    fun expireIfStalled(): Boolean {
        if (header != null && clock() - startedAt > SyncProtocol.TRANSFER_TIMEOUT_MS) {
            reset()
            return true
        }
        return false
    }

    fun reset() {
        header = null
        buffer = null
        nextIndex = 0
    }

    private fun fail(reason: String): Outcome.Failed {
        reset()
        return Outcome.Failed(reason)
    }
}
