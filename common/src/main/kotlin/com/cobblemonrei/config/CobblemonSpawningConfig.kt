package com.cobblemonrei.config

import com.cobblemonrei.DebugLog
import com.cobblemonrei.platform.PlatformHelper
import com.google.gson.GsonBuilder
import java.nio.file.Files

data class CobblemonSpawningConfig(
    val showSpawnWeights: Boolean = true,
    val showEvolutions: Boolean = true,
    val showObtainment: Boolean = true,
    val localDatapackScan: Boolean = true,
    val debugMode: Boolean = false
) {
    companion object {
        private val GSON = GsonBuilder().setPrettyPrinting().create()
        private const val CONFIG_VERSION = 2  // Increment when defaults change

        @Volatile
        private var instance: CobblemonSpawningConfig = CobblemonSpawningConfig()

        fun get(): CobblemonSpawningConfig = instance

        fun load() {
            val file = PlatformHelper.getConfigDir().resolve("cobblemon-spawning-rei.json")
            var needsSave = !Files.exists(file)
            try {
                if (Files.exists(file)) {
                    val jsonText = Files.readString(file)
                    val loaded = GSON.fromJson(jsonText, CobblemonSpawningConfig::class.java)
                    if (loaded != null) {
                        // Force enable localDatapackScan if old config had it disabled
                        // This ensures ZIP datapack scanning works after update
                        instance = if (!loaded.localDatapackScan) {
                            DebugLog.info("Migrating config: enabling localDatapackScan for ZIP datapack support")
                            needsSave = true
                            loaded.copy(localDatapackScan = true)
                        } else {
                            loaded
                        }
                    }
                }
            } catch (e: Exception) {
                DebugLog.warn("Config load failed, using defaults: ${e.message}")
                instance = CobblemonSpawningConfig()
                needsSave = true
            }
            if (needsSave) save()
        }

        fun save() {
            try {
                val file = PlatformHelper.getConfigDir().resolve("cobblemon-spawning-rei.json")
                Files.createDirectories(file.parent)
                Files.writeString(file, GSON.toJson(instance))
            } catch (e: Exception) {
                DebugLog.warn("Config save failed: ${e.message}")
            }
        }
    }
}
