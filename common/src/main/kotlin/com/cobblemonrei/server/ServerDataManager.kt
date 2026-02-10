package com.cobblemonrei.server

import com.cobblemonrei.DebugLog
import com.cobblemonrei.EvolutionDataLoader
import com.cobblemonrei.SpawnDataLoader
import com.cobblemonrei.network.DataSerializer
import com.cobblemonrei.network.SpawnSyncPayload
import com.cobblemonrei.platform.PlatformHelper
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.storage.LevelResource

object ServerDataManager {

    @Volatile
    private var cachedCompressed: ByteArray? = null

    @Volatile
    private var dataLoaded = false

    fun onServerReady(server: MinecraftServer) {
        if (!server.isDedicatedServer) return
        loadAndCache(server)
    }

    fun onPlayerJoin(player: ServerPlayer) {
        val server = player.server ?: return
        if (!server.isDedicatedServer) return
        if (!dataLoaded) loadAndCache(server)
        sendToPlayer(player)
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
            dataLoaded = true

            DebugLog.info(
                "Server data cached: ${spawns.size} spawn species, ${evolutions.size} evolution species, " +
                "${speciesInfo.size} species info (${cachedCompressed?.size ?: 0} bytes compressed)"
            )
        } catch (e: Exception) {
            DebugLog.warn("Server data load failed: ${e.message}")
        }
    }

    private fun sendToPlayer(player: ServerPlayer) {
        val data = cachedCompressed ?: return
        val chunks = DataSerializer.splitIntoChunks(data)
        DebugLog.info("Sending spawn data to ${player.name.string}: ${data.size} bytes in ${chunks.size} chunk(s)")

        try {
            for ((i, chunk) in chunks.withIndex()) {
                PlatformHelper.sendPayloadToPlayer(player, SpawnSyncPayload(i, chunks.size, chunk))
            }
        } catch (e: Exception) {
            DebugLog.warn("Failed to send spawn data to ${player.name.string}: ${e.message}")
        }
    }

    fun reset() {
        cachedCompressed = null
        dataLoaded = false
    }
}
