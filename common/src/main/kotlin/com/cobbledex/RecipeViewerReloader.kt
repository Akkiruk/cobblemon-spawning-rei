package com.cobbledex

import com.cobbledex.platform.PlatformHelper

/**
 * Ensures REI, EMI, and JEI have current CobbleDex data after server sync.
 *
 * REI recipes are dynamic, but sidebar entries still need re-indexing after sync.
 *
 * EMI and JEI register recipes statically. When server sync arrives after their
 * initial registration, recipes are stale. This reloader:
 *  1. Tracks the dataVersion each viewer last registered with
 *  2. On each tick, checks if any viewer is stale (version mismatch)
 *  3. Reloads only stale viewers
 *  4. Verifies success — stops once all viewers are current
 *  5. Uses exponential backoff if a reload doesn't take effect immediately
 */
object RecipeViewerReloader {

    private const val REI_MOD_ID = "roughlyenoughitems"
    private const val EMI_MOD_ID = "emi"
    private const val JEI_MOD_ID = "jei"

    /** Set by CobbleDexREIPlugin.registerEntries() and reloadEntries() */
    @Volatile
    var reiEntriesLastRegisteredVersion = -1L

    /** Set by CobbleDexEMIPlugin.register() after it runs with data */
    @Volatile
    var emiLastRegisteredVersion = -1L

    /** Set by CobbleDexJEIPlugin.registerRecipes() and reloadRecipes() */
    @Volatile
    var jeiLastRegisteredVersion = -1L

    private const val MAX_ATTEMPTS = 6

    @Volatile private var active = false
    @Volatile private var targetDataVersion = -1L
    @Volatile private var attempts = 0
    @Volatile private var ticksUntilCheck = 0

    fun scheduleReload() {
        val version = SpawnDataIndex.dataVersion
        if (active && targetDataVersion == version) return
        targetDataVersion = version
        active = true
        attempts = 0
        ticksUntilCheck = 0
        DebugLog.info("Scheduled recipe viewer verification (dataVersion=$version)")
    }

    fun tick() {
        if (!active) return

        // If data changed since we started, restart the sequence
        val currentVersion = SpawnDataIndex.dataVersion
        if (currentVersion != targetDataVersion) {
            targetDataVersion = currentVersion
            attempts = 0
            ticksUntilCheck = 0
        }

        if (ticksUntilCheck > 0) {
            ticksUntilCheck--
            return
        }

        val reiInstalled = PlatformHelper.isModLoaded(REI_MOD_ID)
        val emiInstalled = PlatformHelper.isModLoaded(EMI_MOD_ID)
        val jeiInstalled = PlatformHelper.isModLoaded(JEI_MOD_ID)

        if (!reiInstalled) reiEntriesLastRegisteredVersion = targetDataVersion
        if (!emiInstalled) emiLastRegisteredVersion = targetDataVersion
        if (!jeiInstalled) jeiLastRegisteredVersion = targetDataVersion

        val reiStale = reiInstalled && reiEntriesLastRegisteredVersion != targetDataVersion
        val emiStale = emiInstalled && emiLastRegisteredVersion != targetDataVersion
        val jeiStale = jeiInstalled && jeiLastRegisteredVersion != targetDataVersion

        if (!reiStale && !emiStale && !jeiStale) {
            active = false
            DebugLog.info("Recipe viewers verified current (dataVersion=$targetDataVersion)")
            return
        }

        attempts++
        if (attempts > MAX_ATTEMPTS) {
            active = false
            val reiStatus = if (reiStale) "stale(v${reiEntriesLastRegisteredVersion})" else "ok"
            val emiStatus = if (emiStale) "stale(v${emiLastRegisteredVersion})" else "ok"
            val jeiStatus = if (jeiStale) "stale(v${jeiLastRegisteredVersion})" else "ok"
            CobbleDexMod.LOGGER.warn("[CobbleDex] Recipe viewer reload gave up after $MAX_ATTEMPTS attempts " +
                "(target=v$targetDataVersion, REI=$reiStatus, EMI=$emiStatus, JEI=$jeiStatus, spawns=${SpawnDataIndex.spawnsBySpecies.size})")
            return
        }

        val reiLabel = if (reiStale) "stale" else "current"
        val emiLabel = if (emiStale) "stale" else "current"
        val jeiLabel = if (jeiStale) "stale" else "current"
        DebugLog.info("Reload attempt $attempts/$MAX_ATTEMPTS — REI=$reiLabel, EMI=$emiLabel, JEI=$jeiLabel " +
            "(target=v$targetDataVersion, spawns=${SpawnDataIndex.spawnsBySpecies.size})")

        if (reiStale) reloadREIEntries()
        if (emiStale) reloadEMI()
        if (jeiStale) reloadJEI()

        // Exponential backoff: 20, 40, 80, 160, 320 ticks (1s, 2s, 4s, 8s, 16s)
        ticksUntilCheck = 20 * (1 shl (attempts - 1))
    }

    fun reset() {
        active = false
        attempts = 0
        ticksUntilCheck = 0
        targetDataVersion = -1L
        reiEntriesLastRegisteredVersion = -1L
        emiLastRegisteredVersion = -1L
        jeiLastRegisteredVersion = -1L
    }

    private fun reloadREIEntries() {
        if (!PlatformHelper.isModLoaded(REI_MOD_ID)) {
            reiEntriesLastRegisteredVersion = targetDataVersion
            return
        }

        try {
            val cls = Class.forName("com.cobbledex.rei.CobbleDexREIPlugin")
            cls.getMethod("reloadEntries").invoke(null)
        } catch (_: ClassNotFoundException) {
            reiEntriesLastRegisteredVersion = targetDataVersion
        } catch (_: NoClassDefFoundError) {
            reiEntriesLastRegisteredVersion = targetDataVersion
        } catch (e: Exception) {
            DebugLog.warn("REI entry reload failed: ${e.message}")
        }
    }

    private fun reloadEMI() {
        if (!PlatformHelper.isModLoaded(EMI_MOD_ID)) {
            emiLastRegisteredVersion = targetDataVersion
            return
        }

        try {
            val cls = Class.forName("dev.emi.emi.runtime.EmiReloadManager")
            cls.getMethod("reload").invoke(null)
            DebugLog.info("Triggered EMI reload")
        } catch (_: ClassNotFoundException) {
            // EMI not installed — mark as always current
            emiLastRegisteredVersion = targetDataVersion
        } catch (e: Exception) {
            DebugLog.warn("EMI reload failed: ${e.message}")
        }
    }

    private fun reloadJEI() {
        if (!PlatformHelper.isModLoaded(JEI_MOD_ID)) {
            jeiLastRegisteredVersion = targetDataVersion
            return
        }

        try {
            val cls = Class.forName("com.cobbledex.jei.CobbleDexJEIPlugin")
            cls.getMethod("reloadRecipes").invoke(null)
        } catch (_: ClassNotFoundException) {
            jeiLastRegisteredVersion = targetDataVersion
        } catch (_: NoClassDefFoundError) {
            // JEI not installed
            jeiLastRegisteredVersion = targetDataVersion
        } catch (e: Exception) {
            DebugLog.warn("JEI reload failed: ${e.message}")
        }
    }
}
