package com.cobbledex.fabric

import com.cobbledex.CobbleDexMod
import net.fabricmc.api.ModInitializer

class CobbleDexFabric : ModInitializer {
    override fun onInitialize() {
        CobbleDexMod.init()
    }
}
