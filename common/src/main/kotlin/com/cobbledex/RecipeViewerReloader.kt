package com.cobbledex

/**
 * Triggers EMI/JEI to re-register recipes after server sync data arrives.
 *
 * REI uses DynamicDisplayGenerator so it reads fresh data on every lookup.
 * EMI and JEI register all recipes statically during plugin init, which
 * runs before the server sync packet arrives. After sync, those static
 * recipes are stale (typically empty). This utility schedules a recipe
 * viewer reload on the next client tick to pick up the synced data.
 */
object RecipeViewerReloader {

    @Volatile
    private var pendingReload = false

    fun scheduleReload() {
        pendingReload = true
    }

    fun tick() {
        if (!pendingReload) return
        pendingReload = false

        reloadEMI()
        reloadJEI()
    }

    private fun reloadEMI() {
        try {
            val cls = Class.forName("dev.emi.emi.runtime.EmiReloadManager")
            val method = cls.getMethod("reload")
            method.invoke(null)
            DebugLog.info("Triggered EMI recipe reload after server sync")
        } catch (_: ClassNotFoundException) {
        } catch (e: Exception) {
            DebugLog.once("emi-reload") { "EMI reload failed: ${e.message}" }
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
            DebugLog.once("jei-reload") { "JEI reload failed: ${e.message}" }
        }
    }
}
