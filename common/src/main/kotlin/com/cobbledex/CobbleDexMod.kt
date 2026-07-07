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

        // Pre-cache spawn + evolution data from mod JARs in background
        Thread({
            try {
                val modRoots = SpawnDataLoader.getModRootPaths()
                if (modRoots.isNotEmpty()) {
                    JarDataCache.initialize(modRoots)
                } else {
                    DebugLog.warn("No mod roots found for JarDataCache initialization")
                }
            } catch (e: Exception) {
                DebugLog.warn("JarDataCache background init failed: ${e.message}")
            }
        }, "CobbleDex-CacheInit").apply { isDaemon = true }.start()
    }

    fun tickReloadCheck() {
        RecipeViewerReloader.tick()

        if (SpawnDataIndex.isFullyLoaded()) {
            // Fires exactly once per session, right as data finishes
            // loading - builds the icon/render atlas automatically if no
            // valid cache exists yet, so players never have to know
            // /cobbledex sprites build exists (see PokemonSpriteAtlas.ensureAtlas).
            PokemonSpriteAtlas.ensureAtlas()
            return
        }

        reloadTickCounter++
        if (reloadTickCounter <= 2 || reloadTickCounter % 100 == 0) {
            SpawnDataIndex.ensureLoadedAsync()
        }
    }

    fun resetReloadTimer() {
        reloadTickCounter = 0
        PokemonSpriteAtlas.resetEnsureAttempt()
        SpawnDataIndex.ensureLoadedAsync()
    }
}
