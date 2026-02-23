package com.cobbledex.neoforge

import com.cobbledex.CobbleDexMod
import com.cobbledex.DebugLog
import com.cobbledex.EvolutionDataLoader
import com.cobbledex.SpawnDataIndex
import com.cobbledex.SpawnDataLoader
import com.cobbledex.network.ChunkAssembler
import com.cobbledex.network.ChunkedSpawnSyncPayload
import com.cobbledex.network.CobbleworkersJobSyncPayload
import com.cobbledex.network.SpawnSyncPayload
import com.cobbledex.network.SpawnSyncSerializer
import com.cobbledex.network.SyncBundle
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
                    val bundle = SpawnSyncSerializer.deserialize(payload.data)
                    SpawnDataIndex.applyServerSync(bundle.spawns, bundle.evolutions, bundle.speciesInfo, bundle.jobRules)
                    DebugLog.info("Received sync from server: ${bundle.spawns.size} spawns, ${bundle.evolutions.size} evolutions")
                } catch (e: Exception) {
                    CobbleDexMod.LOGGER.error("[CobbleDex] Failed to process sync: ${e.message}")
                }
            }
        }
        registrar.playToClient(
            ChunkedSpawnSyncPayload.TYPE,
            ChunkedSpawnSyncPayload.CODEC
        ) { payload, context ->
            context.enqueueWork {
                try {
                    val assembled = ChunkAssembler.receiveChunk(payload) ?: return@enqueueWork
                    val bundle = SpawnSyncSerializer.deserialize(assembled)
                    SpawnDataIndex.applyServerSync(bundle.spawns, bundle.evolutions, bundle.speciesInfo, bundle.jobRules)
                    DebugLog.info("Received chunked sync from server: ${bundle.spawns.size} spawns, ${bundle.evolutions.size} evolutions")
                } catch (e: Exception) {
                    CobbleDexMod.LOGGER.error("[CobbleDex] Failed to process chunked sync: ${e.message}")
                    ChunkAssembler.reset()
                }
            }
        }

        // Register CobbleCrew job sync packet (sent by CobbleCrew server-side)
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

    private fun onPlayerJoin(event: PlayerEvent.PlayerLoggedInEvent) {
        val player = event.entity
        if (player !is ServerPlayer) return
        player.server.execute {
            try {
                val bundle = SyncBundle(
                    spawns = SpawnDataLoader.loadFromRuntime(),
                    evolutions = EvolutionDataLoader.loadFromRuntime(),
                    speciesInfo = EvolutionDataLoader.loadSpeciesBasicInfoFromRuntime(),
                )
                val compressed = SpawnSyncSerializer.serialize(bundle)
                val chunks = ChunkedSpawnSyncPayload.split(compressed)
                for (chunk in chunks) {
                    PacketDistributor.sendToPlayer(player, chunk)
                }
                DebugLog.info("Sent chunked sync to ${player.name.string}: ${bundle.spawns.size} spawns, ${bundle.evolutions.size} evolutions, ${bundle.speciesInfo.size} species info, ${compressed.size} bytes in ${chunks.size} chunks")
            } catch (e: Exception) {
                DebugLog.info("Client ${player.name.string} doesn't support sync or send failed: ${e.message}")
            }
        }
    }
}
