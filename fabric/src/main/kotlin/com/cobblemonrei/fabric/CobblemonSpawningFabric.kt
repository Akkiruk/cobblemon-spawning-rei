package com.cobblemonrei.fabric

import com.cobblemonrei.CobblemonSpawningMod
import net.fabricmc.api.ModInitializer

class CobblemonSpawningFabric : ModInitializer {
    override fun onInitialize() {
        CobblemonSpawningMod.init()
    }
}
