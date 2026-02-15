package com.cobbledex.neoforge

import com.cobbledex.CobbleDexMod
import com.cobbledex.DebugLog
import com.cobbledex.SpawnDataIndex
import com.cobbledex.SpawnDataLoader
import com.cobbledex.network.SpawnSyncPayload
import com.cobbledex.network.SpawnSyncSerializer
import net.minecraft.server.level.ServerPlayer
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.common.Mod
import net.neoforged.fml.loading.FMLEnvironment
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent

@Mod(CobbleDexMod.NEOFORGE_MOD_ID)
class CobbleDexNeoForge(modBus: IEventBus) {
    init {
        modBus.addListener(::onRegisterPayloadHandlers)
        NeoForge.EVENT_BUS.addListener(::onPlayerJoin)

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
        val registrar = event.registrar(CobbleDexMod.NEOFORGE_MOD_ID).optional()
        registrar.playToClient(
            SpawnSyncPayload.TYPE,
            SpawnSyncPayload.CODEC
        ) { payload, context ->
            context.enqueueWork {
                try {
                    val spawns = SpawnSyncSerializer.deserialize(payload.data)
                    SpawnDataIndex.applyServerSync(spawns)
                    DebugLog.info("Received spawn sync from server: ${spawns.size} species")
                } catch (e: Exception) {
                    CobbleDexMod.LOGGER.error("[CobbleDex] Failed to process spawn sync: ${e.message}")
                }
            }
        }
    }

    private fun onPlayerJoin(event: PlayerEvent.PlayerLoggedInEvent) {
        val player = event.entity
        if (player is ServerPlayer) {
            try {
                val spawns = SpawnDataLoader.loadFromRuntime()
                if (spawns.isNotEmpty()) {
                    val compressed = SpawnSyncSerializer.serialize(spawns)
                    PacketDistributor.sendToPlayer(player, SpawnSyncPayload(compressed))
                    DebugLog.info("Sent spawn data to ${player.name.string}: ${spawns.size} species, ${compressed.size} bytes")
                }
            } catch (e: Exception) {
                CobbleDexMod.LOGGER.warn("[CobbleDex] Failed to send spawn data: ${e.message}")
            }
        }
    }
}
