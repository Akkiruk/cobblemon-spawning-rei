package com.cobblemonrei

import org.slf4j.LoggerFactory

object CobblemonSpawningMod {
    const val MOD_ID = "cobblemon-spawning-rei"
    const val NEOFORGE_MOD_ID = "cobblemon_spawning_rei"
    val LOGGER = LoggerFactory.getLogger(MOD_ID)

    private var reloadTickCounter = 0

    val dataLoaded: Boolean
        get() = SpawnDataIndex.loadState != SpawnDataIndex.LoadState.NOT_LOADED

    fun init() {
        DebugLog.info("Initializing")
    }

    fun tickReloadCheck() {
        if (SpawnDataIndex.isFullyLoaded()) return
        reloadTickCounter++
        if (reloadTickCounter % 100 != 0) return
        SpawnDataIndex.ensureLoaded()
    }
}
