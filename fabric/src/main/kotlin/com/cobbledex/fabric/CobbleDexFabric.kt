package com.cobbledex.fabric

import com.cobbledex.CobbleDexMod
import com.cobbledex.DebugLog
import com.cobbledex.EvolutionDataLoader
import com.cobbledex.FossilDataLoader
import com.cobbledex.SpawnDataLoader
import com.cobbledex.network.ChunkedSpawnSyncPayload
import com.cobbledex.network.SpawnSyncPayload
import com.cobbledex.network.SpawnSyncSerializer
import com.cobbledex.network.SyncBundle
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking

class CobbleDexFabric : ModInitializer {
    override fun onInitialize() {
        PayloadTypeRegistry.playS2C().register(SpawnSyncPayload.TYPE, SpawnSyncPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(ChunkedSpawnSyncPayload.TYPE, ChunkedSpawnSyncPayload.CODEC)

        ServerPlayConnectionEvents.JOIN.register { handler, _, server ->
            server.execute {
                val player = handler.player
                val supportsChunked = ServerPlayNetworking.canSend(player, ChunkedSpawnSyncPayload.TYPE)
                val supportsLegacy = ServerPlayNetworking.canSend(player, SpawnSyncPayload.TYPE)
                if (!supportsChunked && !supportsLegacy) {
                    CobbleDexMod.LOGGER.info("[CobbleDex] Client ${player.name.string} doesn't have CobbleDex, skipping sync")
                    return@execute
                }
                try {
                    val bundle = SyncBundle(
                        spawns = SpawnDataLoader.loadFromRuntime(),
                        evolutions = EvolutionDataLoader.loadFromRuntime(),
                        speciesInfo = EvolutionDataLoader.loadSpeciesBasicInfoFromRuntime(),
                        fossils = FossilDataLoader.loadFromRuntime(),
                    )
                    val totalSpawnEntries = bundle.spawns.values.sumOf { it.size }
                    val compressed = SpawnSyncSerializer.serialize(bundle)
                    if (supportsChunked) {
                        val chunks = ChunkedSpawnSyncPayload.split(compressed)
                        for (chunk in chunks) {
                            ServerPlayNetworking.send(player, chunk)
                        }
                        CobbleDexMod.LOGGER.info("[CobbleDex] Sent sync to ${player.name.string}: ${bundle.spawns.size} species ($totalSpawnEntries spawn entries), ${bundle.evolutions.size} evolutions, ${bundle.speciesInfo.size} species info, ${compressed.size} bytes in ${chunks.size} chunks")
                    } else {
                        ServerPlayNetworking.send(player, SpawnSyncPayload(compressed))
                        CobbleDexMod.LOGGER.info("[CobbleDex] Sent sync to ${player.name.string}: ${bundle.spawns.size} species ($totalSpawnEntries spawn entries), ${bundle.evolutions.size} evolutions, ${bundle.speciesInfo.size} species info, ${compressed.size} bytes")
                    }
                } catch (e: Exception) {
                    CobbleDexMod.LOGGER.warn("[CobbleDex] Failed to send sync data to ${player.name.string}: ${e.message}", e)
                }
            }
        }
    }
}
