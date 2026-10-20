package com.cobbledex.rei

import com.cobbledex.DebugLog
import me.shedaniel.rei.api.common.plugins.PluginManager

/**
 * Asks REI to run its plugin reload again, which re-runs [CobbleDexREIPlugin.registerEntries].
 *
 * REI registers its entry list once per reload. A reload that lands before CobbleDex's index is built
 * (common on a server, where REI reloads a few seconds after joining) registers no Pokémon at all and
 * nothing else ever retries. Only called reflectively by [com.cobbledex.RecipeViewerReloader], so REI's
 * classes are never linked when REI is not installed.
 *
 * REI does its own reloads on a worker thread; this does the same rather than stall the render thread.
 * A reload request while REI is already reloading would be lost, so it is declined (returns false) and
 * the caller tries again later.
 */
object ReiReload {
    /** True if a reload was started, false if REI is busy right now. */
    @JvmStatic
    fun reload(): Boolean {
        if (PluginManager.areAnyReloading()) return false
        Thread({
            try {
                PluginManager.getClientInstance().startReload()
                DebugLog.info("Triggered REI reload")
            } catch (e: Throwable) {
                DebugLog.warn("REI reload failed: ${e.message}")
            }
        }, "CobbleDex-REIReload").apply { isDaemon = true }.start()
        return true
    }
}
