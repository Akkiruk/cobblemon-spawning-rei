package com.cobblemonrei.neoforge

import com.cobblemonrei.CobblemonSpawningMod
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.common.Mod

@Mod(CobblemonSpawningMod.NEOFORGE_MOD_ID)
class CobblemonSpawningNeoForge(modBus: IEventBus) {
    init {
        CobblemonSpawningMod.init()
    }
}
