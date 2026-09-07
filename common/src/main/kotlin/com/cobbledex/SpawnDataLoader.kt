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
            try {
                when {
                    detail is PokemonSpawnDetail -> {
                        val rawSpecies = detail.pokemon.species?.lowercase() ?: continue
                        // Strip namespace prefix for consistent keying (e.g. "cobblemon:charizard" -> "charizard")
                        val species = if (rawSpecies.contains(':')) rawSpecies.substringAfter(':') else rawSpecies
                        result.getOrPut(species) { mutableListOf() }.add(extractSpawnInfo(detail, species))
                        count++
                    }
                    // `pokemon-herd` details (Cobblemon 1.8.0+). Read reflectively so this jar still
                    // loads on 1.7.x. One SpawnInfo per herd member species.
                    HerdSpawnReader.isHerdDetail(detail) -> {
                        for ((species, info) in extractHerdSpawnInfos(detail)) {
                            result.getOrPut(species) { mutableListOf() }.add(info)
                            count++
                        }
                    }
                }
            } catch (e: Exception) {
                DebugLog.once("spawn-detail-${(detail as? SpawnDetail)?.id}") { "Failed to read spawn detail: ${e.message}" }
            }
        }

        DebugLog.info("Loaded $count spawn entries for ${result.size} species from Cobblemon runtime")
        return result
    }

    // --- SpawnInfo extraction ---

    /**
     * Reads the spawn bucket name off [detail] via reflection rather than a direct property
     * reference, because the field's type changed between Cobblemon versions: pre-1.8.0 it's a
     * `SpawnBucket` object (read its `name`), 1.8.0+ it's a plain `String`. A direct reference
     * compiles against only one of those shapes and throws NoSuchMethodError against the other
     * at runtime, so this stays compatible with both by inspecting the returned value's type.
     */
    private fun extractBucketName(detail: SpawnDetail): String {
        return try {
            val raw = detail.javaClass.getMethod("getBucket").invoke(detail)
            when (raw) {
                null -> "common"
                is String -> raw.ifBlank { "common" }
                else -> (raw.javaClass.getMethod("getName").invoke(raw) as? String)?.ifBlank { "common" } ?: "common"
            }
        } catch (e: Throwable) {
            "common"
        }
    }

    private fun extractSpawnInfo(detail: PokemonSpawnDetail, species: String): SpawnInfo {
        val pokemon = detail.pokemon
        val form = pokemon.form
        val aspects = pokemon.aspects?.joinToString(" ") ?: ""
        val formAspects = when {
            form != null && form.isNotBlank() && !form.equals("Normal", ignoreCase = true) -> form
            aspects.isNotBlank() -> aspects
            else -> ""
        }

        val bucket = extractBucketName(detail)
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
            minLureLevel = minLureLevel,
            conditionWarnings = merged.warnings
        )
    }

    /**
     * Builds one [SpawnInfo] per member of a `pokemon-herd` detail. The shared world conditions
     * (read off the version-stable [SpawnDetail] base) are mapped once into a base entry, then
     * `.copy()`d per member with only the member-specific fields overridden.
     */
    private fun extractHerdSpawnInfos(detail: Any): List<Pair<String, SpawnInfo>> {
        val sd = detail as? SpawnDetail ?: return emptyList()
        val read = HerdSpawnReader.read(detail) ?: return emptyList()

        val merged = mergeConditions(sd.conditions ?: emptyList(), sd.compositeCondition)
        val anti = buildAntiCondition(sd.anticonditions ?: emptyList(), sd.compositeCondition)
        val context = try { sd.spawnablePositionType?.name } catch (_: Throwable) { null } ?: "grounded"

        val base = SpawnInfo(
            id = "", pokemon = "", formAspects = "",
            bucket = extractBucketName(sd), weight = sd.weight,
            levelRange = read.detailLevelRange, context = context,
            biomes = merged.biomes, timeRange = merged.timeRange,
            weather = SpawnWeather(merged.isRaining, merged.isThundering),
            dimensions = merged.dimensions, structures = merged.structures,
            canSeeSky = merged.canSeeSky, minLight = merged.minLight, maxLight = merged.maxLight,
            minSkyLight = merged.minSkyLight, maxSkyLight = merged.maxSkyLight,
            minY = merged.minY, maxY = merged.maxY,
            neededNearbyBlocks = merged.neededNearbyBlocks, neededBaseBlocks = merged.neededBaseBlocks,
            moonPhase = merged.moonPhase, presets = emptyList(), fluid = null,
            anticondition = anti, weightMultipliers = extractWeightMultipliers(sd),
            minLureLevel = null, conditionWarnings = merged.warnings,
        )

        return read.members.map { member ->
            member.species to base.copy(
                id = "${sd.id ?: member.species}#herd:${member.species}",
                pokemon = member.species,
                formAspects = member.formAspects,
                levelRange = member.ownLevelRange ?: read.detailLevelRange,
                herd = HerdContext(member.role, read.maxHerdSize, member.heldItemId),
            )
        }
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
        val moonPhase: String? = null,
        val warnings: List<String> = emptyList()
    )

    private data class ListMerge(
        val values: List<String>,
        val warning: String? = null,
    )

    private data class BooleanMerge(
        val value: Boolean?,
        val warning: String? = null,
    )

    private data class TextMerge(
        val value: String?,
        val warning: String? = null,
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
                WeightMultiplier(
                    multiplier = wm.multiplier,
                    conditionParts = summarizeWeightConditions(wm.conditions ?: emptyList())
                )
            } catch (_: Exception) { null }
        }
    }

    private fun summarizeWeightConditions(conditions: List<SpawningCondition<*>>): List<WeightConditionPart> {
        if (conditions.isEmpty()) return listOf(WeightConditionPart(type = "always"))
        val parts = mutableListOf<WeightConditionPart>()
        for (cond in conditions) {
            cond.isThundering?.let { if (it) parts.add(WeightConditionPart(type = "thunderstorm")) }
            cond.isRaining?.let { if (it) parts.add(WeightConditionPart(type = "rain")) }
            cond.timeRange?.let { tr ->
                val str = tr.ranges.joinToString(",") { "${it.first}-${it.last}" }
                if (str.isNotBlank()) parts.add(WeightConditionPart(type = "time_range", text = str))
            }
            val biomes = cond.biomes?.mapNotNull { extractRegistryId(it) } ?: emptyList()
            if (biomes.isNotEmpty()) {
                parts.add(WeightConditionPart(type = "biomes", ids = biomes))
            }
            if (cond is FishingSpawningCondition) {
                cond.minLureLevel?.let { parts.add(WeightConditionPart(type = "lure", number = it)) }
            }
        }
        return if (parts.isEmpty()) listOf(WeightConditionPart(type = "conditional")) else parts
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
        val biomeResult = combineRestrictedLists("Biome", a.biomes, b.biomes)
        val rainResult = combineNullableBoolean("Rain", a.isRaining, b.isRaining)
        val thunderResult = combineNullableBoolean("Thunder", a.isThundering, b.isThundering)
        val dimensionResult = combineRestrictedLists("Dimension", a.dimensions, b.dimensions)
        val structureResult = combineRestrictedLists("Structure", a.structures, b.structures)
        val skyResult = combineNullableBoolean("Sky visibility", a.canSeeSky, b.canSeeSky)
        val timeRangeResult = combineTextRestrictions("Time", a.timeRange, b.timeRange)
        val moonPhaseResult = combineTextRestrictions("Moon phase", a.moonPhase, b.moonPhase)
        val minLight = combineLowerBound(a.minLight, b.minLight)
        val maxLight = combineUpperBound(a.maxLight, b.maxLight)
        val minSkyLight = combineLowerBound(a.minSkyLight, b.minSkyLight)
        val maxSkyLight = combineUpperBound(a.maxSkyLight, b.maxSkyLight)
        val minY = combineLowerBound(a.minY, b.minY)
        val maxY = combineUpperBound(a.maxY, b.maxY)

        val warnings = buildSet {
            addAll(a.warnings)
            addAll(b.warnings)
            listOfNotNull(
                biomeResult.second,
                rainResult.second,
                thunderResult.second,
                dimensionResult.second,
                structureResult.second,
                skyResult.second,
                timeRangeResult.warning,
                moonPhaseResult.warning,
            ).forEach(::add)
            if (minLight != null && maxLight != null && minLight > maxLight) {
                add("Light level requirements conflict across combined spawn conditions.")
            }
            if (minSkyLight != null && maxSkyLight != null && minSkyLight > maxSkyLight) {
                add("Sky light requirements conflict across combined spawn conditions.")
            }
            if (minY != null && maxY != null && minY > maxY) {
                add("Height requirements conflict across combined spawn conditions.")
            }
        }.toList()

        return ConditionData(
            biomes = biomeResult.first,
            timeRange = timeRangeResult.value,
            isRaining = rainResult.first,
            isThundering = thunderResult.first,
            dimensions = dimensionResult.first,
            structures = structureResult.first,
            canSeeSky = skyResult.first,
            minLight = minLight,
            maxLight = maxLight,
            minSkyLight = minSkyLight,
            maxSkyLight = maxSkyLight,
            minY = minY,
            maxY = maxY,
            neededNearbyBlocks = combineRequiredLists(a.neededNearbyBlocks, b.neededNearbyBlocks),
            neededBaseBlocks = combineRequiredLists(a.neededBaseBlocks, b.neededBaseBlocks),
            moonPhase = moonPhaseResult.value,
            warnings = warnings
        )
    }

    private fun combineRestrictedLists(label: String, a: List<String>, b: List<String>): Pair<List<String>, String?> {
        if (a.isEmpty()) return b to null
        if (b.isEmpty()) return a to null
        val overlap = a.intersect(b.toSet())
        return if (overlap.isNotEmpty()) {
            overlap.toList() to null
        } else {
            emptyList<String>() to "$label requirements conflict across combined spawn conditions: ${a.joinToString(", ")} vs ${b.joinToString(", ")}."
        }
    }

    private fun combineRequiredLists(a: List<String>, b: List<String>): List<String> {
        if (a.isEmpty()) return b
        if (b.isEmpty()) return a
        return (a + b).distinct()
    }

    private fun combineTextRestrictions(label: String, a: String?, b: String?): TextMerge {
        if (a.isNullOrBlank()) return TextMerge(b)
        if (b.isNullOrBlank()) return TextMerge(a)
        if (a == b) return TextMerge(a)
        return TextMerge(
            value = null,
            warning = "$label requirements conflict across combined spawn conditions: $a vs $b."
        )
    }

    private fun combineNullableBoolean(label: String, a: Boolean?, b: Boolean?): Pair<Boolean?, String?> {
        return when {
            a == null -> b to null
            b == null -> a to null
            a == b -> a to null
            else -> null to "$label requirements conflict across combined spawn conditions."
        }
    }

    private fun combineLowerBound(a: Int?, b: Int?): Int? {
        return when {
            a == null -> b
            b == null -> a
            else -> maxOf(a, b)
        }
    }

    private fun combineUpperBound(a: Int?, b: Int?): Int? {
        return when {
            a == null -> b
            b == null -> a
            else -> minOf(a, b)
        }
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

}
