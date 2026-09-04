package com.cobbledex

import com.cobbledex.platform.PlatformHelper

/**
 * Ensures EMI and JEI have current CobbleDex data after the index rebuilds.
 *
 * REI uses DynamicDisplayGenerator — always reads live data, no reload needed.
 *
 * EMI and JEI register recipes statically. When a rebuild lands after their initial registration,
 * their recipes are stale. This reloader:
 *  1. Tracks the dataVersion each viewer last registered with
 *  2. On each tick, checks if any viewer is stale (version mismatch)
 *  3. Reloads only stale viewers
 *  4. Verifies success — stops once all are current
 *  5. Uses exponential backoff if a reload doesn't take effect immediately
 *
 * Viewers are described by [Viewer] entries rather than parallel fields and branches, so adding a
 * third static-registration viewer is one list entry instead of an edit in eight places.
 */
object RecipeViewerReloader {

    /**
     * A recipe viewer that registers statically and therefore has to be told to reload.
     *
     * [reloadTarget] is invoked reflectively so the viewer's classes are never linked when the mod
     * isn't installed.
     */
    private class Viewer(
        val name: String,
        val modId: String,
        val reloadClass: String,
        val reloadMethod: String,
    ) {
        /** dataVersion this viewer last registered with; -1 until it registers once. */
        @Volatile
        var lastRegisteredVersion = -1L

        var isStale = false
            private set

        fun refreshStaleness(targetVersion: Long) {
            if (!PlatformHelper.isModLoaded(modId)) {
                // Not installed — treat as permanently current so it never blocks completion.
                lastRegisteredVersion = targetVersion
                isStale = false
                return
            }
            isStale = lastRegisteredVersion != targetVersion
        }

        fun status(): String = if (isStale) "stale(v$lastRegisteredVersion)" else "ok"

        fun reload(targetVersion: Long) {
            try {
                Class.forName(reloadClass).getMethod(reloadMethod).invoke(null)
                DebugLog.info("Triggered $name reload")
            } catch (_: ClassNotFoundException) {
                lastRegisteredVersion = targetVersion
            } catch (_: NoClassDefFoundError) {
                lastRegisteredVersion = targetVersion
            } catch (e: Exception) {
                DebugLog.warn("$name reload failed: ${e.message}")
            }
        }
    }

    private val emi = Viewer("EMI", "emi", "dev.emi.emi.runtime.EmiReloadManager", "reload")
    private val jei = Viewer("JEI", "jei", "com.cobbledex.jei.CobbleDexJEIPlugin", "reloadRecipes")
    private val viewers = listOf(emi, jei)

    /** Set by CobbleDexEMIPlugin.register() after it runs with data. */
    var emiLastRegisteredVersion: Long
        get() = emi.lastRegisteredVersion
        set(value) { emi.lastRegisteredVersion = value }

    /** Set by CobbleDexJEIPlugin.registerRecipes() and reloadRecipes(). */
    var jeiLastRegisteredVersion: Long
        get() = jei.lastRegisteredVersion
        set(value) { jei.lastRegisteredVersion = value }

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

        viewers.forEach { it.refreshStaleness(targetDataVersion) }
        val stale = viewers.filter { it.isStale }

        if (stale.isEmpty()) {
            active = false
            DebugLog.info("Recipe viewers verified current (dataVersion=$targetDataVersion)")
            return
        }

        attempts++
        if (attempts > MAX_ATTEMPTS) {
            active = false
            val statuses = viewers.joinToString(", ") { "${it.name}=${it.status()}" }
            CobbleDexMod.LOGGER.warn("[CobbleDex] Recipe viewer reload gave up after $MAX_ATTEMPTS attempts " +
                "(target=v$targetDataVersion, $statuses, spawns=${SpawnDataIndex.spawnsBySpecies.size})")
            return
        }

        val labels = viewers.joinToString(", ") { "${it.name}=${if (it.isStale) "stale" else "current"}" }
        DebugLog.info("Reload attempt $attempts/$MAX_ATTEMPTS — $labels " +
            "(target=v$targetDataVersion, spawns=${SpawnDataIndex.spawnsBySpecies.size})")

        stale.forEach { it.reload(targetDataVersion) }

        // Exponential backoff: 20, 40, 80, 160, 320 ticks (1s, 2s, 4s, 8s, 16s)
        ticksUntilCheck = 20 * (1 shl (attempts - 1))
    }

    fun reset() {
        active = false
        attempts = 0
        ticksUntilCheck = 0
        targetDataVersion = -1L
        viewers.forEach { it.lastRegisteredVersion = -1L }
    }
}
