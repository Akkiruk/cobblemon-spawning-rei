package com.cobbledex.neoforge

import com.cobbledex.CobbleDexMod
import com.cobbledex.CobbleRegionsIntegration
import com.cobbledex.DebugLog
import com.cobbledex.EvolutionDataLoader
import com.cobbledex.FossilDataLoader
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
import net.neoforged.neoforge.event.tick.ServerTickEvent
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import java.util.UUID

@Mod(CobbleDexMod.NEOFORGE_MOD_ID)
class CobbleDexNeoForge(modBus: IEventBus) {
    private data class PendingSync(var ticksUntilAttempt: Int = INITIAL_SYNC_DELAY_TICKS, var attempts: Int = 0)

    private val pendingSyncs = mutableMapOf<UUID, PendingSync>()

    init {
        modBus.addListener(::onRegisterPayloadHandlers)
        NeoForge.EVENT_BUS.addListener(::onPlayerJoin)
        NeoForge.EVENT_BUS.addListener(::onServerTick)

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
                    SpawnDataIndex.applyServerSync(bundle.spawns, bundle.evolutions, bundle.speciesInfo, bundle.jobRules, bundle.fossils, bundle.spawnRegions)
                    DebugLog.info("Received sync from server: ${bundle.spawns.size} spawns, ${bundle.evolutions.size} evolutions, ${bundle.fossils?.size ?: 0} fossils")
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
                    SpawnDataIndex.applyServerSync(bundle.spawns, bundle.evolutions, bundle.speciesInfo, bundle.jobRules, bundle.fossils, bundle.spawnRegions)
                    DebugLog.info("Received chunked sync from server: ${bundle.spawns.size} spawns, ${bundle.evolutions.size} evolutions, ${bundle.fossils?.size ?: 0} fossils")
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
        pendingSyncs[player.uuid] = PendingSync()
    }

    private fun onServerTick(event: ServerTickEvent.Post) {
        val iterator = pendingSyncs.iterator()
        while (iterator.hasNext()) {
            val (playerId, pending) = iterator.next()
            val player = event.server.playerList.getPlayer(playerId)
            if (player == null) {
                iterator.remove()
                continue
            }
            if (--pending.ticksUntilAttempt > 0) continue

            if (sendSyncWhenReady(player, pending)) {
                iterator.remove()
            } else {
                pending.attempts++
                pending.ticksUntilAttempt = SYNC_RETRY_TICKS
            }
        }
    }

    private fun sendSyncWhenReady(player: ServerPlayer, pending: PendingSync): Boolean {
        try {
            val spawns = SpawnDataLoader.loadFromRuntime()
            val evolutions = EvolutionDataLoader.loadFromRuntime()
            if ((spawns.isEmpty() || evolutions.isEmpty()) && pending.attempts < MAX_SYNC_ATTEMPTS) {
                return false
            }

            val bundle = SyncBundle(
                spawns = spawns,
                evolutions = evolutions,
                speciesInfo = EvolutionDataLoader.loadSpeciesBasicInfoFromRuntime(),
                fossils = FossilDataLoader.loadFromRuntime(),
                spawnRegions = CobbleRegionsIntegration.regionsBySpecies(spawns.keys),
            )
            val totalSpawnEntries = bundle.spawns.values.sumOf { it.size }
            val compressed = SpawnSyncSerializer.serialize(bundle)
            val chunks = ChunkedSpawnSyncPayload.split(compressed)
            for (chunk in chunks) {
                PacketDistributor.sendToPlayer(player, chunk)
            }
            CobbleDexMod.LOGGER.info("[CobbleDex] Sent sync to ${player.name.string}: ${bundle.spawns.size} species ($totalSpawnEntries spawn entries), ${bundle.evolutions.size} evolutions, ${bundle.speciesInfo.size} species info, ${compressed.size} bytes in ${chunks.size} chunks after ${pending.attempts} retries")
        } catch (e: Exception) {
            CobbleDexMod.LOGGER.info("[CobbleDex] Client ${player.name.string} doesn't support sync or send failed: ${e.message}")
        }
        return true
    }

    private companion object {
        const val INITIAL_SYNC_DELAY_TICKS = 20
        const val SYNC_RETRY_TICKS = 20
        const val MAX_SYNC_ATTEMPTS = 10
    }
}
