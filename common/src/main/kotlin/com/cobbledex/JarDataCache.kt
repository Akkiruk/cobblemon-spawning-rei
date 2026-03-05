package com.cobbledex

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Self-contained data cache that loads evolution and spawn data from mod JARs
 * using raw JSON parsing (no Cobblemon class deserialization).
 *
 * Loaded once on game startup and kept across server connections.
 * Only replaced when valid server-synced data arrives.
 */
object JarDataCache {

    @Volatile
    private var cachedEvolutions: Map<String, List<EvolutionInfo>> = emptyMap()
    @Volatile
    private var cachedSpawns: Map<String, List<SpawnInfo>> = emptyMap()
    @Volatile
    private var cachedMoves: Map<String, JarMoveData> = emptyMap()

    /** Raw move data parsed from species JSON in mod JARs. */
    data class JarMoveData(
        val levelUp: Map<Int, List<String>>,
        val egg: List<String>,
        val tutor: List<String>,
        val tm: List<String>,
    )

    private val initialized = AtomicBoolean(false)
    private val loading = AtomicBoolean(false)
    private val latch = CountDownLatch(1)

    fun isInitialized(): Boolean = initialized.get()

    fun getCachedEvolutions(): Map<String, List<EvolutionInfo>> = cachedEvolutions
    fun getCachedSpawns(): Map<String, List<SpawnInfo>> = cachedSpawns

    fun hasCachedEvolutions(): Boolean = cachedEvolutions.isNotEmpty()
    fun hasCachedSpawns(): Boolean = cachedSpawns.isNotEmpty()
    fun hasCachedMoves(): Boolean = cachedMoves.isNotEmpty()
    fun getCachedMoves(): Map<String, JarMoveData> = cachedMoves

    /**
     * Wait for the cache to finish initializing (up to timeout).
     * Returns true if cache is ready, false if timed out.
     */
    fun awaitReady(timeoutMs: Long = 30_000): Boolean {
        if (initialized.get()) return true
        return latch.await(timeoutMs, TimeUnit.MILLISECONDS)
    }

    /**
     * Initialize the cache from mod JAR files. Safe to call multiple times —
     * only the first call does work. Runs synchronously on whatever thread calls it.
     */
    fun initialize(modRoots: List<Path>) {
        if (initialized.get()) return
        if (!loading.compareAndSet(false, true)) return

        try {
            DebugLog.info("JarDataCache: initializing from ${modRoots.size} mod roots")
            val startTime = System.currentTimeMillis()

            val presets = loadPresetsFromJars(modRoots)
            DebugLog.info("JarDataCache: loaded ${presets.size} spawn presets")

            cachedSpawns = parseSpawnsFromJars(modRoots, presets)
            val evoAndMoves = parseEvolutionsAndMovesFromJars(modRoots)
            cachedEvolutions = evoAndMoves.first
            cachedMoves = evoAndMoves.second

            val elapsed = System.currentTimeMillis() - startTime
            DebugLog.info("JarDataCache: ready in ${elapsed}ms — " +
                "${cachedSpawns.size} species with spawns, " +
                "${cachedEvolutions.size} species with evolutions, " +
                "${cachedMoves.size} species with moves")

            initialized.set(true)
        } catch (e: Exception) {
            DebugLog.warn("JarDataCache: initialization failed: ${e.message}")
        } finally {
            latch.countDown()
        }
    }

    // ==================== Preset Loading ====================

