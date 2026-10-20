package com.cobbledex.fabric

import com.cobbledex.CobbleDexMod
import com.cobbledex.DiagnosticService
import com.cobbledex.SpawnDataIndex
import com.cobbledex.SpreadsheetExporter
import com.cobbledex.PokemonSpriteAtlas
import com.cobbledex.TmTooltipHandler
import com.cobbledex.DataAvailability
import com.cobbledex.network.CobbleworkersJobSyncPayload
import com.cobbledex.sync.ClientSync
import com.cobbledex.sync.SyncChunkPayload
import com.cobbledex.sync.SyncClientTransport
import com.cobbledex.sync.SyncHeaderPayload
import com.cobbledex.sync.SyncRequestPayload
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

class CobbleDexFabricClient : ClientModInitializer {
    override fun onInitializeClient() {
        CobbleDexMod.init()
        CobbleDexMod.LOGGER.info("[CobbleDex] Fabric client initialized")

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

        // Data the server offers that Cobblemon does not sync. The packet types are registered by CobbleDexFabric.
        ClientSync.install(object : SyncClientTransport {
            override fun canSend(type: CustomPacketPayload.Type<*>): Boolean = ClientPlayNetworking.canSend(type)
            override fun send(payload: CustomPacketPayload) = ClientPlayNetworking.send(payload)
        })
        ClientPlayNetworking.registerGlobalReceiver(SyncHeaderPayload.TYPE) { payload, _ -> ClientSync.onHeader(payload) }
        ClientPlayNetworking.registerGlobalReceiver(SyncChunkPayload.TYPE) { payload, _ -> ClientSync.onChunk(payload) }

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            ClientSync.tick()
            SpreadsheetExporter.tick()
            if (client.player != null) {
                CobbleDexMod.tickClient()
            }
        }

        ClientPlayConnectionEvents.JOIN.register { _, _, _ ->
            // Cobblemon's own species_sync lands during login; watch for it rather than assume.
            CobbleDexMod.onJoinedWorld()
            ClientSync.onJoin(
                remoteServer = !DataAvailability.isLocalWorld(),
                serverAddress = net.minecraft.client.Minecraft.getInstance().currentServer?.ip,
            )
        }

        ItemTooltipCallback.EVENT.register { stack, _, _, lines ->
            TmTooltipHandler.appendTooltip(stack, lines)
        }

        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            SpawnDataIndex.onDisconnect()
            CobbleDexMod.onLeftWorld()
            ClientSync.onLeave()
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
                    .then(ClientCommandManager.literal("forms")
                        .then(ClientCommandManager.argument("species", com.mojang.brigadier.arguments.StringArgumentType.word())
                            .executes { ctx ->
                                val species = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "species")
                                DiagnosticService.showRawForms(species) { msg ->
                                    ctx.source.sendFeedback(Component.literal(msg))
                                }
                            }
                        )
                    )
                    .then(ClientCommandManager.literal("evo")
                        .then(ClientCommandManager.argument("formKey", com.mojang.brigadier.arguments.StringArgumentType.word())
                            .executes { ctx ->
                                val formKey = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "formKey")
                                DiagnosticService.showEvoPages(formKey) { msg ->
                                    ctx.source.sendFeedback(Component.literal(msg))
                                }
                            }
                        )
                    )
                    .then(ClientCommandManager.literal("reload")
                        .executes { ctx ->
                            DiagnosticService.reloadData { msg ->
                                ctx.source.sendFeedback(Component.literal(msg))
                            }
                        }
                    )
                    .then(ClientCommandManager.literal("datasources")
                        .executes { ctx ->
                            DiagnosticService.compareDataSources { msg ->
                                ctx.source.sendFeedback(Component.literal(msg))
                            }
                        }
                    )
                    .then(ClientCommandManager.literal("icons")
                        .executes { ctx ->
                            DiagnosticService.showIconSources { msg ->
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
                                val loaded = PokemonSpriteAtlas.reload(preferCache = true)
                                ctx.source.sendFeedback(Component.literal(if (loaded) "§aCobbleDex sprite atlas reloaded." else "§eNo CobbleDex sprite atlas found."))
                                1
                            }
                        )
                        .then(ClientCommandManager.literal("export")
                            .executes { ctx ->
                                PokemonSpriteAtlas.exportWebsiteSprites(512) { msg ->
                                    ctx.source.sendFeedback(Component.literal(msg))
                                }
                            }
                            .then(ClientCommandManager.argument("size", com.mojang.brigadier.arguments.IntegerArgumentType.integer(32, 1024))
                                .executes { ctx ->
                                    val size = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "size")
                                    PokemonSpriteAtlas.exportWebsiteSprites(size) { msg ->
                                        ctx.source.sendFeedback(Component.literal(msg))
                                    }
                                }
                            )
                        )
                    )
            )
        }
    }
}
