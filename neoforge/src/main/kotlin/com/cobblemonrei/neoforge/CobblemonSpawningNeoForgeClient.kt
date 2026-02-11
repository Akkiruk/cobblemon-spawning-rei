package com.cobblemonrei.neoforge

import com.cobblemonrei.CobblemonSpawningMod
import com.cobblemonrei.SpawnDataIndex
import com.cobblemonrei.network.ClientDataReceiver
import net.minecraft.client.Minecraft
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.common.NeoForge

object CobblemonSpawningNeoForgeClient {

    fun register() {
        NeoForge.EVENT_BUS.addListener(::onClientTick)
        NeoForge.EVENT_BUS.addListener(::onDisconnect)
    }

    private fun onClientTick(event: ClientTickEvent.Post) {
        if (Minecraft.getInstance().player != null) {
            CobblemonSpawningMod.tickReloadCheck()
            ClientDataReceiver.tick()
        }
    }

    private fun onDisconnect(event: ClientPlayerNetworkEvent.LoggingOut) {
        SpawnDataIndex.onDisconnect()
        ClientDataReceiver.reset()
        CobblemonSpawningMod.resetReloadTimer()
    }
}
