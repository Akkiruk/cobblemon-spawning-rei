package com.cobbledex

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile

object SpawnDataLoader {

    private data class PresetData(
        val condition: JsonObject?,
        val anticondition: JsonObject?
    )

    private var presetCache: Map<String, PresetData> = emptyMap()

    @Volatile
    private var cachedModRoots: List<Path>? = null

    fun invalidateCache() {
        cachedModRoots = null
    }

    fun loadFromAllSources(extraDatapacksDir: Path? = null): Map<String, List<SpawnInfo>> {
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
                            Files.walk(spawnDir, 10).use { files ->
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

        // Scan explicitly provided datapacks directory (server world datapacks)
        if (extraDatapacksDir != null && Files.exists(extraDatapacksDir) && Files.isDirectory(extraDatapacksDir)) {
            scanDatapacksDir(extraDatapacksDir, result) { added, count ->
                totalFiles += if (added) 1 else 0
                totalEntries += count
            }
        }

        // Scan client-side datapacks folder if config allows
        val scanDatapacks = com.cobbledex.config.CobbleDexConfig.get().localDatapackScan
        DebugLog.info("Local datapack scan enabled: $scanDatapacks")
        if (scanDatapacks) {
            val datapacksDir = getClientDatapacksDir()
            DebugLog.info("Datapacks directory: $datapacksDir (exists: ${datapacksDir?.let { Files.exists(it) }})")
            if (datapacksDir != null && Files.exists(datapacksDir) && Files.isDirectory(datapacksDir)) {
                val preCount = result.size
                scanDatapacksDir(datapacksDir, result) { added, count ->
                    totalFiles += if (added) 1 else 0
                    totalEntries += count
                }
                DebugLog.info("Datapack scan added ${result.size - preCount} new species, ${totalEntries} total entries")
            }
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
            return parseSpawnJson(json, file.fileName.toString(), result)
        } catch (e: Exception) {
            DebugLog.trackFailedSpawn(file.fileName.toString(), e.message ?: "unknown")
            return false to 0
        }
    }

    private fun parseSpawnJson(json: JsonObject, sourceName: String, result: MutableMap<String, MutableList<SpawnInfo>>): Pair<Boolean, Int> {
        var entryCount = 0
        try {
            val enabledField = json.get("enabled")
            val enabled = when {
                enabledField == null -> true
                enabledField.isJsonPrimitive && enabledField.asJsonPrimitive.isBoolean -> enabledField.asBoolean
                enabledField.isJsonPrimitive && enabledField.asJsonPrimitive.isString -> enabledField.asString.equals("true", ignoreCase = true)
                else -> true
            }
            if (!enabled) return false to 0
            
            val spawns = json.getAsJsonArray("spawns") ?: return false to 0

            for (spawnElement in spawns) {
                val spawn = spawnElement.asJsonObject
                val pokemonElement = spawn.get("pokemon") ?: continue
                val pokemonField: String = when {
                    pokemonElement.isJsonPrimitive -> pokemonElement.asString
                    pokemonElement.isJsonObject -> {
                        val obj = pokemonElement.asJsonObject
                        val name = obj.get("pokemon")?.asString ?: continue
                        val aspects = obj.getAsJsonArray("aspects")?.joinToString(" ") { it.asString } ?: ""
                        if (aspects.isNotBlank()) "$name $aspects" else name
                    }
                    else -> continue
                }
                val species = pokemonField.split(" ").first().lowercase()
                result.getOrPut(species) { mutableListOf() }.add(parseSpawnEntry(spawn, species, pokemonField))
                entryCount++
            }
        } catch (e: Exception) {
            DebugLog.trackFailedSpawn(sourceName, e.message ?: "unknown")
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
        val bSet = b.toHashSet()
        return a.filter { it in bSet }
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
            maxY = obj.get("maxY")?.asInt,
            timeRange = obj.get("timeRange")?.asString,
            dimensions = obj.getAsJsonArray("dimensions")?.map { it.asString } ?: emptyList(),
            isRaining = if (obj.has("isRaining")) obj.get("isRaining")?.asBoolean else null,
            isThundering = if (obj.has("isThundering")) obj.get("isThundering")?.asBoolean else null,
            minLight = obj.get("minLight")?.asInt,
            maxLight = obj.get("maxLight")?.asInt,
            moonPhase = obj.get("moonPhase")?.asString
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
            maxY = nonNull.mapNotNull { it.maxY }.maxOrNull(),
            timeRange = nonNull.mapNotNull { it.timeRange }.firstOrNull(),
            dimensions = nonNull.flatMap { it.dimensions }.distinct(),
            isRaining = nonNull.mapNotNull { it.isRaining }.firstOrNull(),
            isThundering = nonNull.mapNotNull { it.isThundering }.firstOrNull(),
            minLight = nonNull.mapNotNull { it.minLight }.minOrNull(),
            maxLight = nonNull.mapNotNull { it.maxLight }.maxOrNull(),
            moonPhase = nonNull.mapNotNull { it.moonPhase }.firstOrNull()
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
        if (cond == null) return tr("cobbledex-rei-emi-jei.weight.always")
        val parts = mutableListOf<String>()
        cond.get("isThundering")?.asBoolean?.let { if (it) parts.add(tr("cobbledex-rei-emi-jei.weight.thunderstorm")) }
        cond.get("isRaining")?.asBoolean?.let { if (it) parts.add(tr("cobbledex-rei-emi-jei.weight.rain")) }
        cond.get("timeRange")?.asString?.let { parts.add(it) }
        cond.getAsJsonArray("biomes")?.let { arr ->
            val names = arr.map { formatId(it.asString) }
            if (names.size <= 3) parts.add(names.joinToString(", "))
            else parts.add("${names.take(2).joinToString(", ")} " + tr("cobbledex-rei-emi-jei.weight.and_more", names.size - 2))
        }
        cond.get("minLureLevel")?.asInt?.let { parts.add(tr("cobbledex-rei-emi-jei.weight.lure", it)) }
        return if (parts.isEmpty()) tr("cobbledex-rei-emi-jei.weight.conditional") else parts.joinToString(", ")
    }

    // --- Datapacks Scanning ---

    private fun scanDatapacksDir(
        datapacksDir: Path,
        result: MutableMap<String, MutableList<SpawnInfo>>,
        counter: (Boolean, Int) -> Unit
    ) {
        DebugLog.info("Scanning datapacks in: $datapacksDir")
        Files.list(datapacksDir).use { packs ->
            packs.forEach { pack ->
                when {
                    Files.isDirectory(pack) -> {
                        DebugLog.info("  Scanning directory datapack: ${pack.fileName}")
                        scanDatapackDir(pack, result, counter)
                    }
                    pack.toString().endsWith(".zip") -> {
                        DebugLog.info("  Scanning ZIP datapack: ${pack.fileName}")
                        scanDatapackZip(pack, result, counter)
                    }
                }
            }
        }
    }

    private fun scanDatapackDir(
        pack: Path,
        result: MutableMap<String, MutableList<SpawnInfo>>,
        counter: (Boolean, Int) -> Unit
    ) {
        val dataDir = pack.resolve("data")
        if (Files.exists(dataDir)) {
            Files.list(dataDir).use { namespaces ->
                namespaces.filter { Files.isDirectory(it) }.forEach { namespace ->
                    val spawnDir = namespace.resolve("spawn_pool_world")
                    if (Files.exists(spawnDir)) {
                        Files.walk(spawnDir, 10).use { files ->
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

    private fun scanDatapackZip(
        zipPath: Path,
        result: MutableMap<String, MutableList<SpawnInfo>>,
        counter: (Boolean, Int) -> Unit
    ) {
        try {
            ZipFile(zipPath.toFile()).use { zip ->
                val spawnEntries = zip.entries().asSequence()
                    .filter { !it.isDirectory }
                    .filter { it.name.contains("spawn_pool_world") && it.name.endsWith(".json") }
                    .toList()
                
                DebugLog.info("    Found ${spawnEntries.size} spawn files in ${zipPath.fileName}")

                var zipCount = 0
                for (entry in spawnEntries) {
                    try {
                        zip.getInputStream(entry).use { stream ->
                            val json = JsonParser.parseReader(InputStreamReader(stream, Charsets.UTF_8))
                            if (json.isJsonObject) {
                                val (added, count) = parseSpawnJson(json.asJsonObject, entry.name, result)
                                counter(added, count)
                                zipCount += count
                            }
                        }
                    } catch (e: Exception) {
                        DebugLog.once("zip-entry-${entry.name}") { "Failed to parse ${entry.name}: ${e.message}" }
                    }
                }
                DebugLog.info("    Parsed $zipCount spawn entries from ${zipPath.fileName}")
            }
        } catch (e: Exception) {
            DebugLog.warn("Failed to read ZIP ${zipPath.fileName}: ${e.message}")
        }
    }

    // --- Utility ---

    fun getModRootPaths(): List<Path> = findAllModRootPaths()

    private fun findAllModRootPaths(): List<Path> {
        cachedModRoots?.let { return it }
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
        } catch (_: ClassNotFoundException) {
            // Expected on NeoForge
        } catch (e: Exception) {
            DebugLog.once("fabric-mod-paths") { "Fabric mod path discovery failed: ${e.message}" }
        }

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
                } catch (e: Exception) {
                    DebugLog.once("neoforge-modfile-${modFileInfo.hashCode()}") { "NeoForge mod file scan failed: ${e.message}" }
                }
            }
        } catch (_: ClassNotFoundException) {
            // Expected on Fabric
        } catch (e: Exception) {
            DebugLog.once("neoforge-mod-paths") { "NeoForge mod path discovery failed: ${e.message}" }
        }

        val result = paths.distinct()
        cachedModRoots = result
        return result
    }

    private fun getClientDatapacksDir(): Path? {
        return try {
            com.cobbledex.platform.PlatformHelper.getGameDir().resolve("datapacks")
        } catch (e: Exception) {
            DebugLog.once("datapacks-dir") { "Failed to resolve datapacks dir: ${e.message}" }
            null
        }
    }

}
