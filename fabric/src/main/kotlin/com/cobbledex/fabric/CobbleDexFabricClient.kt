package com.cobbledex.fabric

import com.cobbledex.CobbleDexMod
import com.cobbledex.DebugLog
import com.cobbledex.DiagnosticService
import com.cobbledex.SpawnDataIndex
import com.cobbledex.network.ChunkAssembler
import com.cobbledex.network.ChunkedSpawnSyncPayload
import com.cobbledex.network.SpawnSyncPayload
import com.cobbledex.network.SpawnSyncSerializer
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.network.chat.Component

class CobbleDexFabricClient : ClientModInitializer {
    override fun onInitializeClient() {
        CobbleDexMod.init()
        CobbleDexMod.LOGGER.info("[CobbleDex] Fabric client initialized")

        ClientPlayNetworking.registerGlobalReceiver(SpawnSyncPayload.TYPE) { payload, context ->
            context.client().execute {
                try {
                    val bundle = SpawnSyncSerializer.deserialize(payload.data)
                    SpawnDataIndex.applyServerSync(bundle.spawns, bundle.evolutions, bundle.speciesInfo)
                    DebugLog.info("Received sync from server: ${bundle.spawns.size} spawns, ${bundle.evolutions.size} evolutions")
                } catch (e: Exception) {
                    CobbleDexMod.LOGGER.error("[CobbleDex] Failed to process sync: ${e.message}")
                }
            }
        }

        ClientPlayNetworking.registerGlobalReceiver(ChunkedSpawnSyncPayload.TYPE) { payload, context ->
            context.client().execute {
                try {
                    val assembled = ChunkAssembler.receiveChunk(payload) ?: return@execute
                    val bundle = SpawnSyncSerializer.deserialize(assembled)
                    SpawnDataIndex.applyServerSync(bundle.spawns, bundle.evolutions, bundle.speciesInfo)
                    DebugLog.info("Received chunked sync from server: ${bundle.spawns.size} spawns, ${bundle.evolutions.size} evolutions")
                } catch (e: Exception) {
                    CobbleDexMod.LOGGER.error("[CobbleDex] Failed to process chunked sync: ${e.message}")
                    ChunkAssembler.reset()
                }
            }
        }

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            if (client.player != null) {
                CobbleDexMod.tickReloadCheck()
            }
        }

        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            SpawnDataIndex.onDisconnect()
            ChunkAssembler.reset()
            CobbleDexMod.resetReloadTimer()
        }

        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(
                ClientCommandManager.literal("spawningrei")
                    .then(ClientCommandManager.literal("dump")
                        .executes { ctx ->
                            DiagnosticService.dumpDiagnostics { msg ->
                                ctx.source.sendFeedback(Component.literal(msg))
                            }
                        }
                    )
                    .then(ClientCommandManager.literal("stats")
                        .executes { ctx ->
                            DiagnosticService.showStats { msg ->
                                ctx.source.sendFeedback(Component.literal(msg))
                            }
                        }
                    )
                    .then(ClientCommandManager.literal("missing")
                        .executes { ctx ->
                            DiagnosticService.showMissing { msg ->
                                ctx.source.sendFeedback(Component.literal(msg))
                            }
                        }
                    )
                    .then(ClientCommandManager.literal("reload")
                        .executes { ctx ->
                            DiagnosticService.reloadData { msg ->
                                ctx.source.sendFeedback(Component.literal(msg))
                            }
                        }
                    )
            )
        }
    }
}
