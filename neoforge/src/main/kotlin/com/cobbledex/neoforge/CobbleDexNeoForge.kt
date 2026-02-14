package com.cobbledex.neoforge

import com.cobbledex.CobbleDexMod
import com.cobbledex.network.ClientDataReceiver
import com.cobbledex.network.SpawnSyncHashPayload
import com.cobbledex.network.SpawnSyncPayload
import com.cobbledex.server.ServerDataManager
import net.minecraft.server.level.ServerPlayer
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.common.Mod
import net.neoforged.fml.loading.FMLEnvironment
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.server.ServerStartedEvent
import net.neoforged.neoforge.event.server.ServerStoppingEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent

@Mod(CobbleDexMod.NEOFORGE_MOD_ID)
class CobbleDexNeoForge(modBus: IEventBus) {
    init {
        CobbleDexMod.init()

        modBus.addListener(::onRegisterPayloads)

        if (FMLEnvironment.dist == Dist.CLIENT) {
            CobbleDexNeoForgeClient.register()
        }

        NeoForge.EVENT_BUS.addListener(::onServerStarted)
        NeoForge.EVENT_BUS.addListener(::onServerStopping)
        NeoForge.EVENT_BUS.addListener(::onServerTick)
        NeoForge.EVENT_BUS.addListener(::onPlayerJoin)
        NeoForge.EVENT_BUS.addListener(::onPlayerLeave)
    }

    private fun onRegisterPayloads(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar("1").optional()

        registrar.playToClient(
            SpawnSyncHashPayload.TYPE,
            SpawnSyncHashPayload.STREAM_CODEC
        ) { payload, context ->
            context.enqueueWork { ClientDataReceiver.onHashReceived(payload) }
        }

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

    private fun onServerTick(event: ServerTickEvent.Post) {
        ServerDataManager.onServerTick(event.server)
    }

    private fun onPlayerJoin(event: PlayerEvent.PlayerLoggedInEvent) {
        val player = event.entity as? ServerPlayer ?: return
        ServerDataManager.onPlayerJoin(player)
    }

    private fun onPlayerLeave(event: PlayerEvent.PlayerLoggedOutEvent) {
        val player = event.entity as? ServerPlayer ?: return
        ServerDataManager.onPlayerDisconnect(player)
    }
}
