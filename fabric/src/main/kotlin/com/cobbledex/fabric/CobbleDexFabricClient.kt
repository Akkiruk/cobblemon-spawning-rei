package com.cobbledex.fabric

import com.cobbledex.CobbleDexMod
import com.cobbledex.DiagnosticService
import com.cobbledex.SpawnDataIndex
import com.cobbledex.network.ClientDataReceiver
import com.cobbledex.network.SpawnSyncHashPayload
import com.cobbledex.network.SpawnSyncPayload
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.network.chat.Component

class CobbleDexFabricClient : ClientModInitializer {
    override fun onInitializeClient() {
        CobbleDexMod.LOGGER.info("[CobbleDex] Fabric client initialized")

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            if (client.player != null) {
                CobbleDexMod.tickReloadCheck()
                ClientDataReceiver.tick()
            }
        }

        // Receive fingerprint from server — compare with local data
        ClientPlayNetworking.registerGlobalReceiver(SpawnSyncHashPayload.TYPE) { payload, _ ->
            ClientDataReceiver.onHashReceived(payload)
        }

        // Receive spawn sync chunks from server (only applied if fingerprint differed)
        ClientPlayNetworking.registerGlobalReceiver(SpawnSyncPayload.TYPE) { payload, _ ->
            ClientDataReceiver.onChunkReceived(payload)
        }

        // Mark data stale on disconnect, keep cached for instant availability on reconnect
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            SpawnDataIndex.onDisconnect()
            ClientDataReceiver.reset()
            CobbleDexMod.resetReloadTimer()
        }

        // Register diagnostic commands
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
