package com.cobblemonrei.fabric

import com.cobblemonrei.CobblemonSpawningMod
import net.fabricmc.api.ClientModInitializer

class CobblemonSpawningFabricClient : ClientModInitializer {
    override fun onInitializeClient() {
        CobblemonSpawningMod.LOGGER.info("[CobblemonSpawningREI] Fabric client init")
        CobblemonSpawningMod.onClientReady()
    }
}
