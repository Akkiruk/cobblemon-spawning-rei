package com.cobbledex.neoforge

import com.cobbledex.CobbleDexMod
import com.cobbledex.DebugLog
import com.cobbledex.SpawnDataIndex
import com.cobbledex.network.ChunkAssembler
import com.cobbledex.network.ChunkedSpawnSyncPayload
import com.cobbledex.network.CobbleworkersJobSyncPayload
import com.cobbledex.network.ServerSyncPayloadFactory
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
                    val bundle = SpawnSyncSerializer.deserialize(payload.data)
                    SpawnDataIndex.applyServerSync(bundle.spawns, bundle.evolutions, bundle.speciesInfo, bundle.obtainment, bundle.riding, bundle.jobRules, bundle.fossils, bundle.isComplete)
                    DebugLog.info("Received sync from server: ${bundle.spawns.size} spawns, ${bundle.evolutions.size} evolutions, ${bundle.obtainment.size} obtainment, ${bundle.riding.size} riding, ${bundle.fossils?.size ?: 0} fossils")
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
                    SpawnDataIndex.applyServerSync(bundle.spawns, bundle.evolutions, bundle.speciesInfo, bundle.obtainment, bundle.riding, bundle.jobRules, bundle.fossils, bundle.isComplete)
                    DebugLog.info("Received chunked sync from server: ${bundle.spawns.size} spawns, ${bundle.evolutions.size} evolutions, ${bundle.obtainment.size} obtainment, ${bundle.riding.size} riding, ${bundle.fossils?.size ?: 0} fossils")
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
            val prepared = try {
                ServerSyncPayloadFactory.getOrBuild()
            } catch (e: Exception) {
                CobbleDexMod.LOGGER.warn("[CobbleDex] Failed to build sync data for ${player.name.string}: ${e.message}", e)
                return@execute
            }

            try {
                for (chunk in prepared.chunkedPayloads) {
                    PacketDistributor.sendToPlayer(player, chunk)
                }
                CobbleDexMod.LOGGER.info("[CobbleDex] Sent chunked sync to ${player.name.string}: ${prepared.speciesCount} species (${prepared.totalSpawnEntries} spawn entries), ${prepared.evolutionEntryCount} evolutions, ${prepared.speciesInfoCount} species info, ${prepared.obtainmentSpeciesCount} obtainment, ${prepared.ridingSpeciesCount} riding, ${prepared.fossilSpeciesCount} fossils, ${prepared.compressedSize} bytes in ${prepared.chunkedPayloads.size} chunks")
            } catch (_: UnsupportedOperationException) {
                try {
                    if (prepared.compressedSize > SpawnSyncPayload.MAX_PAYLOAD_SIZE) {
                        CobbleDexMod.LOGGER.warn("[CobbleDex] Skipping legacy sync to ${player.name.string}: payload ${prepared.compressedSize} exceeds ${SpawnSyncPayload.MAX_PAYLOAD_SIZE} byte limit")
                        return@execute
                    }
                    PacketDistributor.sendToPlayer(player, prepared.legacyPayload)
                    CobbleDexMod.LOGGER.info("[CobbleDex] Sent legacy sync to ${player.name.string}: ${prepared.speciesCount} species (${prepared.totalSpawnEntries} spawn entries), ${prepared.evolutionEntryCount} evolutions, ${prepared.speciesInfoCount} species info, ${prepared.obtainmentSpeciesCount} obtainment, ${prepared.ridingSpeciesCount} riding, ${prepared.fossilSpeciesCount} fossils, ${prepared.compressedSize} bytes")
                } catch (_: UnsupportedOperationException) {
                    CobbleDexMod.LOGGER.info("[CobbleDex] Client ${player.name.string} doesn't support CobbleDex sync payloads, skipping sync")
                } catch (e: Exception) {
                    CobbleDexMod.LOGGER.warn("[CobbleDex] Failed to send legacy sync to ${player.name.string}: ${e.message}", e)
                }
            } catch (e: Exception) {
                CobbleDexMod.LOGGER.warn("[CobbleDex] Failed to send chunked sync to ${player.name.string}: ${e.message}", e)
            }
        }
    }
}
