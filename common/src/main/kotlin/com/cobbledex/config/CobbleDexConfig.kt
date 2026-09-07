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
    /** Herd pages (Cobblemon 1.8.0+): the roster, alpha, and conditions of each herd a Pokémon joins. */
    val showHerds: Boolean = true,
    /** TM Recipes page (native Cobblemon 1.8.0+ TMs). No effect on older Cobblemon. */
    val showTmRecipes: Boolean = true,
    /**
     * Register one browsable disc entry per native TM (Cobblemon 1.8.0+) in REI/JEI/EMI, so each TM
     * is searchable by move and opens its own recipe. On REI they collapse into one "Cobblemon TMs"
     * group like third-party TM mods. No effect on older Cobblemon.
     */
    val showTmEntries: Boolean = true,
    /**
     * Marks reference page (Cobblemon 1.8.0+). Off by default: Cobblemon ships ~168 mark/ribbon
     * entries, most of them contest/Battle-Tower ribbons it has no way to award, and the data carries
     * no earning conditions — so the page is mostly noise. Opt in via config if you want it.
     */
    val showMarks: Boolean = false,
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
