package com.cobbledex.neoforge

import com.cobbledex.CobbleDexMod
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.common.Mod
import net.neoforged.fml.loading.FMLEnvironment

@Mod(CobbleDexMod.NEOFORGE_MOD_ID)
class CobbleDexNeoForge(modBus: IEventBus) {
    init {
        CobbleDexMod.init()

        if (FMLEnvironment.dist == Dist.CLIENT) {
            CobbleDexNeoForgeClient.register()
        }
    }
}
