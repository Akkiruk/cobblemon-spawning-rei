package com.cobbledex.server

import com.cobbledex.platform.PlatformHelper
import com.cobbledex.sync.SyncRequestPayload
import com.cobbledex.sync.SyncServerTransport
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.Executors

/**
 * The loader-neutral lifecycle of the server half. The loader entrypoints call these from their own
 * events; everything decided here is the same on Fabric and NeoForge.
 *
 * Only a dedicated server starts the sync. A singleplayer or LAN world reads the same files its own
 * client does, so there is nothing to send, and starting would also drop a config file into a
 * player's client config folder.
 */
object ServerSyncRuntime {
    private val log = LoggerFactory.getLogger("cobbledex-sync")
    private val executor by lazy {
        Executors.newSingleThreadExecutor { r -> Thread(r, "CobbleDex-SyncBuild").apply { isDaemon = true } }
    }

    private var service: ServerSyncService? = null

    fun start(server: MinecraftServer, transport: SyncServerTransport) {
        if (!server.isDedicatedServer) return
        try {
            val config = ServerSyncConfig.load(PlatformHelper.getConfigDir())
            val created = ServerSyncService(transport, config, PlatformHelper.getLoadedMods(), executor)
            service = created
            collectInBackground(created, server)
        } catch (e: Throwable) {
            service = null
            log.warn("CobbleDex sync could not start: {}", e.toString())
        }
    }

    /** After `/reload`. Clients that already hold data keep it until they rejoin. */
    fun onReload(server: MinecraftServer) {
        val s = service ?: return
        collectInBackground(s, server)
    }

    /**
     * Reading ~4900 files took 96 ms on the test server, so this is a safeguard for a much larger pack list
     * rather than a fix for a measured stall. The manager is captured here, on the server thread, and read on
     * the worker; the result is handed back through [ServerSyncService.submitSnapshot].
     */
    private fun collectInBackground(s: ServerSyncService, server: MinecraftServer) {
        val manager = server.resourceManager
        executor.execute {
            try {
                val started = System.nanoTime()
                val files = ResourceCollector.collect(manager)
                log.info("CobbleDex sync: read {} data files in {} ms", files.size, (System.nanoTime() - started) / 1_000_000)
                s.submitSnapshot(files)
            } catch (e: Throwable) {
                log.warn("CobbleDex sync could not read the server data: {}", e.toString())
            }
        }
    }

    fun onRequest(player: ServerPlayer, request: SyncRequestPayload) {
        val s = service ?: return
        try {
            s.handleRequest(player.uuid, player.gameProfile.name, request)
        } catch (e: Throwable) {
            log.warn("CobbleDex sync request from {} failed: {}", player.gameProfile.name, e.toString())
        }
    }

    fun tick() {
        val s = service ?: return
        try {
            s.tick()
        } catch (e: Throwable) {
            log.warn("CobbleDex sync tick failed: {}", e.toString())
        }
    }

    fun onDisconnect(player: UUID) {
        service?.onDisconnect(player)
    }

    fun stop() {
        service = null
    }
}
