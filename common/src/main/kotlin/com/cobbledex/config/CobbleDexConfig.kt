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
    val showPokedexInfo: Boolean = true,
    val localDatapackScan: Boolean = true,
    val debugMode: Boolean = false
) {
    companion object {
        private val GSON = GsonBuilder().setPrettyPrinting().create()
        private const val CONFIG_VERSION = 2  // Increment when defaults change

        @Volatile
        private var instance: CobbleDexConfig = CobbleDexConfig()

        fun get(): CobbleDexConfig = instance

        fun load() {
            val configDir = PlatformHelper.getConfigDir()
            val file = configDir.resolve("cobbledex-rei-emi-jei.json")
            val oldFile = configDir.resolve("cobblemon-spawning-rei.json")
            
            // Migrate from old config filename if exists
            if (Files.exists(oldFile) && !Files.exists(file)) {
                try {
                    Files.move(oldFile, file)
                    DebugLog.info("Migrated config from cobblemon-spawning-rei.json to cobbledex-rei-emi-jei.json")
                } catch (e: Exception) {
                    DebugLog.warn("Config migration failed, will use old file: ${e.message}")
                }
            }
            
            // Use whichever file exists (prefer new name)
            val configFile = if (Files.exists(file)) file else if (Files.exists(oldFile)) oldFile else file
            var needsSave = !Files.exists(configFile)
            
            try {
                if (Files.exists(configFile)) {
                    val jsonText = Files.readString(configFile)
                    val loaded = GSON.fromJson(jsonText, CobbleDexConfig::class.java)
                    if (loaded != null) {
                        // Force enable localDatapackScan if old config had it disabled
                        instance = if (!loaded.localDatapackScan) {
                            DebugLog.info("Migrating config: enabling localDatapackScan for ZIP datapack support")
                            needsSave = true
                            loaded.copy(localDatapackScan = true)
                        } else {
                            loaded
                        }
                    }
                    // If we loaded from old file, ensure we save to new location
                    if (configFile == oldFile) needsSave = true
                }
            } catch (e: Exception) {
                DebugLog.warn("Config load failed, using defaults: ${e.message}")
                instance = CobbleDexConfig()
                needsSave = true
            }
            if (needsSave) save()
            
            // Clean up old config file after successful migration
            if (Files.exists(oldFile) && Files.exists(file)) {
                try {
                    Files.delete(oldFile)
                    DebugLog.info("Deleted old config file cobblemon-spawning-rei.json")
                } catch (e: Exception) {
                    DebugLog.warn("Could not delete old config file: ${e.message}")
                }
            }
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
