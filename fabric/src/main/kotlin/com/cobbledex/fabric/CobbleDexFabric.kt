package com.cobbledex.fabric

import com.cobbledex.CobbleDexMod
import com.cobbledex.DebugLog
import com.cobbledex.SpawnDataLoader
import com.cobbledex.network.SpawnSyncPayload
import com.cobbledex.network.SpawnSyncSerializer
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
                    DebugLog.info("Client ${player.name.string} doesn't have CobbleDex, skipping spawn sync")
                    return@execute
                }
                try {
                    val spawns = SpawnDataLoader.loadFromRuntime()
                    if (spawns.isNotEmpty()) {
                        val compressed = SpawnSyncSerializer.serialize(spawns)
                        ServerPlayNetworking.send(player, SpawnSyncPayload(compressed))
                        DebugLog.info("Sent spawn data to ${player.name.string}: ${spawns.size} species, ${compressed.size} bytes")
                    }
                } catch (e: Exception) {
                    CobbleDexMod.LOGGER.warn("[CobbleDex] Failed to send spawn data: ${e.message}")
                }
            }
        }
    }
}
