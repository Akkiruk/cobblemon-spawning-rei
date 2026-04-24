package com.cobbledex.fabric

import com.cobbledex.CobbleDexMod
import com.cobbledex.network.ChunkedSpawnSyncPayload
import com.cobbledex.network.ServerSyncPayloadFactory
import com.cobbledex.network.SpawnSyncPayload
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking

class CobbleDexFabric : ModInitializer {
    override fun onInitialize() {
        PayloadTypeRegistry.playS2C().register(SpawnSyncPayload.TYPE, SpawnSyncPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(ChunkedSpawnSyncPayload.TYPE, ChunkedSpawnSyncPayload.CODEC)

        ServerPlayConnectionEvents.JOIN.register { handler, _, server ->
            server.execute {
                val player = handler.player
                val supportsChunked = ServerPlayNetworking.canSend(player, ChunkedSpawnSyncPayload.TYPE)
                val supportsLegacy = ServerPlayNetworking.canSend(player, SpawnSyncPayload.TYPE)
                if (!supportsChunked && !supportsLegacy) {
                    CobbleDexMod.LOGGER.info("[CobbleDex] Client ${player.name.string} doesn't have CobbleDex, skipping sync")
                    return@execute
                }
                val prepared = try {
                    ServerSyncPayloadFactory.getOrBuild()
                } catch (e: Exception) {
                    CobbleDexMod.LOGGER.warn("[CobbleDex] Failed to build sync data for ${player.name.string}: ${e.message}", e)
                    return@execute
                }

                try {
                    if (supportsChunked) {
                        for (chunk in prepared.chunkedPayloads) {
                            ServerPlayNetworking.send(player, chunk)
                        }
                        CobbleDexMod.LOGGER.info("[CobbleDex] Sent chunked sync to ${player.name.string}: ${prepared.speciesCount} species (${prepared.totalSpawnEntries} spawn entries), ${prepared.evolutionEntryCount} evolutions, ${prepared.speciesInfoCount} species info, ${prepared.fossilSpeciesCount} fossils, ${prepared.compressedSize} bytes in ${prepared.chunkedPayloads.size} chunks")
                    } else {
                        ServerPlayNetworking.send(player, prepared.legacyPayload)
                        CobbleDexMod.LOGGER.info("[CobbleDex] Sent legacy sync to ${player.name.string}: ${prepared.speciesCount} species (${prepared.totalSpawnEntries} spawn entries), ${prepared.evolutionEntryCount} evolutions, ${prepared.speciesInfoCount} species info, ${prepared.fossilSpeciesCount} fossils, ${prepared.compressedSize} bytes")
                    }
                } catch (e: Exception) {
                    CobbleDexMod.LOGGER.warn("[CobbleDex] Failed to send sync payloads to ${player.name.string}: ${e.message}", e)
                }
            }
        }
    }
}
