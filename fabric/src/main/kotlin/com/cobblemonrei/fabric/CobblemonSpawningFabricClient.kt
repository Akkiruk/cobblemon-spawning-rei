package com.cobblemonrei.fabric

import com.cobblemonrei.CobblemonSpawningMod
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents

class CobblemonSpawningFabricClient : ClientModInitializer {
    override fun onInitializeClient() {
        CobblemonSpawningMod.LOGGER.info("[CobblemonSpawningREI] Fabric client initialized")
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            if (client.player != null) {
                CobblemonSpawningMod.tickReloadCheck()
            }
        }
    }
}
