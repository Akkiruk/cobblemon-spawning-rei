package com.cobbledex

import com.cobblemon.mod.common.api.conditional.RegistryLikeCondition
import com.cobblemon.mod.common.api.conditional.RegistryLikeIdentifierCondition
import com.cobblemon.mod.common.api.conditional.RegistryLikeTagCondition
import com.cobblemon.mod.common.api.spawning.CobblemonSpawnPools
import com.cobblemon.mod.common.api.spawning.condition.AreaTypeSpawningCondition
import com.cobblemon.mod.common.api.spawning.condition.FishingSpawningCondition
import com.cobblemon.mod.common.api.spawning.condition.GroundedTypeSpawningCondition
import com.cobblemon.mod.common.api.spawning.condition.SpawningCondition
import com.cobblemon.mod.common.api.spawning.condition.SubmergedTypeSpawningCondition
import com.cobblemon.mod.common.api.spawning.detail.PokemonSpawnDetail
import com.cobblemon.mod.common.api.spawning.detail.SpawnDetail
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.file.Files
import java.nio.file.Path

/**
 * Reads spawn data directly from Cobblemon's runtime spawn pool.
 * All preset resolution, datapack merging, and condition compilation
 * is handled by Cobblemon before we read it.
 */
object SpawnDataLoader {

    @Volatile
    private var cachedModRoots: List<Path>? = null

    fun invalidateCache() {
        cachedModRoots = null
    }

    fun loadFromRuntime(): Map<String, List<SpawnInfo>> {
        val details = try {
            CobblemonSpawnPools.WORLD_SPAWN_POOL.details.toList()
        } catch (e: Exception) {
            DebugLog.warnOnce("spawn-pool-access") { "Failed to read spawn pool: ${e.message}" }
            return emptyMap()
        }

        if (details.isEmpty()) return emptyMap()

        val result = mutableMapOf<String, MutableList<SpawnInfo>>()
        var count = 0

        for (detail in details) {
            if (detail !is PokemonSpawnDetail) continue
            try {
                val species = detail.pokemon.species?.lowercase() ?: continue
                val info = extractSpawnInfo(detail, species)
                result.getOrPut(species) { mutableListOf() }.add(info)
                count++
            } catch (e: Exception) {
                DebugLog.once("spawn-detail-${detail.id}") { "Failed to read spawn detail: ${e.message}" }
            }
        }

        DebugLog.info("Loaded $count spawn entries for ${result.size} species from Cobblemon runtime")
        return result
    }

    // --- SpawnInfo extraction ---

    private fun extractSpawnInfo(detail: PokemonSpawnDetail, species: String): SpawnInfo {
        val pokemon = detail.pokemon
        val form = pokemon.form
        val aspects = pokemon.aspects?.joinToString(" ") ?: ""
        val formAspects = when {
            form != null && form.isNotBlank() && !form.equals("Normal", ignoreCase = true) -> form
            aspects.isNotBlank() -> aspects
            else -> ""
        }

        val bucket = detail.bucket?.name ?: "common"
        val levelRange = detail.levelRange?.let { "${it.first}-${it.last}" } ?: "1-100"
        val context = detail.spawnablePositionType?.name ?: "grounded"

        val conditions = detail.conditions ?: emptyList()
        val anticonditions = detail.anticonditions ?: emptyList()

        val merged = mergeConditions(conditions, detail.compositeCondition)
        val anti = buildAntiCondition(anticonditions, detail.compositeCondition)
        val weightMults = extractWeightMultipliers(detail)
        val minLureLevel = conditions.filterIsInstance<FishingSpawningCondition>().firstOrNull()?.minLureLevel
        val fluid = conditions.filterIsInstance<SubmergedTypeSpawningCondition<*>>().firstOrNull()?.fluid?.let { extractRegistryId(it) }

        return SpawnInfo(
            id = detail.id ?: species,
            pokemon = species,
            formAspects = formAspects,
            bucket = bucket,
            weight = detail.weight,
            levelRange = levelRange,
            context = context,
            biomes = merged.biomes,
            timeRange = merged.timeRange,
            weather = SpawnWeather(merged.isRaining, merged.isThundering),
            dimensions = merged.dimensions,
            structures = merged.structures,
            canSeeSky = merged.canSeeSky,
            minLight = merged.minLight,
            maxLight = merged.maxLight,
            minSkyLight = merged.minSkyLight,
            maxSkyLight = merged.maxSkyLight,
            minY = merged.minY,
            maxY = merged.maxY,
            neededNearbyBlocks = merged.neededNearbyBlocks,
            neededBaseBlocks = merged.neededBaseBlocks,
            moonPhase = merged.moonPhase,
            presets = emptyList(),
            fluid = fluid,
            anticondition = anti,
            weightMultipliers = weightMults,
            minLureLevel = minLureLevel
        )
    }

