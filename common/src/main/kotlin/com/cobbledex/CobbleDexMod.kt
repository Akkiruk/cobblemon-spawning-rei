package com.cobbledex

import com.cobbledex.config.CobbleDexConfig
import org.slf4j.LoggerFactory

object CobbleDexMod {
    const val MOD_ID = "cobbledex-rei-emi-jei"
    const val NEOFORGE_MOD_ID = "cobbledex_rei_emi_jei"
    val LOGGER = LoggerFactory.getLogger(MOD_ID)

    private var reloadTickCounter = 0

    val dataLoaded: Boolean
        get() = SpawnDataIndex.loadState != SpawnDataIndex.LoadState.NOT_LOADED

    fun init() {
        DebugLog.info("Initializing")
        try {
            CobbleDexConfig.load()
        } catch (e: Exception) {
            DebugLog.warn("Config load deferred: ${e.message}")
        }
    }

    fun tickReloadCheck() {
        RecipeViewerReloader.tick()

        if (SpawnDataIndex.isFullyLoaded()) return

        reloadTickCounter++
        if (reloadTickCounter <= 2 || reloadTickCounter % 100 == 0) {
            SpawnDataIndex.ensureLoadedAsync()
        }
    }

    fun resetReloadTimer() {
        reloadTickCounter = 0
        SpawnDataIndex.ensureLoadedAsync()
    }
}
