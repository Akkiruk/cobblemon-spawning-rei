package com.cobblemonrei

import org.slf4j.LoggerFactory

object CobblemonSpawningMod {
    const val MOD_ID = "cobblemon-spawning-rei"
    const val NEOFORGE_MOD_ID = "cobblemon_spawning_rei"
    const val VERSION = "1.0.0"
    val LOGGER = LoggerFactory.getLogger(MOD_ID)

    var dataLoaded = false
        private set

    fun init() {
        LOGGER.info("[CobblemonSpawningREI] Initializing v$VERSION")
    }

    fun onClientReady() {
        LOGGER.info("[CobblemonSpawningREI] Loading Cobblemon data from JAR...")
        try {
            SpawnDataIndex.loadAll()
            dataLoaded = true
            LOGGER.info("[CobblemonSpawningREI] Loaded ${SpawnDataIndex.spawnsBySpecies.size} species spawn entries, ${SpawnDataIndex.evolutionsBySpecies.size} evolution entries")
        } catch (e: Exception) {
            LOGGER.error("[CobblemonSpawningREI] Failed to load data: ${e.message}", e)
        }
    }
}
