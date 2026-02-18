package com.cobbledex

import com.cobblemon.mod.common.api.spawning.CobblemonSpawnPools
import com.cobblemon.mod.common.api.spawning.SpawnDetailPresets
import com.cobblemon.mod.common.api.spawning.SpawnSet
import com.cobblemon.mod.common.api.spawning.preset.SpawnDetailPreset
import net.minecraft.resources.ResourceLocation
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Path

/**
 * Client-side fallback that loads spawn data by reading JSON files from mod JARs
 * and feeding them through Cobblemon's own Gson parsers and preset system.
 *
 * Only used when WORLD_SPAWN_POOL is empty (dedicated server without CobbleDex server-side).
 * Cobblemon handles all deserialization, preset resolution, and condition compilation.
 */
object SpawnPoolClientLoader {

    private const val SPAWN_POOL_PATH = "spawn_pool_world"
    private const val PRESET_PATH = "spawn_detail_presets"

    @Volatile
    private var clientLoaded = false

    fun isClientLoaded(): Boolean = clientLoaded

    fun reset() {
        clientLoaded = false
    }

    /**
     * Attempts to populate CobblemonSpawnPools.WORLD_SPAWN_POOL on the client
     * by reading spawn JSON files from mod JARs using Cobblemon's own parsers.
     *
     * @return true if spawn pool was successfully populated with entries
     */
    fun loadSpawnPoolFromModJars(modRoots: List<Path>): Boolean {
        if (clientLoaded) return true

        val pool = try {
            CobblemonSpawnPools.WORLD_SPAWN_POOL
        } catch (e: Exception) {
            DebugLog.warn("Client spawn loader: cannot access WORLD_SPAWN_POOL: ${e.message}")
            return false
        }

        // Step 1: Load presets first — spawn detail deserialization depends on them
        val presetsLoaded = loadPresetsFromModJars(modRoots)
        if (!presetsLoaded) {
            DebugLog.warn("Client spawn loader: preset loading failed, spawn data may be incomplete")
        }

        // Step 2: Read all spawn JSON files from mod JARs
        val spawnSets = mutableMapOf<ResourceLocation, SpawnSet>()
        var fileCount = 0
        var failCount = 0

        for (root in modRoots) {
            try {
                val dataDir = root.resolve("data")
                if (!Files.exists(dataDir) || !Files.isDirectory(dataDir)) continue

                Files.list(dataDir).use { namespaces ->
                    namespaces.filter { Files.isDirectory(it) }.forEach { namespace ->
                        val namespaceName = namespace.fileName.toString()
                        val spawnDir = namespace.resolve(SPAWN_POOL_PATH)
                        if (!Files.exists(spawnDir) || !Files.isDirectory(spawnDir)) return@forEach

                        Files.walk(spawnDir, 10).use { files ->
                            files.filter { it.toString().endsWith(".json") && Files.isRegularFile(it) }.forEach { file ->
                                try {
                                    val relativePath = spawnDir.relativize(file).toString()
                                        .replace('\\', '/')
                                        .removeSuffix(".json")
                                    val resourceId = ResourceLocation.fromNamespaceAndPath(namespaceName, "$SPAWN_POOL_PATH/$relativePath")

                                    val reader = BufferedReader(InputStreamReader(Files.newInputStream(file), Charsets.UTF_8))
                                    reader.use {
                                        val spawnSet = pool.parse(it, resourceId)
                                        spawnSets[resourceId] = spawnSet
                                        fileCount++
                                    }
                                } catch (e: Exception) {
                                    failCount++
                                    DebugLog.once("client-spawn-parse-${file.fileName}") {
                                        "Client spawn loader: failed to parse ${file.fileName}: ${e.message}"
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                DebugLog.once("client-spawn-root-$root") { "Client spawn loader: failed scanning root: ${e.message}" }
            }
        }

        // Also scan local datapacks
        try {
            val datapacksDir = com.cobbledex.platform.PlatformHelper.getGameDir().resolve("datapacks")
            if (Files.exists(datapacksDir) && Files.isDirectory(datapacksDir)) {
                val dpResult = scanDatapacksForSpawns(datapacksDir, pool)
                spawnSets.putAll(dpResult.first)
                fileCount += dpResult.second
                failCount += dpResult.third
            }
        } catch (e: Exception) {
            DebugLog.once("client-spawn-datapacks") { "Client spawn loader: datapack scan failed: ${e.message}" }
        }

        if (spawnSets.isEmpty()) {
            DebugLog.warn("Client spawn loader: no spawn files found in ${modRoots.size} mod roots")
            return false
        }

        // Step 3: Feed the parsed data through Cobblemon's reload pipeline
        try {
            pool.reload(spawnSets)
            val detailCount = pool.details.size
            clientLoaded = detailCount > 0

            if (clientLoaded) {
                DebugLog.info("Client spawn loader: loaded $detailCount spawn details from $fileCount files ($failCount failed)")
            } else {
                DebugLog.warn("Client spawn loader: parsed $fileCount files but resulted in 0 spawn details")
            }
            return clientLoaded
        } catch (e: Exception) {
            DebugLog.warn("Client spawn loader: pool.reload() failed: ${e.message}")
            return false
        }
    }

    /**
     * Load spawn detail presets from mod JARs using Cobblemon's own parser.
     * These must be loaded before spawn data since SpawnDetailAdapter references them.
     */
    private fun loadPresetsFromModJars(modRoots: List<Path>): Boolean {
        val existingPresets = try {
            SpawnDetailPresets.presets
        } catch (e: Exception) {
            DebugLog.warn("Client spawn loader: cannot access SpawnDetailPresets: ${e.message}")
            return false
        }

        // If presets are already loaded (e.g. from singleplayer), skip
        if (existingPresets.isNotEmpty()) {
            DebugLog.info("Client spawn loader: ${existingPresets.size} presets already loaded, skipping preset loading")
            return true
        }

        val presetMap = mutableMapOf<ResourceLocation, SpawnDetailPreset>()
        var fileCount = 0

        for (root in modRoots) {
            try {
                val dataDir = root.resolve("data")
                if (!Files.exists(dataDir) || !Files.isDirectory(dataDir)) continue

                Files.list(dataDir).use { namespaces ->
                    namespaces.filter { Files.isDirectory(it) }.forEach { namespace ->
                        val namespaceName = namespace.fileName.toString()
                        val presetDir = namespace.resolve(PRESET_PATH)
                        if (!Files.exists(presetDir) || !Files.isDirectory(presetDir)) return@forEach

                        Files.walk(presetDir, 10).use { files ->
                            files.filter { it.toString().endsWith(".json") && Files.isRegularFile(it) }.forEach { file ->
                                try {
                                    val relativePath = presetDir.relativize(file).toString()
                                        .replace('\\', '/')
                                        .removeSuffix(".json")
                                    val resourceId = ResourceLocation.fromNamespaceAndPath(namespaceName, "$PRESET_PATH/$relativePath")

                                    val reader = BufferedReader(InputStreamReader(Files.newInputStream(file), Charsets.UTF_8))
                                    reader.use {
                                        val preset = SpawnDetailPresets.parse(it, resourceId)
                                        presetMap[resourceId] = preset
                                        fileCount++
                                    }
                                } catch (e: Exception) {
                                    DebugLog.once("client-preset-parse-${file.fileName}") {
                                        "Client spawn loader: failed to parse preset ${file.fileName}: ${e.message}"
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                DebugLog.once("client-preset-root-$root") { "Client spawn loader: preset scan failed for root: ${e.message}" }
            }
        }

        if (presetMap.isEmpty()) {
            DebugLog.warn("Client spawn loader: no preset files found")
            return false
        }

        try {
            SpawnDetailPresets.reload(presetMap)
            DebugLog.info("Client spawn loader: loaded ${presetMap.size} presets from $fileCount files")
            return true
        } catch (e: Exception) {
            DebugLog.warn("Client spawn loader: SpawnDetailPresets.reload() failed: ${e.message}")
            return false
        }
    }

    /**
     * Scan local datapacks directory for spawn pool files.
     * Returns (parsed sets, success count, fail count).
     */
    private fun scanDatapacksForSpawns(
        datapacksDir: Path,
        pool: com.cobblemon.mod.common.api.spawning.detail.SpawnPool
    ): Triple<Map<ResourceLocation, SpawnSet>, Int, Int> {
        val result = mutableMapOf<ResourceLocation, SpawnSet>()
        var fileCount = 0
        var failCount = 0

        Files.list(datapacksDir).use { packs ->
            packs.filter { Files.isDirectory(it) }.forEach { pack ->
                val dataDir = pack.resolve("data")
                if (!Files.exists(dataDir)) return@forEach

                Files.list(dataDir).use { namespaces ->
                    namespaces.filter { Files.isDirectory(it) }.forEach { namespace ->
                        val namespaceName = namespace.fileName.toString()
                        val spawnDir = namespace.resolve(SPAWN_POOL_PATH)
                        if (!Files.exists(spawnDir) || !Files.isDirectory(spawnDir)) return@forEach

                        Files.walk(spawnDir, 10).use { files ->
                            files.filter { it.toString().endsWith(".json") && Files.isRegularFile(it) }.forEach { file ->
                                try {
                                    val relativePath = spawnDir.relativize(file).toString()
                                        .replace('\\', '/')
                                        .removeSuffix(".json")
                                    val resourceId = ResourceLocation.fromNamespaceAndPath(namespaceName, "$SPAWN_POOL_PATH/$relativePath")

                                    val reader = BufferedReader(InputStreamReader(Files.newInputStream(file), Charsets.UTF_8))
                                    reader.use {
                                        val spawnSet = pool.parse(it, resourceId)
                                        result[resourceId] = spawnSet
                                        fileCount++
                                    }
                                } catch (e: Exception) {
                                    failCount++
                                    DebugLog.once("client-spawn-dp-${file.fileName}") {
                                        "Client spawn loader: failed to parse datapack spawn ${file.fileName}: ${e.message}"
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return Triple(result, fileCount, failCount)
    }
}
