package com.cobbledex.fabric

import com.cobbledex.CobbleDexMod
import com.cobbledex.DebugLog
import com.cobbledex.DiagnosticService
import com.cobbledex.SpawnDataIndex
import com.cobbledex.SpreadsheetExporter
import com.cobbledex.PokemonSpriteAtlas
import com.cobbledex.TmTooltipHandler
import com.cobbledex.network.ChunkAssembler
import com.cobbledex.network.ChunkedSpawnSyncPayload
import com.cobbledex.network.CobbleworkersJobSyncPayload
import com.cobbledex.network.SpawnSyncPayload
import com.cobbledex.network.SpawnSyncSerializer
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.chat.Component

class CobbleDexFabricClient : ClientModInitializer {
    override fun onInitializeClient() {
        CobbleDexMod.init()
        CobbleDexMod.LOGGER.info("[CobbleDex] Fabric client initialized")

        ClientPlayNetworking.registerGlobalReceiver(SpawnSyncPayload.TYPE) { payload, context ->
            context.client().execute {
                try {
                    val bundle = SpawnSyncSerializer.deserialize(payload.data)
                    SpawnDataIndex.applyServerSync(bundle.spawns, bundle.evolutions, bundle.speciesInfo, bundle.jobRules, bundle.fossils)
                    DebugLog.info("Received sync from server: ${bundle.spawns.size} spawns, ${bundle.evolutions.size} evolutions, ${bundle.fossils?.size ?: 0} fossils")
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
                    SpawnDataIndex.applyServerSync(bundle.spawns, bundle.evolutions, bundle.speciesInfo, bundle.jobRules, bundle.fossils)
                    DebugLog.info("Received chunked sync from server: ${bundle.spawns.size} spawns, ${bundle.evolutions.size} evolutions, ${bundle.fossils?.size ?: 0} fossils")
                } catch (e: Exception) {
                    CobbleDexMod.LOGGER.error("[CobbleDex] Failed to process chunked sync: ${e.message}")
                    ChunkAssembler.reset()
                }
            }
        }

        // Register CobbleCrew job sync packet (sent by CobbleCrew server-side)
        PayloadTypeRegistry.playS2C().register(CobbleworkersJobSyncPayload.TYPE, CobbleworkersJobSyncPayload.CODEC)
        ClientPlayNetworking.registerGlobalReceiver(CobbleworkersJobSyncPayload.TYPE) { payload, context ->
            context.client().execute {
                try {
                    CobbleworkersJobSyncPayload.applyJobRules(payload.data)
                } catch (e: Exception) {
                    CobbleDexMod.LOGGER.error("[CobbleDex] Failed to process CobbleCrew job sync: ${e.message}")
                }
            }
        }

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            SpreadsheetExporter.tick()
            if (client.player != null) {
                CobbleDexMod.tickReloadCheck()
            }
        }

        ItemTooltipCallback.EVENT.register { stack, _, _, lines ->
            TmTooltipHandler.appendTooltip(stack, lines)
        }

        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            SpawnDataIndex.onDisconnect()
            ChunkAssembler.reset()
            CobbleDexMod.resetReloadTimer()
        }

        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(
                ClientCommandManager.literal("cobbledex")
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
                    .then(ClientCommandManager.literal("export")
                        .executes { ctx ->
                            SpreadsheetExporter.export { msg ->
                                ctx.source.sendFeedback(Component.literal(msg))
                            }
                        }
                    )
                    .then(ClientCommandManager.literal("sprites")
                        .then(ClientCommandManager.literal("build")
                            .executes { ctx ->
                                PokemonSpriteAtlas.buildAtlas { msg ->
                                    ctx.source.sendFeedback(Component.literal(msg))
                                }
                            }
                        )
                        .then(ClientCommandManager.literal("reload")
                            .executes { ctx ->
                                val loaded = PokemonSpriteAtlas.reload()
                                ctx.source.sendFeedback(Component.literal(if (loaded) "§aCobbleDex sprite atlas reloaded." else "§eNo CobbleDex sprite atlas found."))
                                1
                            }
                        )
                    )
            )
        }
    }
}
