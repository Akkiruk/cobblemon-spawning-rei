package com.cobbledex.config

import com.cobbledex.DebugLog
import com.cobbledex.platform.PlatformHelper
import com.google.gson.GsonBuilder
import java.nio.file.Files

data class CobbleDexConfig(
    val showSpawnWeights: Boolean = true,
    val showEvolutions: Boolean = true,
    val showObtainment: Boolean = true,
    val showDrops: Boolean = true,
    val showStats: Boolean = true,
    val showMoves: Boolean = true,
    val showPokedexInfo: Boolean = true,
    val showPokemonDescription: Boolean = true,
    val showFossils: Boolean = true,
    val showTypeChart: Boolean = true,
    val showNatures: Boolean = true,
    val showJobs: Boolean = true,
    val showAlternateForms: Boolean = true,
    val showRiding: Boolean = true,
    /** TM Recipes page (native Cobblemon 1.8.0+ TMs). No effect on older Cobblemon. */
    val showTmRecipes: Boolean = true,
    /** Marks reference page (Cobblemon 1.8.0+). No effect on older Cobblemon. */
    val showMarks: Boolean = true,
    /** Render the Moves page as separate Level-up/Egg/Tutor/TM sections instead of one unified list. */
    val groupMovesByMethod: Boolean = false,
    val registerFormEntries: Boolean = true,
    val debugMode: Boolean = false
) {
    companion object {
        private val GSON = GsonBuilder().setPrettyPrinting().create()

        @Volatile
        private var instance: CobbleDexConfig = CobbleDexConfig()

        fun get(): CobbleDexConfig = instance

        fun load() {
            val file = PlatformHelper.getConfigDir().resolve("cobbledex-rei-emi-jei.json")
            var needsSave = !Files.exists(file)
            
            try {
                if (Files.exists(file)) {
                    val jsonText = Files.readString(file)
                    val loaded = GSON.fromJson(jsonText, CobbleDexConfig::class.java)
                    if (loaded != null) instance = loaded
                }
            } catch (e: Exception) {
                DebugLog.warn("Config load failed, using defaults: ${e.message}")
                instance = CobbleDexConfig()
                needsSave = true
            }
            if (needsSave) save()
        }

        fun save() {
            try {
                val file = PlatformHelper.getConfigDir().resolve("cobbledex-rei-emi-jei.json")
                Files.createDirectories(file.parent)
                Files.writeString(file, GSON.toJson(instance))
            } catch (e: Exception) {
                DebugLog.warn("Config save failed: ${e.message}")
            }
        }
    }
}
