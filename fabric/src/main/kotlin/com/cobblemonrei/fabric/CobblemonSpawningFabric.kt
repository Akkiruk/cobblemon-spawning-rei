package com.cobblemonrei.fabric

import com.cobblemonrei.CobblemonSpawningMod
import com.cobblemonrei.network.SpawnSyncPayload
import com.cobblemonrei.server.ServerDataManager
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents

class CobblemonSpawningFabric : ModInitializer {
    override fun onInitialize() {
        CobblemonSpawningMod.init()

        // Register S2C payload type for spawn data sync
        PayloadTypeRegistry.playS2C().register(SpawnSyncPayload.TYPE, SpawnSyncPayload.STREAM_CODEC)

        // Send cached spawn data to each joining player (dedicated server only)
        ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
            ServerDataManager.onPlayerJoin(handler.player)
        }

        // Load and cache spawn data when a dedicated server finishes starting
        ServerLifecycleEvents.SERVER_STARTED.register { server ->
            ServerDataManager.onServerReady(server)
        }

        // Clear cached data when server stops
        ServerLifecycleEvents.SERVER_STOPPING.register { _ ->
            ServerDataManager.reset()
        }
    }
}
