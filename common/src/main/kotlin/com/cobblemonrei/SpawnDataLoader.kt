package com.cobblemonrei

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.client.Minecraft
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

object SpawnDataLoader {

    private data class PresetData(
        val condition: JsonObject?,
        val anticondition: JsonObject?
    )

    private var presetCache: Map<String, PresetData> = emptyMap()

    fun loadFromAllSources(): Map<String, List<SpawnInfo>> {
        val roots = findAllModRootPaths()
        DebugLog.debug("Scanning ${roots.size} mod roots for spawn data")

        presetCache = loadAllPresets(roots)
        DebugLog.debug("Loaded ${presetCache.size} spawn presets: ${presetCache.keys.sorted().joinToString(", ")}")

        val result = mutableMapOf<String, MutableList<SpawnInfo>>()
        var totalFiles = 0
        var totalEntries = 0

        for (root in roots) {
            try {
                val dataDir = root.resolve("data")
                if (!Files.exists(dataDir) || !Files.isDirectory(dataDir)) continue

                Files.list(dataDir).use { namespaces ->
                    namespaces.filter { Files.isDirectory(it) }.forEach { namespace ->
                        val spawnDir = namespace.resolve("spawn_pool_world")
                        if (Files.exists(spawnDir) && Files.isDirectory(spawnDir)) {
                            Files.walk(spawnDir).use { files ->
                                files.filter { it.toString().endsWith(".json") }.forEach { file ->
                                    val (added, count) = parseSpawnFile(file, result)
                                    if (added) totalFiles++
                                    totalEntries += count
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                DebugLog.once("scan-root-${root}") { "Error scanning mod root: ${e.message}" }
            }
        }

        // Datapacks folder
        try {
            val gameDir = Minecraft.getInstance().gameDirectory.toPath()
            val datapacksDir = gameDir.resolve("datapacks")
            if (Files.exists(datapacksDir) && Files.isDirectory(datapacksDir)) {
                scanDatapacksDir(datapacksDir, result) { added, count ->
                    totalFiles += if (added) 1 else 0
                    totalEntries += count
                }
            }
        } catch (e: Exception) {
            DebugLog.once("datapack-scan") { "Datapack scan failed: ${e.message}" }
        }

        DebugLog.info("Parsed $totalEntries spawn entries from $totalFiles files (${presetCache.size} presets)")
        return result
    }

    // --- Preset Loading ---

    private fun loadAllPresets(roots: List<Path>): Map<String, PresetData> {
        val result = mutableMapOf<String, PresetData>()
        for (root in roots) {
            try {
                val dataDir = root.resolve("data")
                if (!Files.exists(dataDir) || !Files.isDirectory(dataDir)) continue
                Files.list(dataDir).use { namespaces ->
                    namespaces.filter { Files.isDirectory(it) }.forEach { namespace ->
                        val presetDir = namespace.resolve("spawn_detail_presets")
                        if (Files.exists(presetDir) && Files.isDirectory(presetDir)) {
                            Files.list(presetDir).use { files ->
                                files.filter { it.toString().endsWith(".json") }.forEach { file ->
                                    parsePresetFile(file)?.let { (name, data) -> result[name] = data }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                DebugLog.once("preset-root-${root}") { "Preset scan failed for root: ${e.message}" }
            }
        }
        return result
    }

    private fun parsePresetFile(file: Path): Pair<String, PresetData>? {
        return try {
            val name = file.fileName.toString().removeSuffix(".json")
            val json = Files.newInputStream(file).use { stream ->
                InputStreamReader(stream).use { reader ->
                    JsonParser.parseReader(reader).asJsonObject
                }
            }
            name to PresetData(
                condition = json.getAsJsonObject("condition"),
                anticondition = json.getAsJsonObject("anticondition")
            )
        } catch (e: Exception) {
            DebugLog.once("preset-parse-${file.fileName}") { "Failed to parse preset: ${e.message}" }
            null
        }
    }

    // --- Spawn File Parsing ---

    private fun parseSpawnFile(file: Path, result: MutableMap<String, MutableList<SpawnInfo>>): Pair<Boolean, Int> {
        var entryCount = 0
        try {
            val json = Files.newInputStream(file).use { stream ->
                InputStreamReader(stream).use { reader ->
                    JsonParser.parseReader(reader).asJsonObject
                }
            }
            if (json.has("enabled") && !json.get("enabled").asBoolean) return false to 0
            val spawns = json.getAsJsonArray("spawns") ?: return false to 0

            for (spawnElement in spawns) {
                val spawn = spawnElement.asJsonObject
                val pokemonField = spawn.get("pokemon")?.asString ?: continue
                val species = pokemonField.split(" ").first().lowercase()
                result.getOrPut(species) { mutableListOf() }.add(parseSpawnEntry(spawn, species, pokemonField))
                entryCount++
            }
        } catch (e: Exception) {
            DebugLog.trackFailedSpawn(file.fileName.toString(), e.message ?: "unknown")
            return false to 0
        }
        return (entryCount > 0) to entryCount
    }

    private fun parseSpawnEntry(spawn: JsonObject, species: String, pokemonField: String): SpawnInfo {
        val id = spawn.get("id")?.asString ?: species
        val bucket = spawn.get("bucket")?.asString ?: "common"
        val weight = spawn.get("weight")?.asFloat ?: 1.0f
        val level = spawn.get("level")?.asString ?: spawn.get("levelRange")?.asString ?: "1-100"
        val context = spawn.get("spawnablePositionType")?.asString
            ?: spawn.get("context")?.asString
            ?: "grounded"
        val presetNames = spawn.getAsJsonArray("presets")?.map { it.asString } ?: emptyList()

        // Form aspects: "pikachu region_bias=alola" → "region_bias=alola"
        val parts = pokemonField.split(" ")
        val formAspects = if (parts.size > 1) parts.drop(1).joinToString(" ") else ""

        // Parse entry's own condition
        val condition = spawn.getAsJsonObject("condition")
        val cond = parseConditionFields(condition)

        // Parse compositeCondition as fallback
        val composite = spawn.getAsJsonObject("compositeCondition")
        val compCond = parseCompositeCondition(composite)

        // Merge entry condition + composite fallback
        val merged = mergeConditionData(cond, compCond)

        // Resolve and merge preset conditions
        val presetMerged = resolvePresets(presetNames, merged)

        // Parse entry's own anticondition
        val entryAnti = parseAntiConditionBlock(spawn.getAsJsonObject("anticondition"))

        // Merge preset anticonditions
        val presetAntis = presetNames.mapNotNull { presetCache[it]?.anticondition }.map { parseAntiConditionBlock(it) }
        val combinedAnti = mergeAntiConditions(listOfNotNull(entryAnti) + presetAntis)

        // Parse weight multipliers
        val weightMults = parseWeightMultipliers(spawn)

        // Min lure level
        val minLureLevel = condition?.get("minLureLevel")?.asInt

        return SpawnInfo(
            id = id,
            pokemon = species,
            formAspects = formAspects,
            bucket = bucket,
            weight = weight,
            levelRange = level,
            context = context,
            biomes = presetMerged.biomes,
            timeRange = presetMerged.timeRange,
            weather = SpawnWeather(presetMerged.isRaining, presetMerged.isThundering),
            dimensions = presetMerged.dimensions,
            structures = presetMerged.structures,
            canSeeSky = presetMerged.canSeeSky,
            minLight = presetMerged.minLight,
            maxLight = presetMerged.maxLight,
            minSkyLight = presetMerged.minSkyLight,
            maxSkyLight = presetMerged.maxSkyLight,
            minY = presetMerged.minY,
            maxY = presetMerged.maxY,
            neededNearbyBlocks = presetMerged.neededNearbyBlocks,
            neededBaseBlocks = presetMerged.neededBaseBlocks,
            moonPhase = presetMerged.moonPhase,
            presets = presetNames,
            fluid = presetMerged.fluid,
            anticondition = combinedAnti,
            weightMultipliers = weightMults,
            minLureLevel = minLureLevel
        )
    }

    // --- Condition Parsing ---

    private data class ConditionData(
        val biomes: List<String> = emptyList(),
        val timeRange: String? = null,
        val isRaining: Boolean? = null,
        val isThundering: Boolean? = null,
        val dimensions: List<String> = emptyList(),
        val structures: List<String> = emptyList(),
        val canSeeSky: Boolean? = null,
        val minLight: Int? = null,
        val maxLight: Int? = null,
        val minSkyLight: Int? = null,
        val maxSkyLight: Int? = null,
        val minY: Int? = null,
        val maxY: Int? = null,
        val neededNearbyBlocks: List<String> = emptyList(),
        val neededBaseBlocks: List<String> = emptyList(),
        val moonPhase: String? = null,
        val fluid: String? = null
    )

    private fun parseConditionFields(obj: JsonObject?): ConditionData {
        if (obj == null) return ConditionData()
        return ConditionData(
            biomes = obj.getAsJsonArray("biomes")?.map { it.asString } ?: emptyList(),
            timeRange = obj.get("timeRange")?.asString,
            isRaining = obj.get("isRaining")?.asBoolean,
            isThundering = obj.get("isThundering")?.asBoolean,
            dimensions = obj.getAsJsonArray("dimensions")?.map { it.asString } ?: emptyList(),
            structures = obj.getAsJsonArray("structures")?.map { it.asString } ?: emptyList(),
            canSeeSky = obj.get("canSeeSky")?.asBoolean,
            minLight = obj.get("minLight")?.asInt,
            maxLight = obj.get("maxLight")?.asInt,
            minSkyLight = obj.get("minSkyLight")?.asInt,
            maxSkyLight = obj.get("maxSkyLight")?.asInt,
            minY = obj.get("minY")?.asInt,
            maxY = obj.get("maxY")?.asInt,
            neededNearbyBlocks = obj.getAsJsonArray("neededNearbyBlocks")?.map { it.asString } ?: emptyList(),
            neededBaseBlocks = obj.getAsJsonArray("neededBaseBlocks")?.map { it.asString } ?: emptyList(),
            moonPhase = obj.get("moonPhase")?.let {
                if (it.isJsonPrimitive && it.asJsonPrimitive.isNumber) it.asInt.toString()
                else it.asString
            },
            fluid = obj.get("fluid")?.asString
        )
    }

    private fun parseCompositeCondition(composite: JsonObject?): ConditionData {
        if (composite == null) return ConditionData()
        val conditions = composite.getAsJsonArray("conditions") ?: return ConditionData()

        // Composite sub-conditions use AND semantics — biomes/dimensions must intersect
        var merged = ConditionData()
        for (element in conditions) {
            val sub = parseConditionFields(element.asJsonObject)
            merged = mergeConditionDataAnd(merged, sub)
        }
        return merged
    }

    /** AND-merge for composite conditions. Biomes/dimensions intersect; scalars use primary-wins. */
    private fun mergeConditionDataAnd(primary: ConditionData, secondary: ConditionData): ConditionData {
        return ConditionData(
            biomes = intersectLists(primary.biomes, secondary.biomes),
            timeRange = primary.timeRange ?: secondary.timeRange,
            isRaining = primary.isRaining ?: secondary.isRaining,
            isThundering = primary.isThundering ?: secondary.isThundering,
            dimensions = intersectLists(primary.dimensions, secondary.dimensions),
            structures = intersectLists(primary.structures, secondary.structures),
            canSeeSky = primary.canSeeSky ?: secondary.canSeeSky,
            minLight = primary.minLight ?: secondary.minLight,
            maxLight = primary.maxLight ?: secondary.maxLight,
            minSkyLight = primary.minSkyLight ?: secondary.minSkyLight,
            maxSkyLight = primary.maxSkyLight ?: secondary.maxSkyLight,
            minY = primary.minY ?: secondary.minY,
            maxY = primary.maxY ?: secondary.maxY,
            neededNearbyBlocks = combineLists(primary.neededNearbyBlocks, secondary.neededNearbyBlocks),
            neededBaseBlocks = combineLists(primary.neededBaseBlocks, secondary.neededBaseBlocks),
            moonPhase = primary.moonPhase ?: secondary.moonPhase,
            fluid = primary.fluid ?: secondary.fluid
        )
    }

    /** OR-merge for presets. Lists are unioned; primary scalars win. */
    private fun mergeConditionData(primary: ConditionData, secondary: ConditionData): ConditionData {
        return ConditionData(
            biomes = combineLists(primary.biomes, secondary.biomes),
            timeRange = primary.timeRange ?: secondary.timeRange,
            isRaining = primary.isRaining ?: secondary.isRaining,
            isThundering = primary.isThundering ?: secondary.isThundering,
            dimensions = combineLists(primary.dimensions, secondary.dimensions),
            structures = combineLists(primary.structures, secondary.structures),
            canSeeSky = primary.canSeeSky ?: secondary.canSeeSky,
            minLight = primary.minLight ?: secondary.minLight,
            maxLight = primary.maxLight ?: secondary.maxLight,
            minSkyLight = primary.minSkyLight ?: secondary.minSkyLight,
            maxSkyLight = primary.maxSkyLight ?: secondary.maxSkyLight,
            minY = primary.minY ?: secondary.minY,
            maxY = primary.maxY ?: secondary.maxY,
            neededNearbyBlocks = combineLists(primary.neededNearbyBlocks, secondary.neededNearbyBlocks),
            neededBaseBlocks = combineLists(primary.neededBaseBlocks, secondary.neededBaseBlocks),
            moonPhase = primary.moonPhase ?: secondary.moonPhase,
            fluid = primary.fluid ?: secondary.fluid
        )
    }

    private fun combineLists(a: List<String>, b: List<String>): List<String> {
        if (a.isEmpty()) return b
        if (b.isEmpty()) return a
        return (a + b).distinct()
    }

    /** Intersection treating empty as wildcard (unconstrained). */
    private fun intersectLists(a: List<String>, b: List<String>): List<String> {
        if (a.isEmpty()) return b
        if (b.isEmpty()) return a
        return a.filter { it in b }
    }

    /** Resolves presets and merges their conditions into the base entry data. */
    private fun resolvePresets(presetNames: List<String>, baseCond: ConditionData): ConditionData {
        var result = baseCond
        for (name in presetNames) {
            val preset = presetCache[name] ?: continue
            val presetCond = parseConditionFields(preset.condition)
            result = mergeConditionData(result, presetCond)
        }
        return result
    }

    // --- Anti-Condition Parsing ---

    private fun parseAntiConditionBlock(obj: JsonObject?): SpawnAntiCondition? {
        if (obj == null) return null
        val anti = SpawnAntiCondition(
            biomes = obj.getAsJsonArray("biomes")?.map { it.asString } ?: emptyList(),
            structures = obj.getAsJsonArray("structures")?.map { it.asString } ?: emptyList(),
            neededBaseBlocks = obj.getAsJsonArray("neededBaseBlocks")?.map { it.asString } ?: emptyList(),
            neededNearbyBlocks = obj.getAsJsonArray("neededNearbyBlocks")?.map { it.asString } ?: emptyList(),
            minY = obj.get("minY")?.asInt,
            maxY = obj.get("maxY")?.asInt
        )
        return if (anti.isEmpty) null else anti
    }

    private fun mergeAntiConditions(antis: List<SpawnAntiCondition?>): SpawnAntiCondition? {
        val nonNull = antis.filterNotNull()
        if (nonNull.isEmpty()) return null
        if (nonNull.size == 1) return nonNull.first()
        return SpawnAntiCondition(
            biomes = nonNull.flatMap { it.biomes }.distinct(),
            structures = nonNull.flatMap { it.structures }.distinct(),
            neededBaseBlocks = nonNull.flatMap { it.neededBaseBlocks }.distinct(),
            neededNearbyBlocks = nonNull.flatMap { it.neededNearbyBlocks }.distinct(),
            minY = nonNull.mapNotNull { it.minY }.minOrNull(),
            maxY = nonNull.mapNotNull { it.maxY }.maxOrNull()
        ).takeIf { !it.isEmpty }
    }

    // --- Weight Multipliers ---

    private fun parseWeightMultipliers(spawn: JsonObject): List<WeightMultiplier> {
        val result = mutableListOf<WeightMultiplier>()

        // Single: "weightMultiplier": { "multiplier": 5.0, "condition": {...} }
        spawn.getAsJsonObject("weightMultiplier")?.let { wm ->
            val mult = wm.get("multiplier")?.asFloat ?: return@let
            val cond = wm.getAsJsonObject("condition")
            result.add(WeightMultiplier(mult, summarizeCondition(cond)))
        }

        // Array: "weightMultipliers": [...]
        spawn.getAsJsonArray("weightMultipliers")?.forEach { element ->
            val wm = element.asJsonObject
            val mult = wm.get("multiplier")?.asFloat ?: return@forEach
            val cond = wm.getAsJsonObject("condition")
            result.add(WeightMultiplier(mult, summarizeCondition(cond)))
        }

        return result
    }

    private fun summarizeCondition(cond: JsonObject?): String {
        if (cond == null) return "always"
        val parts = mutableListOf<String>()
        cond.get("isThundering")?.asBoolean?.let { if (it) parts.add("thunderstorm") }
        cond.get("isRaining")?.asBoolean?.let { if (it) parts.add("rain") }
        cond.get("timeRange")?.asString?.let { parts.add(it) }
        cond.getAsJsonArray("biomes")?.let { arr ->
            val names = arr.map { formatId(it.asString) }
            if (names.size <= 3) parts.add(names.joinToString(", "))
            else parts.add("${names.take(2).joinToString(", ")} +${names.size - 2} more")
        }
        cond.get("minLureLevel")?.asInt?.let { parts.add("lure $it+") }
        return if (parts.isEmpty()) "conditional" else parts.joinToString(", ")
    }

    // --- Datapacks Scanning ---

    private fun scanDatapacksDir(
        datapacksDir: Path,
        result: MutableMap<String, MutableList<SpawnInfo>>,
        counter: (Boolean, Int) -> Unit
    ) {
        Files.list(datapacksDir).use { packs ->
            packs.filter { Files.isDirectory(it) }.forEach { pack ->
                val dataDir = pack.resolve("data")
                if (Files.exists(dataDir)) {
                    Files.list(dataDir).use { namespaces ->
                        namespaces.filter { Files.isDirectory(it) }.forEach { namespace ->
                            val spawnDir = namespace.resolve("spawn_pool_world")
                            if (Files.exists(spawnDir)) {
                                Files.walk(spawnDir).use { files ->
                                    files.filter { it.toString().endsWith(".json") }.forEach { file ->
                                        val (added, count) = parseSpawnFile(file, result)
                                        counter(added, count)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // --- Utility ---

    private fun findAllModRootPaths(): List<Path> {
        val paths = mutableListOf<Path>()

        // Fabric
        try {
            val fabricLoader = Class.forName("net.fabricmc.loader.api.FabricLoader")
            val instance = fabricLoader.getMethod("getInstance").invoke(null)
            @Suppress("UNCHECKED_CAST")
            val allMods = instance.javaClass.getMethod("getAllMods").invoke(instance) as Collection<Any>
            for (mod in allMods) {
                @Suppress("UNCHECKED_CAST")
                val rootPaths = mod.javaClass.getMethod("getRootPaths").invoke(mod) as List<Path>
                paths.addAll(rootPaths)
            }
        } catch (_: Exception) {}

        // NeoForge
        try {
            val modList = Class.forName("net.neoforged.fml.ModList")
            val list = modList.getMethod("get").invoke(null)
            @Suppress("UNCHECKED_CAST")
            val modFiles = list.javaClass.getMethod("getModFiles").invoke(list) as List<Any>
            for (modFileInfo in modFiles) {
                try {
                    val modFile = modFileInfo.javaClass.getMethod("getFile").invoke(modFileInfo)
                    val findResource = modFile.javaClass.getMethod("findResource", Array<String>::class.java)
                    val dataPath = findResource.invoke(modFile, arrayOf("data")) as? Path
                    if (dataPath != null && Files.exists(dataPath)) {
                        paths.add(dataPath.parent)
                    }
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}

        return paths.distinct()
    }

    @Deprecated("Use loadFromAllSources()", ReplaceWith("loadFromAllSources()"))
    fun loadFromCobblemonJar(): Map<String, List<SpawnInfo>> = loadFromAllSources()

    fun findCobblemonDataPath(subdir: String): Path? {
        return findCobblemonRootPath()?.resolve("data/cobblemon/$subdir")?.takeIf { Files.exists(it) }
    }

    @Volatile
    private var cachedRootPath: Path? = null

    fun findCobblemonRootPath(): Path? {
        cachedRootPath?.let { return it }

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

        DebugLog.once("cobblemon-jar-path") { "Could not locate Cobblemon mod JAR data path" }
        return null
    }
}
