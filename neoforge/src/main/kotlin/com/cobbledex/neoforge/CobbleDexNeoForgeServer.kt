package com.cobbledex.neoforge

import com.cobbledex.server.ServerSyncRuntime
import com.cobbledex.sync.SyncServerTransport
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.MinecraftServer
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.event.TagsUpdatedEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.server.ServerStartedEvent
import net.neoforged.neoforge.event.server.ServerStoppingEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.server.ServerLifecycleHooks
import java.util.UUID

/**
 * The server half's NeoForge wiring. Everything it touches exists on a dedicated server;
 * nothing here may reference a `net.minecraft.client` class.
 */
object CobbleDexNeoForgeServer {

    fun register(bus: IEventBus) {
        bus.addListener(::onServerStarted)
        bus.addListener(::onServerStopping)
        bus.addListener(::onTagsUpdated)
        bus.addListener(::onServerTick)
        bus.addListener(::onPlayerLoggedOut)
    }

    private fun onServerStarted(event: ServerStartedEvent) {
        ServerSyncRuntime.start(event.server, NeoForgeServerTransport(event.server))
    }

    private fun onServerStopping(event: ServerStoppingEvent) {
        ServerSyncRuntime.stop()
    }

    /** Fired when a reload finishes on the server (and on a client when tags arrive, which is ignored). */
    private fun onTagsUpdated(event: TagsUpdatedEvent) {
        if (event.updateCause != TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) return
        val server = ServerLifecycleHooks.getCurrentServer() ?: return
        ServerSyncRuntime.onReload(server)
    }

    private fun onServerTick(event: ServerTickEvent.Post) {
        ServerSyncRuntime.tick()
    }

    private fun onPlayerLoggedOut(event: PlayerEvent.PlayerLoggedOutEvent) {
        ServerSyncRuntime.onDisconnect(event.entity.uuid)
    }
}

private class NeoForgeServerTransport(private val server: MinecraftServer) : SyncServerTransport {
    override fun canSend(player: UUID, type: CustomPacketPayload.Type<*>): Boolean {
        val p = server.playerList.getPlayer(player) ?: return false
        return p.connection.hasChannel(type)
    }

    override fun send(player: UUID, payload: CustomPacketPayload): Boolean {
        val p = server.playerList.getPlayer(player) ?: return false
        PacketDistributor.sendToPlayer(p, payload)
        return true
    }

    override fun isOperator(player: UUID): Boolean {
        val p = server.playerList.getPlayer(player) ?: return false
        return server.playerList.isOp(p.gameProfile)
    }
}
