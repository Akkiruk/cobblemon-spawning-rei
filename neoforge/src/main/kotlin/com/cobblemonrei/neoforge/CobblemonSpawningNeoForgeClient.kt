package com.cobblemonrei.neoforge

import com.cobblemonrei.CobblemonSpawningMod
import me.shedaniel.rei.forge.REIPluginClient
import net.minecraft.client.Minecraft
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ClientTickEvent

@REIPluginClient
class CobblemonSpawningNeoForgeREI : com.cobblemonrei.rei.CobblemonREIClientPlugin()

@EventBusSubscriber(modid = CobblemonSpawningMod.NEOFORGE_MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = [Dist.CLIENT])
object CobblemonSpawningNeoForgeClient {

    private var initialized = false

    @SubscribeEvent
    fun onClientTick(event: ClientTickEvent.Post) {
        if (!initialized && Minecraft.getInstance().player != null) {
            initialized = true
            CobblemonSpawningMod.onClientReady()
        }
    }
}