    private fun loadPresetsFromJars(modRoots: List<Path>): Map<String, JsonObject> {
        val presets = mutableMapOf<String, JsonObject>()

        for (root in modRoots) {
            try {
                val dataDir = root.resolve("data")
                if (!Files.exists(dataDir) || !Files.isDirectory(dataDir)) continue

                Files.list(dataDir).use { namespaces ->
                    namespaces.filter { Files.isDirectory(it) }.forEach { namespace ->
                        val presetDir = namespace.resolve("spawn_detail_presets")
                        if (!Files.exists(presetDir) || !Files.isDirectory(presetDir)) return@forEach

                        Files.walk(presetDir, 5).use { files ->
                            files.filter { it.toString().endsWith(".json") && Files.isRegularFile(it) }.forEach { file ->
                                try {
                                    val name = file.fileName.toString().removeSuffix(".json")
                                    val obj = InputStreamReader(Files.newInputStream(file), Charsets.UTF_8).use { reader ->
                                        JsonParser.parseReader(reader).asJsonObject
                                    }
                                    presets[name] = obj
                                } catch (_: Exception) {}
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // Also scan local datapacks
        try {
            val datapacksDir = com.cobbledex.platform.PlatformHelper.getGameDir().resolve("datapacks")
            if (Files.exists(datapacksDir) && Files.isDirectory(datapacksDir)) {
                Files.list(datapacksDir).use { packs ->
                    packs.filter { Files.isDirectory(it) }.forEach { pack ->
                        val dataDir = pack.resolve("data")
                        if (!Files.exists(dataDir)) return@forEach
                        Files.list(dataDir).use { namespaces ->
                            namespaces.filter { Files.isDirectory(it) }.forEach { namespace ->
                                val presetDir = namespace.resolve("spawn_detail_presets")
                                if (!Files.exists(presetDir) || !Files.isDirectory(presetDir)) return@forEach
                                Files.walk(presetDir, 5).use { files ->
                                    files.filter { it.toString().endsWith(".json") && Files.isRegularFile(it) }.forEach { file ->
                                        try {
                                            val name = file.fileName.toString().removeSuffix(".json")
                                            val obj = InputStreamReader(Files.newInputStream(file), Charsets.UTF_8).use { reader ->
                                                JsonParser.parseReader(reader).asJsonObject
                                            }
                                            presets[name] = obj
                                        } catch (_: Exception) {}
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        return presets
    }

    // ==================== Spawn Parsing ====================

    private fun parseSpawnsFromJars(modRoots: List<Path>, presets: Map<String, JsonObject>): Map<String, List<SpawnInfo>> {
        val result = mutableMapOf<String, MutableList<SpawnInfo>>()
        var fileCount = 0
        var spawnCount = 0
        var failCount = 0

        val sources = mutableListOf<Pair<Path, String>>() // (dataDir, namespace)

        // Collect from mod JARs
        for (root in modRoots) {
            try {
                val dataDir = root.resolve("data")
                if (!Files.exists(dataDir) || !Files.isDirectory(dataDir)) continue
                Files.list(dataDir).use { namespaces ->
                    namespaces.filter { Files.isDirectory(it) }.forEach { ns ->
                        val spawnDir = ns.resolve("spawn_pool_world")
                        if (Files.exists(spawnDir) && Files.isDirectory(spawnDir)) {
                            sources.add(spawnDir to ns.fileName.toString())
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // Collect from local datapacks
        try {
            val datapacksDir = com.cobbledex.platform.PlatformHelper.getGameDir().resolve("datapacks")
            if (Files.exists(datapacksDir) && Files.isDirectory(datapacksDir)) {
                Files.list(datapacksDir).use { packs ->
                    packs.filter { Files.isDirectory(it) }.forEach { pack ->
                        val dataDir = pack.resolve("data")
                        if (!Files.exists(dataDir)) return@forEach
                        Files.list(dataDir).use { namespaces ->
                            namespaces.filter { Files.isDirectory(it) }.forEach { ns ->
                                val spawnDir = ns.resolve("spawn_pool_world")
                                if (Files.exists(spawnDir) && Files.isDirectory(spawnDir)) {
                                    sources.add(spawnDir to ns.fileName.toString())
                                }
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        for ((spawnDir, _) in sources) {
            try {
                Files.walk(spawnDir, 10).use { files ->
                    files.filter { it.toString().endsWith(".json") && Files.isRegularFile(it) }.forEach { file ->
                        try {
                            val obj = InputStreamReader(Files.newInputStream(file), Charsets.UTF_8).use { reader ->
                                JsonParser.parseReader(reader).asJsonObject
                            }
                            if (obj.has("enabled") && !obj.get("enabled").asBoolean) return@forEach

                            val spawns = obj.getAsJsonArray("spawns") ?: return@forEach
                            fileCount++

                            for (spawnElem in spawns) {
                                try {
                                    val spawnObj = spawnElem.asJsonObject
                                    val info = parseSpawnEntry(spawnObj, presets)
                                    if (info != null) {
                                        val species = SpeciesNameNormalizer.normalize(info.pokemon)
                                        result.getOrPut(species) { mutableListOf() }.add(info)
                                        spawnCount++
                                    }
                                } catch (_: Exception) { failCount++ }
                            }
                        } catch (_: Exception) { failCount++ }
                    }
                }
            } catch (_: Exception) {}
        }

        DebugLog.info("JarDataCache: parsed $spawnCount spawns from $fileCount files ($failCount failed)")
        return result
    }

    private fun parseSpawnEntry(spawn: JsonObject, presets: Map<String, JsonObject>): SpawnInfo? {
        val pokemon = spawn.optString("pokemon") ?: return null
        val species = pokemon.split(" ").firstOrNull()?.lowercase() ?: return null

        // Extract form/aspects from the pokemon property string (e.g. "raichu alolan")
        val parts = pokemon.split(" ")
        val formAspects = if (parts.size > 1) parts.drop(1).joinToString(" ").lowercase() else ""

        val bucket = spawn.optString("bucket") ?: "common"
        val levelStr = spawn.optString("level") ?: "1-100"
        val weight = spawn.optFloat("weight") ?: 1.0f
        val context = spawn.optString("spawnablePositionType") ?: spawn.optString("context") ?: spawn.optString("type") ?: "grounded"

        // Load preset names and merge their conditions
        val presetNames = spawn.optStringArray("presets")
        var condition = spawn.optObject("condition")
        var anticondition = spawn.optObject("anticondition")

        for (presetName in presetNames) {
            val preset = presets[presetName]
            if (preset != null) {
                condition = mergeConditions(condition, preset.optObject("condition"))
                anticondition = mergeConditions(anticondition, preset.optObject("anticondition"))
            }
        }

        // Extract spawn condition fields
        val biomes = condition.optStringArray("biomes")
        val structures = condition.optStringArray("structures")
        val dimensions = condition.optStringArray("dimensions")
        val neededNearbyBlocks = condition.optStringArray("neededNearbyBlocks")
        val neededBaseBlocks = condition.optStringArray("neededBaseBlocks")
        val canSeeSky = condition?.optBool("canSeeSky")
        val minLight = condition?.optInt("minLight")
        val maxLight = condition?.optInt("maxLight")
        val minSkyLight = condition?.optInt("minSkyLight")
        val maxSkyLight = condition?.optInt("maxSkyLight")
        val minY = condition?.optInt("minY")
        val maxY = condition?.optInt("maxY")
        val timeRange = condition?.optString("timeRange")
        val isRaining = condition?.optBool("isRaining")
        val isThundering = condition?.optBool("isThundering")
        val moonPhase = condition?.optString("moonPhase")
        val fluid = condition?.optString("fluid")

        // Weight multipliers — handle both plural array and singular object forms
        val weightMults = mutableListOf<WeightMultiplier>()
        spawn.optArray("weightMultipliers")?.forEach { wmElem ->
            try {
                val wm = wmElem.asJsonObject
                val mult = wm.optFloat("multiplier") ?: return@forEach
                val parts = parseWeightMultiplierConditions(wm)
                weightMults.add(WeightMultiplier(multiplier = mult, conditionParts = parts))
            } catch (_: Exception) {}
        }
        // Singular "weightMultiplier" (single object, not array)
        if (weightMults.isEmpty()) {
            spawn.optObject("weightMultiplier")?.let { wm ->
                val mult = wm.optFloat("multiplier")
                if (mult != null) {
                    val parts = parseWeightMultiplierConditions(wm)
                    weightMults.add(WeightMultiplier(multiplier = mult, conditionParts = parts))
                }
            }
        }

        // Fishing lure level
        val minLureLevel = condition?.optInt("minLureLevel")

        // Anti-conditions
        val anti = if (anticondition != null) {
            SpawnAntiCondition(
                biomes = anticondition.optStringArray("biomes"),
                structures = anticondition.optStringArray("structures"),
                neededBaseBlocks = anticondition.optStringArray("neededBaseBlocks"),
                neededNearbyBlocks = anticondition.optStringArray("neededNearbyBlocks"),
                minY = anticondition.optInt("minY"),
                maxY = anticondition.optInt("maxY"),
                timeRange = anticondition.optString("timeRange"),
                dimensions = anticondition.optStringArray("dimensions"),
                isRaining = anticondition.optBool("isRaining"),
                isThundering = anticondition.optBool("isThundering"),
                minLight = anticondition.optInt("minLight"),
                maxLight = anticondition.optInt("maxLight"),
                moonPhase = anticondition.optString("moonPhase")
            )
        } else null

        return SpawnInfo(
            id = spawn.optString("id") ?: species,
            pokemon = species,
            formAspects = formAspects,
            bucket = bucket,
            weight = weight,
            levelRange = levelStr,
            context = context.lowercase(),
            biomes = biomes,
            timeRange = timeRange,
            weather = SpawnWeather(isRaining, isThundering),
            dimensions = dimensions,
            structures = structures,
            canSeeSky = canSeeSky,
            minLight = minLight,
            maxLight = maxLight,
            minSkyLight = minSkyLight,
            maxSkyLight = maxSkyLight,
            minY = minY,
            maxY = maxY,
            neededNearbyBlocks = neededNearbyBlocks,
            neededBaseBlocks = neededBaseBlocks,
            moonPhase = moonPhase,
            presets = presetNames,
            fluid = fluid,
            anticondition = if (anti?.isEmpty == true) null else anti,
            weightMultipliers = weightMults,
            minLureLevel = minLureLevel
        )
    }

    /**
     * Parse conditions from a weight multiplier object. Handles both:
     * - "conditions": [...] (array of condition objects)
     * - "condition": {...} (single condition object)
     */
    private fun parseWeightMultiplierConditions(wm: JsonObject): List<WeightConditionPart> {
        // Try plural array first, then singular object
        val condArray = wm.optArray("conditions")
        if (condArray != null && !condArray.isEmpty) {
            return summarizeWeightConditionsFromJson(condArray)
        }
        val condObj = wm.optObject("condition")
        if (condObj != null) {
            return summarizeWeightConditionFromObject(condObj)
        }
        return listOf(WeightConditionPart(type = "always"))
    }

    private fun summarizeWeightConditionFromObject(cond: JsonObject): List<WeightConditionPart> {
        val parts = mutableListOf<WeightConditionPart>()
        parseConditionFields(cond, parts)
        return if (parts.isEmpty()) listOf(WeightConditionPart(type = "conditional")) else parts
    }

    private fun summarizeWeightConditionsFromJson(conditions: JsonArray?): List<WeightConditionPart> {
        if (conditions == null || conditions.isEmpty) return listOf(WeightConditionPart(type = "always"))
        val parts = mutableListOf<WeightConditionPart>()
        for (condElem in conditions) {
            try {
                val cond = condElem.asJsonObject
                parseConditionFields(cond, parts)
            } catch (_: Exception) {}
        }
        return if (parts.isEmpty()) listOf(WeightConditionPart(type = "conditional")) else parts
    }

    private fun parseConditionFields(cond: JsonObject, parts: MutableList<WeightConditionPart>) {
        cond.optBool("isThundering")?.let { if (it) parts.add(WeightConditionPart(type = "thunderstorm")) }
        cond.optBool("isRaining")?.let { if (it) parts.add(WeightConditionPart(type = "rain")) }

        // timeRange can be a string ("twilight", "night") or an object with ranges
        val timeRangeElem = cond.get("timeRange")
        if (timeRangeElem != null) {
            if (timeRangeElem.isJsonPrimitive) {
                parts.add(WeightConditionPart(type = "time_range", text = timeRangeElem.asString))
            } else if (timeRangeElem.isJsonObject) {
                val ranges = timeRangeElem.asJsonObject.optArray("ranges")
                if (ranges != null && !ranges.isEmpty) {
                    val str = ranges.mapNotNull {
                        if (!it.isJsonArray) return@mapNotNull null
                        val arr = it.asJsonArray
                        if (arr.size() < 2) return@mapNotNull null
                        "${arr[0].asInt}-${arr[1].asInt}"
                    }.joinToString(",")
                    if (str.isNotBlank()) parts.add(WeightConditionPart(type = "time_range", text = str))
                }
            }
        }

        val biomes = cond.optStringArray("biomes")
        if (biomes.isNotEmpty()) {
            parts.add(WeightConditionPart(type = "biomes", ids = biomes))
        }

        cond.optInt("minLureLevel")?.let { parts.add(WeightConditionPart(type = "lure", number = it)) }
    }

    /**
     * Merge two condition JSON objects. Values from [overlay] fill in
     * missing fields from [base]. List fields are combined.
     */
    private fun mergeConditions(base: JsonObject?, overlay: JsonObject?): JsonObject? {
        if (overlay == null) return base
        if (base == null) return overlay.deepCopy()

        val merged = base.deepCopy()
        for ((key, value) in overlay.entrySet()) {
            if (merged.has(key)) {
                // For array fields, combine them
                val existing = merged.get(key)
                if (existing.isJsonArray && value.isJsonArray) {
                    val combined = existing.asJsonArray.deepCopy()
                    for (elem in value.asJsonArray) {
                        val asStr = if (elem.isJsonPrimitive) elem.asString else elem.toString()
                        val alreadyHas = combined.any {
                            val s = if (it.isJsonPrimitive) it.asString else it.toString()
                            s == asStr
                        }
                        if (!alreadyHas) combined.add(elem)
                    }
                    merged.add(key, combined)
                }
                // For non-array, the spawn's value takes priority (don't overwrite)
            } else {
                merged.add(key, value.deepCopy())
            }
        }
        return merged
    }

    // ==================== Evolution Parsing ====================

    private fun parseEvolutionsAndMovesFromJars(modRoots: List<Path>): Pair<Map<String, List<EvolutionInfo>>, Map<String, JarMoveData>> {
        val result = mutableMapOf<String, MutableList<EvolutionInfo>>()
        val movesResult = mutableMapOf<String, JarMoveData>()
        var fileCount = 0
        var failCount = 0
        var baseEvoCount = 0
        var formEvoCount = 0

        for (root in modRoots) {
            try {
                val dataDir = root.resolve("data")
                if (!Files.exists(dataDir) || !Files.isDirectory(dataDir)) continue

                Files.list(dataDir).use { namespaces ->
                    namespaces.filter { Files.isDirectory(it) }.forEach { namespace ->
                        val speciesDir = namespace.resolve("species")
                        if (!Files.exists(speciesDir) || !Files.isDirectory(speciesDir)) return@forEach

                        Files.walk(speciesDir, 10).use { files ->
                            files.filter { it.toString().endsWith(".json") && Files.isRegularFile(it) }.forEach { file ->
                                try {
                                    val obj = InputStreamReader(Files.newInputStream(file), Charsets.UTF_8).use { reader ->
                                        JsonParser.parseReader(reader).asJsonObject
                                    }

                                    val name = obj.optString("name")?.lowercase() ?: return@forEach
                                    fileCount++

                                    // Parse moves
                                    val movesArray = obj.optArray("moves")
                                    if (movesArray != null) {
                                        val levelUp = mutableMapOf<Int, MutableList<String>>()
                                        val egg = mutableListOf<String>()
                                        val tutor = mutableListOf<String>()
                                        val tm = mutableListOf<String>()
                                        for (elem in movesArray) {
                                            try {
                                                val str = elem.asString
                                                val colonIdx = str.indexOf(':')
                                                if (colonIdx < 1) continue
                                                val prefix = str.substring(0, colonIdx)
                                                val moveName = str.substring(colonIdx + 1)
                                                when (prefix) {
                                                    "egg" -> egg.add(moveName)
                                                    "tm" -> tm.add(moveName)
                                                    "tutor" -> tutor.add(moveName)
                                                    else -> prefix.toIntOrNull()?.let { level ->
                                                        levelUp.getOrPut(level) { mutableListOf() }.add(moveName)
                                                    }
                                                }
                                            } catch (_: Exception) {}
                                        }
                                        if (levelUp.isNotEmpty() || egg.isNotEmpty() || tutor.isNotEmpty() || tm.isNotEmpty()) {
                                            movesResult[name] = JarMoveData(levelUp, egg, tutor, tm)
                                        }
                                    }

                                    // Base evolutions
                                    val evolutions = obj.optArray("evolutions")
                                    if (evolutions != null) {
                                        for (evoElem in evolutions) {
                                            try {
                                                val info = parseEvolutionFromJson(name, null, evoElem.asJsonObject)
                                                if (info != null) {
                                                    result.getOrPut(name) { mutableListOf() }.add(info)
                                                    baseEvoCount++
                                                }
                                            } catch (_: Exception) {}
                                        }
                                    }

                                    // Form evolutions
                                    val forms = obj.optArray("forms")
                                    if (forms != null) {
                                        for (formElem in forms) {
                                            try {
                                                val form = formElem.asJsonObject
                                                val formEvos = form.optArray("evolutions") ?: continue
                                                if (formEvos.isEmpty) continue

                                                val aspects = form.optStringArray("aspects").toSet()
                                                val formKey = if (aspects.isEmpty()) name
                                                    else "$name ${aspects.sorted().joinToString(" ")}"

                                                for (evoElem in formEvos) {
                                                    try {
                                                        val info = parseEvolutionFromJson(name, aspects, evoElem.asJsonObject)
                                                        if (info != null) {
                                                            result.getOrPut(formKey) { mutableListOf() }.add(info)
                                                            if (formKey != name) {
                                                                result.getOrPut(name) { mutableListOf() }.add(info)
                                                            }
                                                            formEvoCount++
                                                        }
                                                    } catch (_: Exception) {}
                                                }
                                            } catch (_: Exception) {}
                                        }
                                    }
                                } catch (_: Exception) { failCount++ }
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        DebugLog.info("JarDataCache: parsed $baseEvoCount base + $formEvoCount form evolutions from $fileCount species files ($failCount failed)")
        return Pair(result, movesResult)
    }

    private fun parseEvolutionFromJson(fromSpecies: String, fromAspects: Set<String>?, evo: JsonObject): EvolutionInfo? {
        val id = evo.optString("id") ?: return null
        val resultStr = evo.optString("result") ?: return null

        // Result can be "species" or "species aspect1 aspect2"
        val resultParts = resultStr.split(" ")
        val toSpecies = resultParts.first().lowercase()
        val toAspects = if (resultParts.size > 1) resultParts.drop(1).map { it.lowercase() }.toSet() else emptySet()

        val variant = evo.optString("variant") ?: "level_up"
        val consumeHeldItem = evo.optBool("consumeHeldItem") ?: false

        // Parse required context (item for item_interact, properties for trade, block for block_click)
        val requiredContext = evo.optString("requiredContext")

        // Parse requirements array
        val requirements = mutableListOf<EvolutionRequirement>()
        val reqArray = evo.optArray("requirements")
        if (reqArray != null) {
            for (reqElem in reqArray) {
                try {
                    requirements.add(parseRequirementFromJson(reqElem.asJsonObject))
                } catch (_: Exception) {}
            }
        }

        return EvolutionInfo(
            id = id,
            fromSpecies = fromSpecies,
            fromAspects = fromAspects ?: emptySet(),
            toSpecies = toSpecies,
            toAspects = toAspects,
            variant = variant,
            requirements = requirements,
            requiredContext = requiredContext,
            consumeHeldItem = consumeHeldItem
        )
    }

    private fun parseRequirementFromJson(req: JsonObject): EvolutionRequirement {
        val variant = req.optString("variant") ?: "unknown"
        val data = mutableMapOf<String, Any>()

        when (variant) {
            "level" -> req.optInt("minLevel")?.let { data["minLevel"] = it }
            "friendship" -> req.optInt("amount")?.let { data["amount"] = it }
            "time_range" -> req.optString("range")?.let { data["range"] = it }
            "held_item" -> req.optString("itemCondition")?.let { data["itemCondition"] = it }
            "owner_holds_item" -> req.optString("itemCondition")?.let { data["itemCondition"] = it }
            "has_move_type", "move_type" -> req.optString("type")?.let { data["type"] = it }
            "move_set", "has_move" -> req.optString("move")?.let { data["move"] = it }
            "biome" -> {
                req.optString("biomeCondition")?.let { data["biomeCondition"] = it }
                req.optString("biomeAnticondition")?.let { data["biomeAnticondition"] = it }
            }
            "structure" -> {
                req.optString("structureCondition")?.let { data["structureCondition"] = it }
                req.optString("structureAnticondition")?.let { data["structureAnticondition"] = it }
            }
            "stat_compare" -> {
                req.optString("highStat")?.let { data["highStat"] = it }
                req.optString("lowStat")?.let { data["lowStat"] = it }
            }
            "stat_equal" -> {
                req.optString("statOne")?.let { data["statOne"] = it }
                req.optString("statTwo")?.let { data["statTwo"] = it }
            }
            "pokemon_properties", "properties" -> {
                req.optString("target")?.let { data["target"] = it }
            }
            "property_range" -> {
                req.optString("range")?.let { data["range"] = it }
                req.optString("feature")?.let { data["feature"] = it }
            }
            "blocks_traveled" -> req.optInt("amount")?.let { data["amount"] = it }
            "use_move" -> {
                req.optString("move")?.let { data["move"] = it }
                req.optInt("amount")?.let { data["amount"] = it }
            }
            "defeat" -> {
                req.optString("target")?.let { data["target"] = it }
                req.optInt("amount")?.let { data["amount"] = it }
            }
            "recoil" -> req.optInt("amount")?.let { data["amount"] = it }
            "damage_taken" -> req.optInt("amount")?.let { data["amount"] = it }
            "battle_critical_hits" -> req.optInt("amount")?.let { data["amount"] = it }
            "party_member" -> {
                req.optString("target")?.let { data["target"] = it }
                req.optBool("contains")?.let { data["contains"] = it }
            }
            "moon_phase" -> req.optString("moonPhase")?.let { data["moonPhase"] = it }
            "weather" -> req.optBool("isRaining")?.let { data["isRaining"] = it }
            "advancement" -> req.optString("requiredAdvancement")?.let { data["requiredAdvancement"] = it }
            "world" -> req.optString("identifier")?.let { data["identifier"] = it }
            "attack_defence_ratio" -> req.optString("ratio")?.let { data["ratio"] = it }
            "any" -> {
                val possibilities = req.optArray("possibilities")
                if (possibilities != null && !possibilities.isEmpty) {
                    try {
                        return parseRequirementFromJson(possibilities[0].asJsonObject)
                    } catch (_: Exception) {}
                }
            }
            else -> {
                // Generic: copy all non-variant primitive fields
                for ((key, value) in req.entrySet()) {
                    if (key == "variant") continue
                    if (value.isJsonPrimitive) {
                        val prim = value.asJsonPrimitive
                        when {
                            prim.isNumber -> data[key] = prim.asNumber
                            prim.isBoolean -> data[key] = prim.asBoolean
                            prim.isString -> data[key] = prim.asString
                        }
                    }
                }
            }
        }

        return EvolutionRequirement(variant, data)
    }

    // ==================== JSON Helper Extensions ====================

    private fun JsonObject.optString(key: String): String? {
        val elem = get(key) ?: return null
        if (elem.isJsonPrimitive && elem.asJsonPrimitive.isString) return elem.asString
        if (elem.isJsonPrimitive) return elem.asString
        return null
    }

    private fun JsonObject.optInt(key: String): Int? {
        val elem = get(key) ?: return null
        if (elem.isJsonPrimitive && elem.asJsonPrimitive.isNumber) return elem.asInt
        return null
    }

    private fun JsonObject.optFloat(key: String): Float? {
        val elem = get(key) ?: return null
        if (elem.isJsonPrimitive && elem.asJsonPrimitive.isNumber) return elem.asFloat
        return null
    }

    private fun JsonObject.optBool(key: String): Boolean? {
        val elem = get(key) ?: return null
        if (elem.isJsonPrimitive && elem.asJsonPrimitive.isBoolean) return elem.asBoolean
        return null
    }

    private fun JsonObject.optObject(key: String): JsonObject? {
        val elem = get(key) ?: return null
        return if (elem.isJsonObject) elem.asJsonObject else null
    }

    private fun JsonObject.optArray(key: String): JsonArray? {
        val elem = get(key) ?: return null
        return if (elem.isJsonArray) elem.asJsonArray else null
    }

    private fun JsonObject?.optStringArray(key: String): List<String> {
        if (this == null) return emptyList()
        val arr = optArray(key) ?: return emptyList()
        return arr.mapNotNull { elem ->
            if (elem.isJsonPrimitive) elem.asString else null
        }
    }
}
