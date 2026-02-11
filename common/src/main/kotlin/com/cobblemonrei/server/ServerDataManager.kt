package com.cobblemonrei.server

import com.cobblemonrei.DebugLog
import com.cobblemonrei.EvolutionDataLoader
import com.cobblemonrei.SpawnDataLoader
import com.cobblemonrei.network.DataSerializer
import com.cobblemonrei.network.SpawnSyncHashPayload
import com.cobblemonrei.network.SpawnSyncPayload
import com.cobblemonrei.platform.PlatformHelper
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.storage.LevelResource
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object ServerDataManager {

    @Volatile
    private var cachedCompressed: ByteArray? = null

    @Volatile
    private var cachedFingerprint: String = ""

    @Volatile
    private var dataLoaded = false

    private data class SyncState(
        val chunks: List<ByteArray>,
        var currentIndex: Int,
        var delayTicks: Int,
        var retries: Int = 0
    )

    private const val MAX_RETRIES = 3

    private val pendingSyncs = ConcurrentHashMap<UUID, SyncState>()

    fun onServerReady(server: MinecraftServer) {
        if (!server.isDedicatedServer) return
        loadAndCache(server)
    }

    fun onPlayerJoin(player: ServerPlayer) {
        val server = player.server ?: return
        if (!server.isDedicatedServer) return
        if (!dataLoaded) loadAndCache(server)

        // Send tiny fingerprint first — client compares with local data
        try {
            PlatformHelper.sendPayloadToPlayer(player, SpawnSyncHashPayload(cachedFingerprint))
            DebugLog.info("Sent data fingerprint to ${player.name.string}: $cachedFingerprint")
        } catch (e: Exception) {
            DebugLog.warn("Failed to send fingerprint to ${player.name.string}: ${e.message}")
            return
        }

        // Queue data chunks for delayed sending (100 ticks = ~5 seconds)
        val data = cachedCompressed ?: return
        val chunks = DataSerializer.splitIntoChunks(data)
        pendingSyncs[player.uuid] = SyncState(chunks, 0, 100)
        DebugLog.info("Queued ${chunks.size} data chunk(s) for ${player.name.string} (${data.size} bytes, sending after 5s)")
    }

    fun onServerTick(server: MinecraftServer) {
        if (!server.isDedicatedServer) return
        if (pendingSyncs.isEmpty()) return

        val toRemove = mutableListOf<UUID>()

        for ((uuid, state) in pendingSyncs) {
            if (state.delayTicks > 0) {
                state.delayTicks--
                continue
            }

            val player = server.playerList.getPlayer(uuid)
            if (player == null) {
                toRemove.add(uuid)
                continue
            }

            try {
                val chunk = state.chunks[state.currentIndex]
                PlatformHelper.sendPayloadToPlayer(
                    player,
                    SpawnSyncPayload(state.currentIndex, state.chunks.size, chunk)
                )
                state.retries = 0
            } catch (e: Exception) {
                state.retries++
                if (state.retries >= MAX_RETRIES) {
                    DebugLog.warn("Failed to send chunk ${state.currentIndex} to ${player.name.string} after $MAX_RETRIES retries: ${e.message}")
                    toRemove.add(uuid)
                } else {
                    DebugLog.debug("Retrying chunk ${state.currentIndex} for ${player.name.string} (attempt ${state.retries}/$MAX_RETRIES)")
                    state.delayTicks = 20 // wait 1 second before retry
                }
                continue
            }

            state.currentIndex++
            if (state.currentIndex >= state.chunks.size) {
                DebugLog.info("Finished sending data to ${server.playerList.getPlayer(uuid)?.name?.string ?: uuid}")
                toRemove.add(uuid)
            }
        }

        for (uuid in toRemove) pendingSyncs.remove(uuid)
    }

    fun onPlayerDisconnect(player: ServerPlayer) {
        pendingSyncs.remove(player.uuid)
    }

    private fun loadAndCache(server: MinecraftServer) {
        try {
            val datapacksDir = server.getWorldPath(LevelResource.DATAPACK_DIR)
            DebugLog.info("Loading spawn data on dedicated server (datapacks: $datapacksDir)")

            val spawns = SpawnDataLoader.loadFromAllSources(datapacksDir)

            val evolutions = try {
                EvolutionDataLoader.loadFromRuntime()
            } catch (e: Exception) {
                DebugLog.warn("Evolution load on server failed: ${e.message}")
                emptyMap()
            }

            val speciesInfo = try {
                EvolutionDataLoader.loadSpeciesBasicInfoFromRuntime()
            } catch (e: Exception) {
                DebugLog.warn("Species info load on server failed: ${e.message}")
                emptyMap()
            }

            cachedCompressed = DataSerializer.serialize(spawns, evolutions, speciesInfo)
            cachedFingerprint = DataSerializer.computeFingerprint(spawns, evolutions, speciesInfo)
            dataLoaded = true

            DebugLog.info(
                "Server data cached: ${spawns.size} spawn species, ${evolutions.size} evolution species, " +
                "${speciesInfo.size} species info (${cachedCompressed?.size ?: 0} bytes compressed, fingerprint=$cachedFingerprint)"
            )
        } catch (e: Exception) {
            DebugLog.warn("Server data load failed: ${e.message}")
        }
    }

    fun reset() {
        cachedCompressed = null
        cachedFingerprint = ""
        dataLoaded = false
        pendingSyncs.clear()
    }
}
