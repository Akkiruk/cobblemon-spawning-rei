package com.cobblemonrei.neoforge

import com.cobblemonrei.CobblemonSpawningMod
import com.cobblemonrei.network.ClientDataReceiver
import com.cobblemonrei.network.SpawnSyncPayload
import com.cobblemonrei.server.ServerDataManager
import net.minecraft.server.level.ServerPlayer
import net.neoforged.bus.api.IEventBus
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.server.ServerStartedEvent
import net.neoforged.neoforge.event.server.ServerStoppingEvent
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent

@Mod(CobblemonSpawningMod.NEOFORGE_MOD_ID)
class CobblemonSpawningNeoForge(modBus: IEventBus) {
    init {
        CobblemonSpawningMod.init()

        // Register payload on mod bus
        modBus.addListener(::onRegisterPayloads)

        // Server events on game bus
        NeoForge.EVENT_BUS.addListener(::onServerStarted)
        NeoForge.EVENT_BUS.addListener(::onServerStopping)
        NeoForge.EVENT_BUS.addListener(::onPlayerJoin)
    }

    private fun onRegisterPayloads(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar("1").optional()
        registrar.playToClient(
            SpawnSyncPayload.TYPE,
            SpawnSyncPayload.STREAM_CODEC
        ) { payload, context ->
            context.enqueueWork { ClientDataReceiver.onChunkReceived(payload) }
        }
    }

    private fun onServerStarted(event: ServerStartedEvent) {
        ServerDataManager.onServerReady(event.server)
    }

    private fun onServerStopping(event: ServerStoppingEvent) {
        ServerDataManager.reset()
    }

    private fun onPlayerJoin(event: PlayerEvent.PlayerLoggedInEvent) {
        val player = event.entity as? ServerPlayer ?: return
        ServerDataManager.onPlayerJoin(player)
    }
}
