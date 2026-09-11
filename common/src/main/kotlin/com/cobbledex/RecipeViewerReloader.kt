package com.cobbledex

import com.cobbledex.platform.PlatformHelper

/**
 * Ensures EMI and JEI have current CobbleDex data after the index rebuilds.
 *
 * REI uses DynamicDisplayGenerator - always reads live data, no reload needed.
 *
 * EMI and JEI register recipes statically. When a rebuild lands after their initial registration,
 * their recipes are stale. This reloader:
 *  1. Tracks the dataVersion each viewer last registered with
 *  2. On each tick, checks if any viewer is stale (version mismatch)
 *  3. Reloads stale viewers - JEI incrementally (see below), EMI via its own reload manager
 *  4. Verifies success - stops once all are current
 *
 * JEI and EMI are driven differently because their reload shapes differ, not out of choice:
 *  - **JEI**: `CobbleDexJEIPlugin.continueReload(Long)` does a bounded slice of work per call and
 *    returns whether it's finished, so it's called every tick with no backoff - a large modpack's
 *    reload is spread over however many ticks it needs instead of blocking one frame for seconds
 *    (issue #43). It is invoked reflectively so JEI's classes are never linked when JEI isn't
 *    installed.
 *  - **EMI**: reload is triggered through EMI's own `EmiReloadManager.reload()`, a fire-and-forget
 *    call into EMI's internals with no progress signal - we can only ask again later whether it
 *    took effect, hence the [Viewer] abstraction and exponential backoff retained for it.
 *
 *    This isn't a gap: `EmiReloadManager.reload()` starts its own background `Thread` and returns
 *    immediately (confirmed from EMI 1.1.12's source) - every plugin's `register()`, including
 *    ours, runs off the render thread there. EMI just shows its own "Reloading" line and hides its
 *    list widget until that thread finishes; nothing about it blocks the game the way JEI's
 *    synchronous `IRecipeManager` calls did. So issue #43's freeze was JEI-specific by construction,
 *    not something EMI happened to dodge - no incremental path is needed for it.
 */
object RecipeViewerReloader {

    /**
     * A recipe viewer that registers statically, reloads atomically (one opaque call, no progress
     * signal), and therefore has to be retried with backoff until its registered version catches up.
     * Currently only EMI fits this shape - JEI is driven incrementally instead (see [stepJei]).
     *
     * [reloadClass]/[reloadMethod] are invoked reflectively so the viewer's classes are never linked
     * when the mod isn't installed.
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
                // Not installed - treat as permanently current so it never blocks completion.
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

    private const val JEI_MOD_ID = "jei"
    private const val JEI_STEP_CLASS = "com.cobbledex.jei.CobbleDexJEIPlugin"
    private const val JEI_STEP_METHOD = "continueReload"

    private val emi = Viewer("EMI", "emi", "dev.emi.emi.runtime.EmiReloadManager", "reload")

    /** Set by CobbleDexEMIPlugin.register() after it runs with data. */
    var emiLastRegisteredVersion: Long
        get() = emi.lastRegisteredVersion
        set(value) { emi.lastRegisteredVersion = value }

    /** Set by CobbleDexJEIPlugin's initial registration and by its incremental reload on completion. */
    @Volatile var jeiLastRegisteredVersion: Long = -1L

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

        // JEI reports its own progress, so it's driven every tick regardless of EMI's backoff timer.
        val jeiDone = stepJei(targetDataVersion)

        if (ticksUntilCheck > 0) {
            ticksUntilCheck--
        } else {
            emi.refreshStaleness(targetDataVersion)
            if (emi.isStale) {
                attempts++
                if (attempts > MAX_ATTEMPTS) {
                    active = false
                    CobbleDexMod.LOGGER.warn(
                        "[CobbleDex] Recipe viewer reload gave up after $MAX_ATTEMPTS attempts " +
                            "(target=v$targetDataVersion, EMI=${emi.status()}, " +
                            "JEI=${if (jeiDone) "ok" else "in progress"}, spawns=${SpawnDataIndex.spawnsBySpecies.size})"
                    )
                    return
                }
                DebugLog.info(
                    "Reload attempt $attempts/$MAX_ATTEMPTS - EMI=stale, " +
                        "JEI=${if (jeiDone) "current" else "in progress"} " +
                        "(target=v$targetDataVersion, spawns=${SpawnDataIndex.spawnsBySpecies.size})"
                )
                emi.reload(targetDataVersion)
                // Exponential backoff: 20, 40, 80, 160, 320 ticks (1s, 2s, 4s, 8s, 16s)
                ticksUntilCheck = 20 * (1 shl (attempts - 1))
            }
        }

        if (jeiDone && !emi.isStale) {
            active = false
            DebugLog.info("Recipe viewers verified current (dataVersion=$targetDataVersion)")
        }
    }

    /**
     * Advances JEI's incremental reload by one tick if it's stale, reflectively so JEI's classes
     * are never linked when JEI isn't installed. Returns true once JEI is current (including
     * "JEI not installed", which is trivially current).
     */
    private fun stepJei(targetVersion: Long): Boolean {
        if (!PlatformHelper.isModLoaded(JEI_MOD_ID)) {
            jeiLastRegisteredVersion = targetVersion
            return true
        }
        if (jeiLastRegisteredVersion == targetVersion) return true

        return try {
            val result = Class.forName(JEI_STEP_CLASS)
                .getMethod(JEI_STEP_METHOD, java.lang.Long.TYPE)
                .invoke(null, targetVersion)
            (result as? Boolean) ?: true
        } catch (_: ClassNotFoundException) {
            jeiLastRegisteredVersion = targetVersion
            true
        } catch (_: NoClassDefFoundError) {
            jeiLastRegisteredVersion = targetVersion
            true
        } catch (e: Exception) {
            DebugLog.warn("JEI reload failed: ${e.message}")
            jeiLastRegisteredVersion = targetVersion // don't spin forever on a hard error
            true
        }
    }

    fun reset() {
        active = false
        attempts = 0
        ticksUntilCheck = 0
        targetDataVersion = -1L
        emi.lastRegisteredVersion = -1L
        jeiLastRegisteredVersion = -1L
    }
}
