package com.cobbledex.fabric

import com.cobbledex.CobbleDexMod
import com.cobbledex.DiagnosticService
import com.cobbledex.SpawnDataIndex
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.network.chat.Component

class CobbleDexFabricClient : ClientModInitializer {
    override fun onInitializeClient() {
        CobbleDexMod.init()
        CobbleDexMod.LOGGER.info("[CobbleDex] Fabric client initialized")

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            if (client.player != null) {
                CobbleDexMod.tickReloadCheck()
            }
        }

        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            SpawnDataIndex.onDisconnect()
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
