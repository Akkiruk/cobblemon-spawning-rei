package com.cobblemonrei.neoforge

import com.cobblemonrei.CobblemonSpawningMod
import com.cobblemonrei.SpawnDataIndex
import com.cobblemonrei.network.ClientDataReceiver
import net.minecraft.client.Minecraft
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import net.neoforged.neoforge.client.event.ClientTickEvent

@EventBusSubscriber(modid = CobblemonSpawningMod.NEOFORGE_MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = [Dist.CLIENT])
object CobblemonSpawningNeoForgeClient {

    @SubscribeEvent
    @JvmStatic
    fun onClientTick(event: ClientTickEvent.Post) {
        if (Minecraft.getInstance().player != null) {
            CobblemonSpawningMod.tickReloadCheck()
            ClientDataReceiver.tick()
        }
    }

    @SubscribeEvent
    @JvmStatic
    fun onDisconnect(event: ClientPlayerNetworkEvent.LoggingOut) {
        SpawnDataIndex.onDisconnect()
        ClientDataReceiver.reset()
        CobblemonSpawningMod.resetReloadTimer()
    }
}
