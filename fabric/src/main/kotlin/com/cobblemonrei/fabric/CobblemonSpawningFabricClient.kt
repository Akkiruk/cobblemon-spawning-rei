package com.cobblemonrei.fabric

import com.cobblemonrei.CobblemonSpawningMod
import com.cobblemonrei.SpawnDataIndex
import com.cobblemonrei.network.ClientDataReceiver
import com.cobblemonrei.network.SpawnSyncHashPayload
import com.cobblemonrei.network.SpawnSyncPayload
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking

class CobblemonSpawningFabricClient : ClientModInitializer {
    override fun onInitializeClient() {
        CobblemonSpawningMod.LOGGER.info("[CobblemonSpawningREI] Fabric client initialized")

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            if (client.player != null) {
                CobblemonSpawningMod.tickReloadCheck()
                ClientDataReceiver.tick()
            }
        }

        // Receive fingerprint from server — compare with local data
        ClientPlayNetworking.registerGlobalReceiver(SpawnSyncHashPayload.TYPE) { payload, _ ->
            ClientDataReceiver.onHashReceived(payload)
        }

        // Receive spawn sync chunks from server (only applied if fingerprint differed)
        ClientPlayNetworking.registerGlobalReceiver(SpawnSyncPayload.TYPE) { payload, _ ->
            ClientDataReceiver.onChunkReceived(payload)
        }

        // Clear server data on disconnect so local data reloads next session
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            SpawnDataIndex.onDisconnect()
            ClientDataReceiver.reset()
        }
    }
}
