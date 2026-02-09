package com.cobblemonrei

import com.google.gson.JsonParser
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Path

object SpawnDataLoader {

    fun loadFromCobblemonJar(): Map<String, List<SpawnInfo>> {
        val result = mutableMapOf<String, MutableList<SpawnInfo>>()
        val spawnPath = findCobblemonDataPath("spawn_pool_world") ?: run {
            CobblemonSpawningMod.LOGGER.warn("Could not find Cobblemon spawn_pool_world data path")
            return emptyMap()
        }

        var fileCount = 0
        var entryCount = 0

        Files.walk(spawnPath)
            .filter { it.toString().endsWith(".json") }
            .forEach { file ->
                try {
                    val json = JsonParser.parseReader(InputStreamReader(Files.newInputStream(file))).asJsonObject
                    if (json.has("enabled") && !json.get("enabled").asBoolean) return@forEach

                    val spawns = json.getAsJsonArray("spawns") ?: return@forEach
                    fileCount++

                    for (spawnElement in spawns) {
                        val spawn = spawnElement.asJsonObject
                        val pokemon = spawn.get("pokemon")?.asString ?: continue
                        val species = pokemon.split(" ").first().lowercase()

                        val info = parseSpawnEntry(spawn, species)
                        result.getOrPut(species) { mutableListOf() }.add(info)
                        entryCount++
                    }
                } catch (e: Exception) {
                    CobblemonSpawningMod.LOGGER.debug("Failed to parse spawn file ${file.fileName}: ${e.message}")
                }
            }

        CobblemonSpawningMod.LOGGER.info("Parsed $entryCount spawn entries from $fileCount files")
        return result
    }

    private fun parseSpawnEntry(spawn: com.google.gson.JsonObject, species: String): SpawnInfo {
        val id = spawn.get("id")?.asString ?: species
        val bucket = spawn.get("bucket")?.asString ?: "common"
        val weight = spawn.get("weight")?.asFloat ?: 1.0f
        val level = spawn.get("level")?.asString ?: spawn.get("levelRange")?.asString ?: "1-100"
        val context = spawn.get("context")?.asString
            ?: spawn.get("spawnablePositionType")?.asString
            ?: "grounded"
        val presets = spawn.getAsJsonArray("presets")?.map { it.asString } ?: emptyList()

        var biomes = emptyList<String>()
        var timeRange: String? = null
        var isRaining: Boolean? = null
        var isThundering: Boolean? = null
        var dimensions = emptyList<String>()
        var structures = emptyList<String>()
        var canSeeSky: Boolean? = null
        var minLight: Int? = null
        var maxLight: Int? = null
        var neededNearbyBlocks = emptyList<String>()
        var neededBaseBlocks = emptyList<String>()
        var moonPhase: String? = null

        val condition = spawn.getAsJsonObject("condition")
        if (condition != null) {
            biomes = condition.getAsJsonArray("biomes")?.map { it.asString } ?: emptyList()
            timeRange = condition.get("timeRange")?.asString
            isRaining = condition.get("isRaining")?.asBoolean
            isThundering = condition.get("isThundering")?.asBoolean
            dimensions = condition.getAsJsonArray("dimensions")?.map { it.asString } ?: emptyList()
            structures = condition.getAsJsonArray("structures")?.map { it.asString } ?: emptyList()
            canSeeSky = condition.get("canSeeSky")?.asBoolean
            minLight = condition.get("minLight")?.asInt
            maxLight = condition.get("maxLight")?.asInt
            neededNearbyBlocks = condition.getAsJsonArray("neededNearbyBlocks")?.map { it.asString } ?: emptyList()
            neededBaseBlocks = condition.getAsJsonArray("neededBaseBlocks")?.map { it.asString } ?: emptyList()
            moonPhase = condition.get("moonPhase")?.asString
        }

        // Also check compositeCondition
        val composite = spawn.getAsJsonObject("compositeCondition")
        if (composite != null && biomes.isEmpty()) {
            val conditions = composite.getAsJsonArray("conditions")
            if (conditions != null && conditions.size() > 0) {
                val first = conditions[0].asJsonObject
                biomes = first.getAsJsonArray("biomes")?.map { it.asString } ?: biomes
                timeRange = timeRange ?: first.get("timeRange")?.asString
                isRaining = isRaining ?: first.get("isRaining")?.asBoolean
                isThundering = isThundering ?: first.get("isThundering")?.asBoolean
            }
        }

        return SpawnInfo(
            id = id,
            pokemon = species,
            bucket = bucket,
            weight = weight,
            levelRange = level,
            context = context,
            biomes = biomes,
            timeRange = timeRange,
            weather = SpawnWeather(isRaining, isThundering),
            dimensions = dimensions,
            structures = structures,
            canSeeSky = canSeeSky,
            minLight = minLight,
            maxLight = maxLight,
            neededNearbyBlocks = neededNearbyBlocks,
            neededBaseBlocks = neededBaseBlocks,
            moonPhase = moonPhase,
            presets = presets
        )
    }

    fun findCobblemonDataPath(subdir: String): Path? {
        return findCobblemonRootPath()?.resolve("data/cobblemon/$subdir")?.takeIf { Files.exists(it) }
    }

    private var cachedRootPath: Path? = null

    fun findCobblemonRootPath(): Path? {
        cachedRootPath?.let { return it }

        // Try Fabric mod container
        try {
            val fabricLoader = Class.forName("net.fabricmc.loader.api.FabricLoader")
            val getInstance = fabricLoader.getMethod("getInstance")
            val loader = getInstance.invoke(null)
            val getModContainer = loader.javaClass.getMethod("getModContainer", String::class.java)
            val optional = getModContainer.invoke(loader, "cobblemon") as java.util.Optional<*>
            if (optional.isPresent) {
                val container = optional.get()
                val getRootPaths = container.javaClass.getMethod("getRootPaths")
                @Suppress("UNCHECKED_CAST")
                val paths = getRootPaths.invoke(container) as List<Path>
                if (paths.isNotEmpty()) {
                    val root = paths.first()
                    if (Files.exists(root.resolve("data/cobblemon"))) {
                        cachedRootPath = root
                        return root
                    }
                }
            }
        } catch (_: Exception) { }

        // Try NeoForge mod list
        try {
            val modList = Class.forName("net.neoforged.fml.ModList")
            val get = modList.getMethod("get")
            val list = get.invoke(null)
            val getModFileById = list.javaClass.getMethod("getModFileById", String::class.java)
            val modFileInfo = getModFileById.invoke(list, "cobblemon") ?: return null
            val getFile = modFileInfo.javaClass.getMethod("getFile")
            val modFile = getFile.invoke(modFileInfo)
            val findResource = modFile.javaClass.getMethod("findResource", Array<String>::class.java)
            val path = findResource.invoke(modFile, arrayOf("data", "cobblemon")) as Path
            if (Files.exists(path)) {
                cachedRootPath = path.parent
                return path.parent
            }
        } catch (_: Exception) { }

        CobblemonSpawningMod.LOGGER.warn("Could not locate Cobblemon mod JAR data path")
        return null
    }
}
