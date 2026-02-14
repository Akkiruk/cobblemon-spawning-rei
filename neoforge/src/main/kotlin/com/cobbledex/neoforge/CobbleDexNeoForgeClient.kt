package com.cobbledex.neoforge

import com.cobbledex.CobbleDexMod
import com.cobbledex.DiagnosticService
import com.cobbledex.SpawnDataIndex
import net.minecraft.client.Minecraft
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent
import net.neoforged.neoforge.common.NeoForge

object CobbleDexNeoForgeClient {

    fun register() {
        NeoForge.EVENT_BUS.addListener(::onClientTick)
        NeoForge.EVENT_BUS.addListener(::onDisconnect)
        NeoForge.EVENT_BUS.addListener(::onRegisterCommands)
    }

    private fun onClientTick(event: ClientTickEvent.Post) {
        if (Minecraft.getInstance().player != null) {
            CobbleDexMod.tickReloadCheck()
        }
    }

    private fun onDisconnect(event: ClientPlayerNetworkEvent.LoggingOut) {
        SpawnDataIndex.onDisconnect()
        CobbleDexMod.resetReloadTimer()
    }

    private fun onRegisterCommands(event: RegisterClientCommandsEvent) {
        event.dispatcher.register(
            Commands.literal("spawningrei")
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
        )
    }
}