    // --- Condition extraction ---

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
        val moonPhase: String? = null
    )

    private fun mergeConditions(
        conditions: List<SpawningCondition<*>>,
        composite: com.cobblemon.mod.common.api.spawning.condition.CompositeSpawningCondition?
    ): ConditionData {
        var result = ConditionData()
        for (cond in conditions) {
            result = combineConditionData(result, readCondition(cond))
        }
        if (composite != null) {
            try {
                for (sub in composite.conditions ?: emptyList()) {
                    result = combineConditionData(result, readCondition(sub))
                }
            } catch (_: Exception) {}
        }
        return result
    }

    private fun readCondition(cond: SpawningCondition<*>): ConditionData {
        val biomes = cond.biomes?.mapNotNull { extractRegistryId(it) } ?: emptyList()
        val dimensions = cond.dimensions?.map { it.toString() } ?: emptyList()
        val structures = cond.structures?.mapNotNull { either ->
            try { either.map({ it.toString() }, { "#${it.location}" }) } catch (_: Exception) { null }
        } ?: emptyList()

        val timeRange = cond.timeRange?.ranges?.takeIf { it.isNotEmpty() }
            ?.joinToString(",") { "${it.first}-${it.last}" }
        val moonPhase = cond.moonPhase?.ranges?.takeIf { it.isNotEmpty() }
            ?.joinToString(",") { "${it.first}-${it.last}" }

        var nearbyBlocks = emptyList<String>()
        var baseBlocks = emptyList<String>()
        if (cond is AreaTypeSpawningCondition<*>) {
            nearbyBlocks = cond.neededNearbyBlocks?.mapNotNull { extractRegistryId(it) } ?: emptyList()
        }
        if (cond is GroundedTypeSpawningCondition<*>) {
            baseBlocks = cond.neededBaseBlocks?.mapNotNull { extractRegistryId(it) } ?: emptyList()
        }

        return ConditionData(
            biomes = biomes,
            timeRange = timeRange,
            isRaining = cond.isRaining,
            isThundering = cond.isThundering,
            dimensions = dimensions,
            structures = structures,
            canSeeSky = cond.canSeeSky,
            minLight = cond.minLight,
            maxLight = cond.maxLight,
            minSkyLight = cond.minSkyLight,
            maxSkyLight = cond.maxSkyLight,
            minY = cond.minY?.toInt(),
            maxY = cond.maxY?.toInt(),
            neededNearbyBlocks = nearbyBlocks,
            neededBaseBlocks = baseBlocks,
            moonPhase = moonPhase
        )
    }

    // --- Anti-conditions ---

    private fun buildAntiCondition(
        anticonditions: List<SpawningCondition<*>>,
        composite: com.cobblemon.mod.common.api.spawning.condition.CompositeSpawningCondition?
    ): SpawnAntiCondition? {
        val allAnti = anticonditions.toMutableList()
        try { composite?.anticonditions?.let { allAnti.addAll(it) } } catch (_: Exception) {}
        if (allAnti.isEmpty()) return null

        val allBiomes = mutableListOf<String>()
        val allStructures = mutableListOf<String>()
        val allBaseBlocks = mutableListOf<String>()
        val allNearbyBlocks = mutableListOf<String>()
        val allDimensions = mutableListOf<String>()
        var minY: Int? = null; var maxY: Int? = null
        var timeRange: String? = null
        var isRaining: Boolean? = null; var isThundering: Boolean? = null
        var minLight: Int? = null; var maxLight: Int? = null
        var moonPhase: String? = null

        for (cond in allAnti) {
            val data = readCondition(cond)
            allBiomes.addAll(data.biomes)
            allStructures.addAll(data.structures)
            allDimensions.addAll(data.dimensions)
            allNearbyBlocks.addAll(data.neededNearbyBlocks)
            allBaseBlocks.addAll(data.neededBaseBlocks)
            if (data.minY != null) minY = minY?.let { minOf(it, data.minY) } ?: data.minY
            if (data.maxY != null) maxY = maxY?.let { maxOf(it, data.maxY) } ?: data.maxY
            timeRange = timeRange ?: data.timeRange
            isRaining = isRaining ?: data.isRaining
            isThundering = isThundering ?: data.isThundering
            minLight = minLight ?: data.minLight
            maxLight = maxLight ?: data.maxLight
            moonPhase = moonPhase ?: data.moonPhase
        }

        val anti = SpawnAntiCondition(
            biomes = allBiomes.distinct(), structures = allStructures.distinct(),
            neededBaseBlocks = allBaseBlocks.distinct(), neededNearbyBlocks = allNearbyBlocks.distinct(),
            minY = minY, maxY = maxY, timeRange = timeRange, dimensions = allDimensions.distinct(),
            isRaining = isRaining, isThundering = isThundering,
            minLight = minLight, maxLight = maxLight, moonPhase = moonPhase
        )
        return if (anti.isEmpty) null else anti
    }

    // --- Weight multipliers ---

    private fun extractWeightMultipliers(detail: SpawnDetail): List<WeightMultiplier> {
        val mults = detail.weightMultipliers ?: return emptyList()
        return mults.mapNotNull { wm ->
            try {
                WeightMultiplier(wm.multiplier, summarizeWeightConditions(wm.conditions ?: emptyList()))
            } catch (_: Exception) { null }
        }
    }

    private fun summarizeWeightConditions(conditions: List<SpawningCondition<*>>): String {
        if (conditions.isEmpty()) return tr("cobbledex-rei-emi-jei.weight.always")
        val parts = mutableListOf<String>()
        for (cond in conditions) {
            cond.isThundering?.let { if (it) parts.add(tr("cobbledex-rei-emi-jei.weight.thunderstorm")) }
            cond.isRaining?.let { if (it) parts.add(tr("cobbledex-rei-emi-jei.weight.rain")) }
            cond.timeRange?.let { tr ->
                val str = tr.ranges.joinToString(",") { "${it.first}-${it.last}" }
                if (str.isNotBlank()) parts.add(str)
            }
            val biomes = cond.biomes?.mapNotNull { extractRegistryId(it) } ?: emptyList()
            if (biomes.isNotEmpty()) {
                val names = biomes.map { formatId(it) }
                if (names.size <= 3) parts.add(names.joinToString(", "))
                else parts.add("${names.take(2).joinToString(", ")} " + tr("cobbledex-rei-emi-jei.weight.and_more", names.size - 2))
            }
            if (cond is FishingSpawningCondition) {
                cond.minLureLevel?.let { parts.add(tr("cobbledex-rei-emi-jei.weight.lure", it)) }
            }
        }
        return if (parts.isEmpty()) tr("cobbledex-rei-emi-jei.weight.conditional") else parts.joinToString(", ")
    }

    // --- Registry ID helpers ---

    private fun <T> extractRegistryId(condition: RegistryLikeCondition<T>): String? {
        return when (condition) {
            is RegistryLikeIdentifierCondition<*> -> condition.identifier.toString()
            is RegistryLikeTagCondition<*> -> "#${condition.tag.location}"
            else -> null
        }
    }

    // --- Condition merge helpers ---

    private fun combineConditionData(a: ConditionData, b: ConditionData): ConditionData {
        return ConditionData(
            biomes = combineLists(a.biomes, b.biomes),
            timeRange = a.timeRange ?: b.timeRange,
            isRaining = a.isRaining ?: b.isRaining,
            isThundering = a.isThundering ?: b.isThundering,
            dimensions = combineLists(a.dimensions, b.dimensions),
            structures = combineLists(a.structures, b.structures),
            canSeeSky = a.canSeeSky ?: b.canSeeSky,
            minLight = a.minLight ?: b.minLight,
            maxLight = a.maxLight ?: b.maxLight,
            minSkyLight = a.minSkyLight ?: b.minSkyLight,
            maxSkyLight = a.maxSkyLight ?: b.maxSkyLight,
            minY = a.minY ?: b.minY,
            maxY = a.maxY ?: b.maxY,
            neededNearbyBlocks = combineLists(a.neededNearbyBlocks, b.neededNearbyBlocks),
            neededBaseBlocks = combineLists(a.neededBaseBlocks, b.neededBaseBlocks),
            moonPhase = a.moonPhase ?: b.moonPhase
        )
    }

    private fun combineLists(a: List<String>, b: List<String>): List<String> {
        if (a.isEmpty()) return b
        if (b.isEmpty()) return a
        return (a + b).distinct()
    }

    // --- Mod root paths (kept for ObtainmentDataLoader) ---

    fun getModRootPaths(): List<Path> = findAllModRootPaths()

    internal fun findAllModRootPaths(): List<Path> {
        cachedModRoots?.let { return it }
        val paths = mutableListOf<Path>()

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
        } catch (e: Exception) {
            DebugLog.once("fabric-mod-paths") { "Fabric mod path discovery failed: ${e.message}" }
        }

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
        } catch (e: Exception) {
            DebugLog.once("neoforge-mod-paths") { "NeoForge mod path discovery failed: ${e.message}" }
        }

        val result = paths.distinct()
        cachedModRoots = result
        return result
    }

    // --- File-based spawn loading (for LAN/dedicated server clients where runtime pool is empty) ---

    fun loadFromModFiles(): Map<String, List<SpawnInfo>> {
        val modRoots = findAllModRootPaths()
        if (modRoots.isEmpty()) {
            DebugLog.warn("No mod roots found for file-based spawn loading")
            return emptyMap()
        }

        val presets = loadPresetsFromFiles(modRoots)
        val result = mutableMapOf<String, MutableList<SpawnInfo>>()
        var count = 0

        for (root in modRoots) {
            val dataDir = root.resolve("data")
            if (!Files.exists(dataDir) || !Files.isDirectory(dataDir)) continue

            try {
                Files.list(dataDir).use { namespaces ->
                    for (ns in namespaces) {
                        if (!Files.isDirectory(ns)) continue
                        val poolDir = ns.resolve("spawn_pool_world")
                        if (!Files.isDirectory(poolDir)) continue

                        Files.list(poolDir).use { files ->
                            for (file in files) {
                                if (!file.toString().endsWith(".json")) continue
                                try {
                                    val parsed = parseSpawnSetJson(Files.readString(file), presets)
                                    for ((species, infos) in parsed) {
                                        result.getOrPut(species) { mutableListOf() }.addAll(infos)
                                        count += infos.size
                                    }
                                } catch (e: Exception) {
                                    DebugLog.once("spawn-file-${file.fileName}") {
                                        "Parse failed ${file.fileName}: ${e.message}"
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                DebugLog.once("spawn-dir-scan") { "Data dir scan failed: ${e.message}" }
            }
        }

        DebugLog.info("File-based: $count spawn entries for ${result.size} species from mod files")
        return result
    }

    private fun loadPresetsFromFiles(modRoots: List<Path>): Map<String, JsonObject> {
        val presets = mutableMapOf<String, JsonObject>()
        for (root in modRoots) {
            val dataDir = root.resolve("data")
            if (!Files.exists(dataDir)) continue
            try {
                Files.list(dataDir).use { nsList ->
                    for (ns in nsList) {
                        if (!Files.isDirectory(ns)) continue
                        val presetsDir = ns.resolve("spawn_detail_presets")
                        if (!Files.isDirectory(presetsDir)) continue
                        Files.list(presetsDir).use { files ->
                            for (file in files) {
                                val name = file.fileName.toString()
                                if (!name.endsWith(".json")) continue
                                try {
                                    presets[name.removeSuffix(".json")] =
                                        JsonParser.parseString(Files.readString(file)).asJsonObject
                                } catch (_: Exception) {}
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        return presets
    }

    private fun parseSpawnSetJson(json: String, presets: Map<String, JsonObject>): Map<String, List<SpawnInfo>> {
        val root = JsonParser.parseString(json).asJsonObject
        if (root.has("enabled") && !root["enabled"].asBoolean) return emptyMap()
        val spawns = root.getAsJsonArray("spawns") ?: return emptyMap()
        val result = mutableMapOf<String, MutableList<SpawnInfo>>()

        for (element in spawns) {
            try {
                val obj = element.asJsonObject
                val type = obj.get("type")?.asString ?: "pokemon"
                if (type != "pokemon") continue
                val pokemon = obj.get("pokemon")?.asString?.lowercase() ?: continue
                val info = parseSpawnEntryJson(obj, pokemon, presets)
                result.getOrPut(pokemon) { mutableListOf() }.add(info)
            } catch (_: Exception) {}
        }
        return result
    }

    private fun parseSpawnEntryJson(obj: JsonObject, pokemon: String, presets: Map<String, JsonObject>): SpawnInfo {
        val id = obj.get("id")?.asString ?: pokemon
        val form = obj.get("form")?.asString
        val aspects = obj.getAsJsonArray("aspects")?.joinToString(" ") { it.asString } ?: ""
        val formAspects = when {
            form != null && form.isNotBlank() && !form.equals("Normal", ignoreCase = true) -> form
            aspects.isNotBlank() -> aspects
            else -> ""
        }

        val bucket = obj.get("bucket")?.asString ?: "common"
        val level = obj.get("level")?.asString ?: "1-100"
        val context = obj.get("spawnablePositionType")?.asString ?: "grounded"
        val weight = obj.get("weight")?.asFloat ?: 1.0f

        val presetNames = obj.getAsJsonArray("presets")?.map { it.asString } ?: emptyList()
        val condition = obj.getAsJsonObject("condition") ?: JsonObject()

        // Preset provides defaults, spawn entry overrides
        val mergedCond = JsonObject()
        for (name in presetNames) {
            val pc = presets[name]?.getAsJsonObject("condition") ?: continue
            for ((k, v) in pc.entrySet()) if (!mergedCond.has(k)) mergedCond.add(k, v)
        }
        for ((k, v) in condition.entrySet()) mergedCond.add(k, v)

        val mergedAnti = JsonObject()
        for (name in presetNames) {
            val pa = presets[name]?.getAsJsonObject("anticondition") ?: continue
            for ((k, v) in pa.entrySet()) if (!mergedAnti.has(k)) mergedAnti.add(k, v)
        }
        obj.getAsJsonObject("anticondition")?.let { sa ->
            for ((k, v) in sa.entrySet()) mergedAnti.add(k, v)
        }

        val anti = if (mergedAnti.size() > 0) {
            SpawnAntiCondition(
                biomes = jsonStrings(mergedAnti, "biomes"),
                structures = jsonStrings(mergedAnti, "structures"),
                neededBaseBlocks = jsonStrings(mergedAnti, "neededBaseBlocks"),
                neededNearbyBlocks = jsonStrings(mergedAnti, "neededNearbyBlocks"),
                minY = mergedAnti.get("minY")?.asInt,
                maxY = mergedAnti.get("maxY")?.asInt,
                timeRange = mergedAnti.get("timeRange")?.let(::parseJsonRange),
                dimensions = jsonStrings(mergedAnti, "dimensions"),
                isRaining = mergedAnti.get("isRaining")?.asBoolean,
                isThundering = mergedAnti.get("isThundering")?.asBoolean,
                minLight = mergedAnti.get("minLight")?.asInt,
                maxLight = mergedAnti.get("maxLight")?.asInt,
                moonPhase = mergedAnti.get("moonPhase")?.let(::parseJsonRange)
            ).takeUnless { it.isEmpty }
        } else null

        // Handle both weightMultiplier (singular object) and weightMultipliers (array)
        val weightMults = mutableListOf<WeightMultiplier>()
        obj.getAsJsonObject("weightMultiplier")?.let { parseWeightMultJson(it)?.let(weightMults::add) }
        obj.getAsJsonArray("weightMultipliers")?.forEach { parseWeightMultJson(it.asJsonObject)?.let(weightMults::add) }

        val minLureLevel = if (context == "fishing") mergedCond.get("minLureLevel")?.asInt else null

        return SpawnInfo(
            id = id, pokemon = pokemon, formAspects = formAspects,
            bucket = bucket, weight = weight, levelRange = level, context = context,
            biomes = jsonStrings(mergedCond, "biomes"),
            timeRange = mergedCond.get("timeRange")?.let(::parseJsonRange),
            weather = SpawnWeather(mergedCond.get("isRaining")?.asBoolean, mergedCond.get("isThundering")?.asBoolean),
            dimensions = jsonStrings(mergedCond, "dimensions"),
            structures = jsonStrings(mergedCond, "structures"),
            canSeeSky = mergedCond.get("canSeeSky")?.asBoolean,
            minLight = mergedCond.get("minLight")?.asInt,
            maxLight = mergedCond.get("maxLight")?.asInt,
            minSkyLight = mergedCond.get("minSkyLight")?.asInt,
            maxSkyLight = mergedCond.get("maxSkyLight")?.asInt,
            minY = mergedCond.get("minY")?.asInt, maxY = mergedCond.get("maxY")?.asInt,
            neededNearbyBlocks = jsonStrings(mergedCond, "neededNearbyBlocks"),
            neededBaseBlocks = jsonStrings(mergedCond, "neededBaseBlocks"),
            moonPhase = mergedCond.get("moonPhase")?.let(::parseJsonRange),
            presets = presetNames,
            fluid = mergedCond.get("fluid")?.asString,
            anticondition = anti, weightMultipliers = weightMults,
            minLureLevel = minLureLevel
        )
    }

    private fun jsonStrings(obj: JsonObject, key: String): List<String> =
        obj.getAsJsonArray(key)?.map { it.asString } ?: emptyList()

    private fun parseJsonRange(element: JsonElement): String? {
        if (element.isJsonPrimitive) return element.asString
        if (element.isJsonObject) {
            val ranges = element.asJsonObject.getAsJsonArray("ranges") ?: return null
            return buildString {
                var first = true
                for (r in ranges) {
                    if (!first) append(",")
                    first = false
                    if (r.isJsonArray) {
                        val arr = r.asJsonArray
                        append("${arr[0].asInt}-${arr[1].asInt}")
                    } else append(r.asString)
                }
            }
        }
        return null
    }

    private fun parseWeightMultJson(wm: JsonObject): WeightMultiplier? {
        val mult = wm.get("multiplier")?.asFloat ?: return null
        val cond = wm.getAsJsonObject("condition")
        val summary = if (cond != null) {
            val parts = mutableListOf<String>()
            cond.get("isThundering")?.let { if (it.asBoolean) parts.add(tr("cobbledex-rei-emi-jei.weight.thunderstorm")) }
            cond.get("isRaining")?.let { if (it.asBoolean) parts.add(tr("cobbledex-rei-emi-jei.weight.rain")) }
            cond.get("timeRange")?.let { parseJsonRange(it)?.let { r -> parts.add(r) } }
            val biomes = jsonStrings(cond, "biomes")
            if (biomes.isNotEmpty()) {
                val names = biomes.map { formatId(it) }
                if (names.size <= 3) parts.add(names.joinToString(", "))
                else parts.add("${names.take(2).joinToString(", ")} " + tr("cobbledex-rei-emi-jei.weight.and_more", names.size - 2))
            }
            cond.get("minLureLevel")?.let { parts.add(tr("cobbledex-rei-emi-jei.weight.lure", it.asInt)) }
            if (parts.isEmpty()) tr("cobbledex-rei-emi-jei.weight.conditional") else parts.joinToString(", ")
        } else tr("cobbledex-rei-emi-jei.weight.always")
        return WeightMultiplier(mult, summary)
    }

}
