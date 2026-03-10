package com.cobbledex

/**
 * Triggers EMI/JEI to re-register recipes after server sync data arrives.
 *
 * REI uses DynamicDisplayGenerator so it reads fresh data on every lookup.
 * EMI and JEI register all recipes statically during plugin init, which
 * runs before the server sync packet arrives. After sync, those static
 * recipes are stale (typically empty). This utility schedules multiple
 * recipe viewer reload attempts with staggered delays to handle the race
 * between server sync packets and EMI/JEI's own initialization reload.
 */
object RecipeViewerReloader {

    /** Tick delays for each retry attempt after server sync */
    private val RELOAD_DELAYS = intArrayOf(0, 20, 60, 200)

    @Volatile
    private var pendingRetryIndex = -1

    @Volatile
    private var retryTicksRemaining = 0

    /** The data version when we started the reload sequence; used to detect stale retries */
    @Volatile
    private var reloadDataVersion = -1L

    fun scheduleReload() {
        val currentVersion = SpawnDataIndex.dataVersion
        if (reloadDataVersion == currentVersion && pendingRetryIndex >= 0) {
            // Already reloading for this data version
            return
        }
        reloadDataVersion = currentVersion
        pendingRetryIndex = 0
        retryTicksRemaining = RELOAD_DELAYS[0]
        DebugLog.info("Scheduled recipe viewer reload (dataVersion=$currentVersion, ${RELOAD_DELAYS.size} attempts)")
    }

    fun tick() {
        if (pendingRetryIndex < 0) return

        // Data changed since we started? Restart the sequence
        if (SpawnDataIndex.dataVersion != reloadDataVersion) {
            reloadDataVersion = SpawnDataIndex.dataVersion
            pendingRetryIndex = 0
            retryTicksRemaining = RELOAD_DELAYS[0]
        }

        if (retryTicksRemaining > 0) {
            retryTicksRemaining--
            return
        }

        val attempt = pendingRetryIndex + 1
        DebugLog.info("Recipe viewer reload attempt $attempt/${RELOAD_DELAYS.size} " +
            "(spawns=${SpawnDataIndex.spawnsBySpecies.size}, species=${SpawnDataIndex.allSpeciesNames.size})")

        reloadEMI()
        reloadJEI()

        pendingRetryIndex++
        if (pendingRetryIndex < RELOAD_DELAYS.size) {
            retryTicksRemaining = RELOAD_DELAYS[pendingRetryIndex]
        } else {
            pendingRetryIndex = -1
        }
    }

    fun reset() {
        pendingRetryIndex = -1
        retryTicksRemaining = 0
        reloadDataVersion = -1L
    }

    private fun reloadEMI() {
        try {
            val cls = Class.forName("dev.emi.emi.runtime.EmiReloadManager")
            val method = cls.getMethod("reload")
            method.invoke(null)
            DebugLog.info("Triggered EMI recipe reload")
        } catch (_: ClassNotFoundException) {
        } catch (e: Exception) {
            DebugLog.warn("EMI reload failed: ${e.message}")
        }
    }

    private fun reloadJEI() {
        try {
            val cls = Class.forName("com.cobbledex.jei.CobbleDexJEIPlugin")
            cls.getMethod("reloadRecipes").invoke(null)
        } catch (_: ClassNotFoundException) {
        } catch (_: NoClassDefFoundError) {
            // JEI not installed — loading our plugin class fails because its superclass (IModPlugin) is absent
        } catch (e: Exception) {
            DebugLog.warn("JEI reload failed: ${e.message}")
        }
    }
}
