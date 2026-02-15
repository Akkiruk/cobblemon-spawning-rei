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
                try {
                    val spawns = SpawnDataLoader.loadFromRuntime()
                    if (spawns.isNotEmpty()) {
                        val compressed = SpawnSyncSerializer.serialize(spawns)
                        ServerPlayNetworking.send(handler.player, SpawnSyncPayload(compressed))
                        DebugLog.info("Sent spawn data to ${handler.player.name.string}: ${spawns.size} species, ${compressed.size} bytes")
                    }
                } catch (e: Exception) {
                    CobbleDexMod.LOGGER.warn("[CobbleDex] Failed to send spawn data: ${e.message}")
                }
            }
        }
    }
}
