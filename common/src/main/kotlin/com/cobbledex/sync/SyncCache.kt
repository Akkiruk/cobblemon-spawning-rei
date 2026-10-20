package com.cobbledex.sync

import org.slf4j.LoggerFactory
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/** A transfer saved from an earlier session, still compressed. */
class CachedPayload(val compressed: ByteArray, val hash: Long)

/** Where the client keeps what each server sent, so a reconnect needs no transfer. */
interface SyncCache {
    fun load(key: String): CachedPayload?
    fun store(key: String, compressed: ByteArray, hash: Long)
    fun delete(key: String)
}

/**
 * One file per server under [dir], named by a hash of the server address, written atomically.
 * Every failure is swallowed: a cache that cannot be read or written just means a transfer next time.
 * The most recently used [maxEntries] servers are kept.
 */
class DiskSyncCache(private val dir: Path, private val maxEntries: Int = 8) : SyncCache {
    private val log = LoggerFactory.getLogger("cobbledex-sync")

    override fun load(key: String): CachedPayload? {
        val file = fileFor(key)
        return try {
            if (!Files.isRegularFile(file)) return null
            DataInputStream(Files.newInputStream(file).buffered()).use { input ->
                if (input.readInt() != MAGIC || input.readInt() != SyncProtocol.VERSION) return reject(file, "not a v${SyncProtocol.VERSION} cache file")
                val hash = input.readLong()
                val size = input.readInt()
                if (size !in 1..SyncProtocol.MAX_COMPRESSED_BYTES) return reject(file, "bad size $size")
                val bytes = ByteArray(size)
                input.readFully(bytes)
                if (SyncHash.of(bytes) != hash) return reject(file, "hash mismatch")
                // Touch it so the least recently *used* server is the one pruned.
                try { Files.setLastModifiedTime(file, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis())) } catch (_: Exception) {}
                CachedPayload(bytes, hash)
            }
        } catch (e: Exception) {
            reject(file, e.toString())
        }
    }

    override fun store(key: String, compressed: ByteArray, hash: Long) {
        try {
            Files.createDirectories(dir)
            val target = fileFor(key)
            val temp = Files.createTempFile(dir, "write-", ".tmp")
            try {
                DataOutputStream(Files.newOutputStream(temp).buffered()).use { out ->
                    out.writeInt(MAGIC)
                    out.writeInt(SyncProtocol.VERSION)
                    out.writeLong(hash)
                    out.writeInt(compressed.size)
                    out.write(compressed)
                }
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } finally {
                Files.deleteIfExists(temp)
            }
            prune()
        } catch (e: Exception) {
            log.warn("CobbleDex sync: could not save the server data for next time: {}", e.toString())
        }
    }

    override fun delete(key: String) {
        try { Files.deleteIfExists(fileFor(key)) } catch (_: Exception) {}
    }

    private fun reject(file: Path, why: String): CachedPayload? {
        log.info("CobbleDex sync: discarding the saved server data ({})", why)
        try { Files.deleteIfExists(file) } catch (_: Exception) {}
        return null
    }

    private fun prune() {
        try {
            Files.list(dir).use { stream ->
                val files = stream.filter { it.fileName.toString().endsWith(EXTENSION) }.toList()
                    .sortedByDescending { runCatching { Files.getLastModifiedTime(it).toMillis() }.getOrDefault(0L) }
                files.drop(maxEntries).forEach { try { Files.deleteIfExists(it) } catch (_: Exception) {} }
            }
        } catch (_: Exception) {}
    }

    private fun fileFor(key: String): Path {
        val digest = MessageDigest.getInstance("SHA-256").digest(key.lowercase().trim().toByteArray(Charsets.UTF_8))
        return dir.resolve(digest.take(8).joinToString("") { "%02x".format(it) } + EXTENSION)
    }

    private companion object {
        const val MAGIC = 0x43445853 // "CDXS"
        const val EXTENSION = ".bin"
    }
}
