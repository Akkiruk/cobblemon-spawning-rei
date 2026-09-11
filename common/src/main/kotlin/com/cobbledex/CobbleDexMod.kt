package com.cobbledex

import com.cobbledex.config.CobbleDexConfig
import org.slf4j.LoggerFactory

object CobbleDexMod {
    const val MOD_ID = "cobbledex-rei-emi-jei"
    const val NEOFORGE_MOD_ID = "cobbledex_rei_emi_jei"
    val LOGGER = LoggerFactory.getLogger(MOD_ID)

    /**
     * Ticks between fingerprint samples. Cobblemon's species sync lands within the first second or
     * two of login, so a one-second cadence picks it up promptly without the old "retry forever"
     * loop. Unlike that loop this keeps sampling after the first successful load, so a later
     * Cobblemon reload is picked up too.
     */
    private const val SAMPLE_INTERVAL_TICKS = 20

    private var tickCounter = 0

    val dataLoaded: Boolean
        get() = SpawnDataIndex.loadState != SpawnDataIndex.LoadState.NOT_LOADED

    fun init() {
        DebugLog.info("Initializing")
        try {
            CobbleDexConfig.load()
        } catch (e: Exception) {
            DebugLog.warn("Config load deferred: ${e.message}")
        }

        // Pre-cache the local-file layer (mod JARs + this client's datapacks) in the background.
        // It supplies only the fields Cobblemon does not sync - see LocalFileSource notes in
        // SpawnDataIndex.doLoad.
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

    /**
     * Client tick. Rebuilds only when Cobblemon's data actually changed, so a steady state costs
     * one cheap fingerprint per second and nothing else.
     */
    fun tickClient() {
        RecipeViewerReloader.tick()

        if (++tickCounter < SAMPLE_INTERVAL_TICKS) return
        tickCounter = 0

        if (CobblemonDataSignal.consumeChange()) {
            SpawnDataIndex.rebuildAsync()
            return
        }

        if (SpawnDataIndex.isFullyLoaded()) {
            // Fires once per session, right as data finishes loading - builds the icon/render
            // atlas automatically if no valid cache exists yet, so players never have to know
            // /cobbledex sprites build exists (see PokemonSpriteAtlas.ensureAtlas).
            PokemonSpriteAtlas.ensureAtlas()
            // Spread the per-category panel-measurement cost over idle ticks so the first open of
            // each REI/JEI/EMI category isn't a visible hitch.
            CategorySizer.warmOneCategory()
        }
    }

    /**
     * Joined a world/server - Cobblemon's own data sync is inbound, so sample promptly (reset the
     * tick counter) rather than waiting out whatever's left of the last interval.
     *
     * Does *not* reset [CobblemonDataSignal] - that used to force [consumeChange] to report a
     * change on the very next sample no matter what, which meant every world join triggered a full
     * rebuild + a synchronous JEI/EMI recipe reload even when nothing had actually changed. In
     * singleplayer, Cobblemon's species/spawn registries are already populated by the time this
     * fires - the world's own datapack reload has already happened - so that reload was pure
     * repeat work, and for large modpacks (~1000+ species) it's a multi-second render-thread freeze
     * for nothing (issue #43). The fingerprint compare already catches a *real* change - a
     * genuinely different server/world reliably produces a different fingerprint - so forcing one
     * isn't needed for correctness, only for a probability-zero coincidence.
     */
    fun onJoinedWorld() {
        tickCounter = 0
        PokemonSpriteAtlas.resetEnsureAttempt()
    }

    /** Left the world. See [onJoinedWorld] for why this no longer forces a resample. */
    fun onLeftWorld() {
        tickCounter = 0
        PokemonSpriteAtlas.resetEnsureAttempt()
    }
}
