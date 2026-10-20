package com.cobbledex.server

import com.cobbledex.sync.SyncProtocol
import com.google.gson.GsonBuilder
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

/**
 * `config/cobbledex-server.json`. Every field is optional in the file and clamped when read, so a
 * typo can make the sync less generous but never make it unsafe (chunks above the wire limit,
 * a zero cooldown, an unbounded payload).
 */
class ServerSyncConfig(
    /** Master switch. When false the server answers every request with "refused". */
    val enabled: Boolean = true,
    val opsOnly: Boolean = false,
    chunkKB: Int = SyncProtocol.DEFAULT_CHUNK_BYTES / 1024,
    cooldownSeconds: Int = 60,
    maxPayloadMB: Int = 16,
    /** Species names (`pikachu` or `cobblemon:pikachu`) never sent. */
    val excludeSpecies: List<String> = emptyList(),
    /** Resource ids such as `mypack:spawn_pool_world/secret_*.json`; `*` matches anything. */
    val excludeFiles: List<String> = emptyList(),
) {
    val chunkBytes: Int = (chunkKB.coerceIn(16, SyncProtocol.MAX_CHUNK_BYTES / 1024)) * 1024
    val cooldownMs: Long = cooldownSeconds.coerceIn(5, 3600) * 1000L
    val maxPayloadBytes: Int = maxPayloadMB.coerceIn(1, SyncProtocol.MAX_COMPRESSED_BYTES / (1024 * 1024)) * 1024 * 1024

    private val speciesNames: Set<String> = excludeSpecies.map { it.substringAfter(':').lowercase().trim() }.toSet()
    private val fileMatchers: List<Regex> = excludeFiles.map { glob ->
        Regex(glob.split('*').joinToString(".*") { Regex.escape(it) })
    }

    fun excludesFile(id: String): Boolean = fileMatchers.any { it.matches(id) }

    fun excludesSpecies(name: String?): Boolean = name != null && name.substringAfter(':').lowercase().trim() in speciesNames

    companion object {
        private val LOGGER = LoggerFactory.getLogger("cobbledex-sync")
        private val gson = GsonBuilder().setPrettyPrinting().create()
        const val FILE_NAME = "cobbledex-server.json"

        /** Reads the file, writing the defaults first if there is none. Never throws: a broken file gives the defaults. */
        fun load(configDir: Path): ServerSyncConfig {
            val file = configDir.resolve(FILE_NAME)
            return try {
                if (!Files.exists(file)) {
                    Files.createDirectories(configDir)
                    Files.writeString(file, gson.toJson(Raw()))
                    ServerSyncConfig()
                } else {
                    val raw = gson.fromJson(Files.readString(file), Raw::class.java) ?: Raw()
                    raw.toConfig()
                }
            } catch (e: Exception) {
                LOGGER.warn("Could not read $FILE_NAME ({}), using defaults", e.message)
                ServerSyncConfig()
            }
        }
    }

    /** The shape of the file, with the defaults written out so server owners can see every option. */
    private class Raw(
        val enabled: Boolean = true,
        val opsOnly: Boolean = false,
        val chunkKB: Int = SyncProtocol.DEFAULT_CHUNK_BYTES / 1024,
        val cooldownSeconds: Int = 60,
        val maxPayloadMB: Int = 16,
        val excludeSpecies: List<String>? = emptyList(),
        val excludeFiles: List<String>? = emptyList(),
    ) {
        fun toConfig() = ServerSyncConfig(
            enabled, opsOnly, chunkKB, cooldownSeconds, maxPayloadMB,
            excludeSpecies.orEmpty(), excludeFiles.orEmpty(),
        )
    }
}
