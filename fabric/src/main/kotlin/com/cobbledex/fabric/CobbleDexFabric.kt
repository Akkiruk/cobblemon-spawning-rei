package com.cobbledex.fabric

import com.cobbledex.CobbleDexMod
import com.cobbledex.CobbleRegionsIntegration
import com.cobbledex.DebugLog
import com.cobbledex.EvolutionDataLoader
import com.cobbledex.FossilDataLoader
import com.cobbledex.SpawnDataLoader
import com.cobbledex.network.ChunkedSpawnSyncPayload
import com.cobbledex.network.SpawnSyncPayload
import com.cobbledex.network.SpawnSyncSerializer
import com.cobbledex.network.SyncBundle
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

class CobbleDexFabric : ModInitializer {
    private data class PendingSync(var ticksUntilAttempt: Int = INITIAL_SYNC_DELAY_TICKS, var attempts: Int = 0)

    private val pendingSyncs = mutableMapOf<UUID, PendingSync>()

    override fun onInitialize() {
        PayloadTypeRegistry.playS2C().register(SpawnSyncPayload.TYPE, SpawnSyncPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(ChunkedSpawnSyncPayload.TYPE, ChunkedSpawnSyncPayload.CODEC)

        ServerPlayConnectionEvents.JOIN.register { handler, _, server ->
            pendingSyncs[handler.player.uuid] = PendingSync()
        }

        ServerTickEvents.END_SERVER_TICK.register { server ->
            val iterator = pendingSyncs.iterator()
            while (iterator.hasNext()) {
                val (playerId, pending) = iterator.next()
                val player = server.playerList.getPlayer(playerId)
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
    }

    private fun sendSyncWhenReady(player: ServerPlayer, pending: PendingSync): Boolean {
        val supportsChunked = ServerPlayNetworking.canSend(player, ChunkedSpawnSyncPayload.TYPE)
        val supportsLegacy = ServerPlayNetworking.canSend(player, SpawnSyncPayload.TYPE)
        if (!supportsChunked && !supportsLegacy) {
            CobbleDexMod.LOGGER.info("[CobbleDex] Client ${player.name.string} doesn't have CobbleDex, skipping sync")
            return true
        }

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
            if (supportsChunked) {
                val chunks = ChunkedSpawnSyncPayload.split(compressed)
                for (chunk in chunks) {
                    ServerPlayNetworking.send(player, chunk)
                }
                CobbleDexMod.LOGGER.info("[CobbleDex] Sent sync to ${player.name.string}: ${bundle.spawns.size} species ($totalSpawnEntries spawn entries), ${bundle.evolutions.size} evolutions, ${bundle.speciesInfo.size} species info, ${compressed.size} bytes in ${chunks.size} chunks after ${pending.attempts} retries")
            } else {
                ServerPlayNetworking.send(player, SpawnSyncPayload(compressed))
                CobbleDexMod.LOGGER.info("[CobbleDex] Sent sync to ${player.name.string}: ${bundle.spawns.size} species ($totalSpawnEntries spawn entries), ${bundle.evolutions.size} evolutions, ${bundle.speciesInfo.size} species info, ${compressed.size} bytes after ${pending.attempts} retries")
            }
            return true
        } catch (e: Exception) {
            CobbleDexMod.LOGGER.warn("[CobbleDex] Failed to send sync data to ${player.name.string}: ${e.message}", e)
            return true
        }
    }

    private companion object {
        const val INITIAL_SYNC_DELAY_TICKS = 20
        const val SYNC_RETRY_TICKS = 20
        const val MAX_SYNC_ATTEMPTS = 10
    }
}
