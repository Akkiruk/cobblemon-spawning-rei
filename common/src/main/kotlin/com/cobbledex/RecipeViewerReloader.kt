package com.cobbledex

/**
 * Ensures EMI and JEI have current CobbleDex data after server sync.
 *
 * REI uses DynamicDisplayGenerator — always reads live data, no reload needed.
 *
 * EMI and JEI register recipes statically. When server sync arrives after their
 * initial registration, recipes are stale. This reloader:
 *  1. Tracks the dataVersion each viewer last registered with
 *  2. On each tick, checks if any viewer is stale (version mismatch)
 *  3. Reloads only stale viewers
 *  4. Verifies success — stops once both are current
 *  5. Uses exponential backoff if a reload doesn't take effect immediately
 */
object RecipeViewerReloader {

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

        val emiStale = emiLastRegisteredVersion != targetDataVersion
        val jeiStale = jeiLastRegisteredVersion != targetDataVersion

        if (!emiStale && !jeiStale) {
            active = false
            DebugLog.info("Recipe viewers verified current (dataVersion=$targetDataVersion)")
            return
        }

        attempts++
        if (attempts > MAX_ATTEMPTS) {
            active = false
            val emiStatus = if (emiStale) "stale(v${emiLastRegisteredVersion})" else "ok"
            val jeiStatus = if (jeiStale) "stale(v${jeiLastRegisteredVersion})" else "ok"
            CobbleDexMod.LOGGER.warn("[CobbleDex] Recipe viewer reload gave up after $MAX_ATTEMPTS attempts " +
                "(target=v$targetDataVersion, EMI=$emiStatus, JEI=$jeiStatus, spawns=${SpawnDataIndex.spawnsBySpecies.size})")
            return
        }

        val emiLabel = if (emiStale) "stale" else "current"
        val jeiLabel = if (jeiStale) "stale" else "current"
        DebugLog.info("Reload attempt $attempts/$MAX_ATTEMPTS — EMI=$emiLabel, JEI=$jeiLabel " +
            "(target=v$targetDataVersion, spawns=${SpawnDataIndex.spawnsBySpecies.size})")

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
        emiLastRegisteredVersion = -1L
        jeiLastRegisteredVersion = -1L
    }

    private fun reloadEMI() {
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
