package com.cobbledex.neoforge

import com.cobbledex.CobbleDexMod
import com.cobbledex.DataAvailability
import com.cobbledex.DiagnosticService
import com.cobbledex.PokemonSpriteAtlas
import com.cobbledex.SpawnDataIndex
import com.cobbledex.SpreadsheetExporter
import com.cobbledex.TmTooltipHandler
import com.cobbledex.sync.ClientSync
import com.cobbledex.sync.SyncClientTransport
import net.minecraft.client.Minecraft
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent
import net.neoforged.neoforge.network.PacketDistributor

object CobbleDexNeoForgeClient {

    fun register() {
        // Data the server offers that Cobblemon does not sync. The packet types are registered by CobbleDexNeoForge.
        ClientSync.install(object : SyncClientTransport {
            override fun canSend(type: CustomPacketPayload.Type<*>): Boolean =
                Minecraft.getInstance().connection?.hasChannel(type) ?: false

            override fun send(payload: CustomPacketPayload) {
                PacketDistributor.sendToServer(payload)
            }
        })
        NeoForge.EVENT_BUS.addListener(::onClientTick)
        NeoForge.EVENT_BUS.addListener(::onJoin)
        NeoForge.EVENT_BUS.addListener(::onDisconnect)
        NeoForge.EVENT_BUS.addListener(::onRegisterCommands)
        NeoForge.EVENT_BUS.addListener(::onItemTooltip)
    }

    private fun onClientTick(event: ClientTickEvent.Post) {
        ClientSync.tick()
        SpreadsheetExporter.tick()
        if (Minecraft.getInstance().player != null) {
            CobbleDexMod.tickClient()
        }
    }

    private fun onJoin(event: ClientPlayerNetworkEvent.LoggingIn) {
        // Cobblemon's own species_sync lands during login; watch for it rather than assume.
        CobbleDexMod.onJoinedWorld()
        ClientSync.onJoin(
            remoteServer = !DataAvailability.isLocalWorld(),
            serverAddress = Minecraft.getInstance().currentServer?.ip,
        )
    }

    private fun onDisconnect(event: ClientPlayerNetworkEvent.LoggingOut) {
        SpawnDataIndex.onDisconnect()
        CobbleDexMod.onLeftWorld()
        ClientSync.onLeave()
    }

    private fun onItemTooltip(event: ItemTooltipEvent) {
        TmTooltipHandler.appendTooltip(event.itemStack, event.toolTip)
    }

    private fun onRegisterCommands(event: RegisterClientCommandsEvent) {
        event.dispatcher.register(
            Commands.literal("cobbledex")
                .then(Commands.literal("dump")
                    .executes { ctx ->
                        DiagnosticService.dumpDiagnostics { msg ->
                            ctx.source.sendSuccess({ Component.literal(msg) }, false)
                        }
                    }
                )
                .then(Commands.literal("stats")
                    .executes { ctx ->
                        DiagnosticService.showStats { msg ->
                            ctx.source.sendSuccess({ Component.literal(msg) }, false)
                        }
                    }
                )
                .then(Commands.literal("missing")
                    .executes { ctx ->
                        DiagnosticService.showMissing { msg ->
                            ctx.source.sendSuccess({ Component.literal(msg) }, false)
                        }
                    }
                )
                .then(Commands.literal("reload")
                    .executes { ctx ->
                        DiagnosticService.reloadData { msg ->
                            ctx.source.sendSuccess({ Component.literal(msg) }, false)
                        }
                    }
                )
                .then(Commands.literal("datasources")
                    .executes { ctx ->
                        DiagnosticService.compareDataSources { msg ->
                            ctx.source.sendSuccess({ Component.literal(msg) }, false)
                        }
                    }
                )
                .then(Commands.literal("icons")
                    .executes { ctx ->
                        DiagnosticService.showIconSources { msg ->
                            ctx.source.sendSuccess({ Component.literal(msg) }, false)
                        }
                    }
                )
                .then(Commands.literal("export")
                    .executes { ctx ->
                        SpreadsheetExporter.export { msg ->
                            ctx.source.sendSuccess({ Component.literal(msg) }, false)
                        }
                    }
                )
                .then(Commands.literal("sprites")
                    .then(Commands.literal("build")
                        .executes { ctx ->
                            PokemonSpriteAtlas.buildAtlas { msg ->
                                ctx.source.sendSuccess({ Component.literal(msg) }, false)
                            }
                        }
                    )
                    .then(Commands.literal("reload")
                        .executes { ctx ->
                            val loaded = PokemonSpriteAtlas.reload(preferCache = true)
                            ctx.source.sendSuccess({ Component.literal(if (loaded) "§aCobbleDex sprite atlas reloaded." else "§eNo CobbleDex sprite atlas found.") }, false)
                            1
                        }
                    )
                )
        )
    }
}
