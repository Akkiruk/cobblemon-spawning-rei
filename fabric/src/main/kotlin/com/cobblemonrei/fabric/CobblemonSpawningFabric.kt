package com.cobblemonrei.fabric

import com.cobblemonrei.CobblemonSpawningMod
import com.cobblemonrei.network.SpawnSyncHashPayload
import com.cobblemonrei.network.SpawnSyncPayload
import com.cobblemonrei.server.ServerDataManager
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents

class CobblemonSpawningFabric : ModInitializer {
    override fun onInitialize() {
        CobblemonSpawningMod.init()

        // Register S2C payload types
        PayloadTypeRegistry.playS2C().register(SpawnSyncHashPayload.TYPE, SpawnSyncHashPayload.STREAM_CODEC)
        PayloadTypeRegistry.playS2C().register(SpawnSyncPayload.TYPE, SpawnSyncPayload.STREAM_CODEC)

        ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
            ServerDataManager.onPlayerJoin(handler.player)
        }

        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            ServerDataManager.onPlayerDisconnect(handler.player)
        }

        ServerLifecycleEvents.SERVER_STARTED.register { server ->
            ServerDataManager.onServerReady(server)
        }

        ServerLifecycleEvents.SERVER_STOPPING.register { _ ->
            ServerDataManager.reset()
        }

        // Tick-based chunk sending — 1 chunk per tick per player
        ServerTickEvents.END_SERVER_TICK.register { server ->
            ServerDataManager.onServerTick(server)
        }
    }
}
