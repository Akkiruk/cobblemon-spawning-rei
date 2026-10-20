package com.cobbledex.server

import com.cobbledex.sync.SourceFile
import com.cobbledex.sync.SyncProtocol
import net.minecraft.server.packs.resources.ResourceManager
import org.slf4j.LoggerFactory

/** Reads the mirrored folders from the server's resource manager: the files the server actually loaded, pack priority applied. */
object ResourceCollector {
    private val LOGGER = LoggerFactory.getLogger("cobbledex-sync")

    /** Above this much raw JSON the snapshot is dropped rather than held in memory. */
    private const val MAX_RAW_BYTES = 192L * 1024 * 1024

    fun collect(manager: ResourceManager): List<SourceFile> {
        val out = ArrayList<SourceFile>()
        var bytes = 0L
        for (category in SyncProtocol.CATEGORIES) {
            val found = try {
                manager.listResources(category) { it.path.endsWith(".json") }
            } catch (e: Exception) {
                LOGGER.warn("Could not list {}: {}", category, e.message)
                continue
            }
            for ((location, resource) in found) {
                try {
                    val text = resource.open().use { String(it.readAllBytes(), Charsets.UTF_8) }
                    bytes += text.length
                    if (bytes > MAX_RAW_BYTES) {
                        LOGGER.warn("Server data is over {} MB; the CobbleDex sync is disabled for this run", MAX_RAW_BYTES / (1024 * 1024))
                        return emptyList()
                    }
                    out.add(SourceFile(category, location.toString(), resource.sourcePackId(), text))
                } catch (e: Exception) {
                    LOGGER.debug("Skipping unreadable {}: {}", location, e.message)
                }
            }
        }
        return out
    }
}
