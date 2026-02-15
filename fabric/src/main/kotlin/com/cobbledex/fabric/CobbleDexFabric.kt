package com.cobbledex.fabric

import com.cobbledex.CobbleDexMod
import com.cobbledex.DebugLog
import com.cobbledex.EvolutionDataLoader
import com.cobbledex.SpawnDataLoader
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

        ServerPlayConnectionEvents.JOIN.register { handler, _, server ->
            server.execute {
                val player = handler.player
                if (!ServerPlayNetworking.canSend(player, SpawnSyncPayload.TYPE)) {
                    DebugLog.info("Client ${player.name.string} doesn't have CobbleDex, skipping sync")
                    return@execute
                }
                try {
                    val bundle = SyncBundle(
                        spawns = SpawnDataLoader.loadFromRuntime(),
                        evolutions = EvolutionDataLoader.loadFromRuntime(),
                        speciesInfo = EvolutionDataLoader.loadSpeciesBasicInfoFromRuntime()
                    )
                    val compressed = SpawnSyncSerializer.serialize(bundle)
                    ServerPlayNetworking.send(player, SpawnSyncPayload(compressed))
                    DebugLog.info("Sent sync to ${player.name.string}: ${bundle.spawns.size} spawns, ${bundle.evolutions.size} evolutions, ${bundle.speciesInfo.size} species info, ${compressed.size} bytes")
                } catch (e: Exception) {
                    CobbleDexMod.LOGGER.warn("[CobbleDex] Failed to send sync data: ${e.message}")
                }
            }
        }
    }
}
