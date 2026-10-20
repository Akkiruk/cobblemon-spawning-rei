package com.cobbledex.neoforge

import com.cobbledex.CobbleDexMod
import com.cobbledex.network.CobbleworkersJobSyncPayload
import com.cobbledex.server.ServerSyncRuntime
import com.cobbledex.sync.ClientSync
import com.cobbledex.sync.SyncChunkPayload
import com.cobbledex.sync.SyncHeaderPayload
import com.cobbledex.sync.SyncRequestPayload
import net.minecraft.server.level.ServerPlayer
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.common.Mod
import net.neoforged.fml.loading.FMLEnvironment
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent

/**
 * CobbleDex is a client-side viewer that can also run on a dedicated server, where it answers a
 * CobbleDex client's request for the data files Cobblemon does not sync (see docs/SERVER_COMPANION.md).
 * This class loads on both sides; the client-only parts are guarded by the distribution.
 *
 * The sync channels are registered `optional()`: a client without CobbleDex can join a server that has it,
 * and a CobbleDex client can join a server that does not.
 */
@Mod(CobbleDexMod.NEOFORGE_MOD_ID)
class CobbleDexNeoForge(modBus: IEventBus) {

    init {
        modBus.addListener(::onRegisterPayloadHandlers)
        CobbleDexNeoForgeServer.register(NeoForge.EVENT_BUS)

        if (FMLEnvironment.dist == Dist.CLIENT) {
            try {
                CobbleDexMod.init()
                CobbleDexNeoForgeClient.register()
            } catch (e: Exception) {
                CobbleDexMod.LOGGER.error("[CobbleDex] Client init failed: ${e.message}")
            }
        }
    }

    private fun onRegisterPayloadHandlers(event: RegisterPayloadHandlersEvent) {
        // Server data sync. The types are registered on both sides: the server sends, the client receives.
        val sync = event.registrar(SYNC_NETWORK_VERSION).optional()
        sync.playToServer(SyncRequestPayload.TYPE, SyncRequestPayload.CODEC) { payload, context ->
            context.enqueueWork {
                val player = context.player()
                if (player is ServerPlayer) ServerSyncRuntime.onRequest(player, payload)
            }
        }
        sync.playToClient(SyncHeaderPayload.TYPE, SyncHeaderPayload.CODEC) { payload, context ->
            context.enqueueWork { ClientSync.onHeader(payload) }
        }
        sync.playToClient(SyncChunkPayload.TYPE, SyncChunkPayload.CODEC) { payload, context ->
            context.enqueueWork { ClientSync.onChunk(payload) }
        }

        if (FMLEnvironment.dist != Dist.CLIENT) return
        // CobbleCrew job rules, sent by CobbleCrew's server side on its own channel.
        val cwRegistrar = event.registrar("cobblecrew").optional()
        cwRegistrar.playToClient(
            CobbleworkersJobSyncPayload.TYPE,
            CobbleworkersJobSyncPayload.CODEC
        ) { payload, context ->
            context.enqueueWork {
                try {
                    CobbleworkersJobSyncPayload.applyJobRules(payload.data)
                } catch (e: Exception) {
                    CobbleDexMod.LOGGER.error("[CobbleDex] Failed to process CobbleCrew job sync: ${e.message}")
                }
            }
        }
    }

    private companion object {
        /** Version string of the sync channels; the protocol version is also in every channel id. */
        const val SYNC_NETWORK_VERSION = "1"
    }
}
