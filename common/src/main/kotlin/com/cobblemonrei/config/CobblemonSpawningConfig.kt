package com.cobblemonrei.config

import com.cobblemonrei.DebugLog
import com.cobblemonrei.platform.PlatformHelper
import com.google.gson.GsonBuilder
import java.nio.file.Files

data class CobblemonSpawningConfig(
    val showSpawnWeights: Boolean = true,
    val showEvolutions: Boolean = true,
    val localDatapackScan: Boolean = false,
    val debugMode: Boolean = false
) {
    companion object {
        private val GSON = GsonBuilder().setPrettyPrinting().create()

        @Volatile
        private var instance: CobblemonSpawningConfig = CobblemonSpawningConfig()

        fun get(): CobblemonSpawningConfig = instance

        fun load() {
            val file = PlatformHelper.getConfigDir().resolve("cobblemon-spawning-rei.json")
            try {
                if (Files.exists(file)) {
                    val loaded = GSON.fromJson(Files.readString(file), CobblemonSpawningConfig::class.java)
                    if (loaded != null) instance = loaded
                }
            } catch (e: Exception) {
                DebugLog.warn("Config load failed, using defaults: ${e.message}")
                instance = CobblemonSpawningConfig()
            }
            save()
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
