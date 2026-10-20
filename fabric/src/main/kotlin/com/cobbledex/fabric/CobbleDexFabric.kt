package com.cobbledex.fabric

import com.cobbledex.server.ServerSyncRuntime
import com.cobbledex.sync.SyncChunkPayload
import com.cobbledex.sync.SyncHeaderPayload
import com.cobbledex.sync.SyncRequestPayload
import com.cobbledex.sync.SyncServerTransport
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.MinecraftServer
import java.util.UUID

/**
 * Runs on both sides. It registers the sync's packet types (a type must be known to both the sender and
 * the receiver) and, on a dedicated server, wires the server half into the server's events.
 *
 * Nothing here may reference a `net.minecraft.client` class: this entrypoint also runs on dedicated servers.
 */
class CobbleDexFabric : ModInitializer {
    override fun onInitialize() {
        PayloadTypeRegistry.playC2S().register(SyncRequestPayload.TYPE, SyncRequestPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(SyncHeaderPayload.TYPE, SyncHeaderPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(SyncChunkPayload.TYPE, SyncChunkPayload.CODEC)

        ServerPlayNetworking.registerGlobalReceiver(SyncRequestPayload.TYPE) { payload, context ->
            ServerSyncRuntime.onRequest(context.player(), payload)
        }

        ServerLifecycleEvents.SERVER_STARTED.register { server ->
            ServerSyncRuntime.start(server, FabricServerTransport(server))
        }
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register { server, _, success ->
            if (success) ServerSyncRuntime.onReload(server)
        }
        ServerLifecycleEvents.SERVER_STOPPING.register { ServerSyncRuntime.stop() }
        ServerTickEvents.END_SERVER_TICK.register { ServerSyncRuntime.tick() }
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            ServerSyncRuntime.onDisconnect(handler.player.uuid)
        }
    }
}

private class FabricServerTransport(private val server: MinecraftServer) : SyncServerTransport {
    override fun canSend(player: UUID, type: CustomPacketPayload.Type<*>): Boolean {
        val p = server.playerList.getPlayer(player) ?: return false
        return ServerPlayNetworking.canSend(p, type)
    }

    override fun send(player: UUID, payload: CustomPacketPayload): Boolean {
        val p = server.playerList.getPlayer(player) ?: return false
        ServerPlayNetworking.send(p, payload)
        return true
    }

    override fun isOperator(player: UUID): Boolean {
        val p = server.playerList.getPlayer(player) ?: return false
        return server.playerList.isOp(p.gameProfile)
    }
}
