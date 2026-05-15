package com.cobbledex.neoforge

import com.cobbledex.CobbleDexMod
import com.cobbledex.DiagnosticService
import com.cobbledex.PokemonSpriteAtlas
import com.cobbledex.SpawnDataIndex
import com.cobbledex.SpreadsheetExporter
import com.cobbledex.TmTooltipHandler
import com.cobbledex.network.ChunkAssembler
import net.minecraft.client.Minecraft
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent

object CobbleDexNeoForgeClient {

    fun register() {
        NeoForge.EVENT_BUS.addListener(::onClientTick)
        NeoForge.EVENT_BUS.addListener(::onDisconnect)
        NeoForge.EVENT_BUS.addListener(::onRegisterCommands)
        NeoForge.EVENT_BUS.addListener(::onItemTooltip)
    }

    private fun onClientTick(event: ClientTickEvent.Post) {
        SpreadsheetExporter.tick()
        if (Minecraft.getInstance().player != null) {
            CobbleDexMod.tickReloadCheck()
        }
    }

    private fun onDisconnect(event: ClientPlayerNetworkEvent.LoggingOut) {
        SpawnDataIndex.onDisconnect()
        ChunkAssembler.reset()
        CobbleDexMod.resetReloadTimer()
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
                            val loaded = PokemonSpriteAtlas.reload()
                            ctx.source.sendSuccess({ Component.literal(if (loaded) "§aCobbleDex sprite atlas reloaded." else "§eNo CobbleDex sprite atlas found.") }, false)
                            1
                        }
                    )
                )
        )
    }
}
