package com.cobbledex

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.server.packs.resources.ResourceManager
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile
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
    private var cachedHabitatSpawns: Map<String, List<SpawnInfo>> = emptyMap()
    @Volatile
    private var cachedTms: Map<String, TmInfo> = emptyMap()
    @Volatile
    private var cachedMarks: List<MarkInfo> = emptyList()
    @Volatile
    private var cachedMoves: Map<String, JarMoveData> = emptyMap()
    @Volatile
    private var cachedFormMoves: Map<String, JarMoveData> = emptyMap()
    @Volatile
    private var cachedFossils: Map<String, List<FossilCombo>> = emptyMap()
    @Volatile
    private var cachedTraits: Map<String, JarTraitData> = emptyMap()
    @Volatile
    private var cachedFormTraits: Map<String, JarTraitData> = emptyMap()
    @Volatile
    private var cachedTypeChartOverrides: Map<String, Map<String, Float>> = emptyMap()
    @Volatile
    private var cachedSpeciesProvenance: Map<String, String> = emptyMap()
    @Volatile
    private var cachedMegaStones: Map<Pair<String, String>, String> = emptyMap()

    /** Raw move data parsed from species JSON in mod JARs. */
    data class JarMoveData(
        val levelUp: Map<Int, List<String>>,
        val egg: List<String>,
        val tutor: List<String>,
        val tm: List<String>,
    )

    /**
     * The species fields Cobblemon's network sync does not carry.
     *
     * Verified against Cobblemon 1.7's `Species.encode` / `FormData.encode`: neither writes
     * `catchRate`, `eggGroups`, `eggCycles`, `baseFriendship`, `evYield`, `baseExperienceYield` or
     * `labels`, so a client connected to a dedicated server has none of them. Parsed here from the
     * same species JSON so [SpeciesTraitMerger] can fill each gap individually.
     */
    data class JarTraitData(
        val catchRate: Int? = null,
        val eggGroups: List<String>? = null,
        val eggCycles: Int? = null,
        val baseFriendship: Int? = null,
        val baseExperienceYield: Int? = null,
        val evYield: Map<String, Int>? = null,
        val labels: Set<String>? = null,
    ) {
        val isEmpty: Boolean
            get() = catchRate == null && eggGroups == null && eggCycles == null &&
                baseFriendship == null && baseExperienceYield == null && evYield == null &&
                labels == null
    }

    private val initialized = AtomicBoolean(false)
    private val loading = AtomicBoolean(false)
    private val latch = CountDownLatch(1)

    @Volatile
    private var lateReadyPending = false

    fun isInitialized(): Boolean = initialized.get()

    /**
     * True at most once per occurrence: this cache finished its background scan *after*
     * [SpawnDataIndex.doLoad] had already given up waiting on [awaitReady]'s bounded 5s timeout and
     * built its snapshot without it. Cobblemon's own registries won't change again on their own to
     * trigger [CobblemonDataSignal]'s retry, so every field this cache supplies - species
     * provenance chief among them - would otherwise stay stuck on its pre-cache fallback (e.g.
     * every species reporting its bare namespace, "cobblemon", as its "Added by" source) for the
     * rest of the session on any modpack big enough that the scan genuinely outlasts the timeout.
     * Consumed like [CobblemonDataSignal.consumeChange] so [CobbleDexMod.tickClient] can trigger
     * exactly one catch-up rebuild.
     */
    fun consumeLateReady(): Boolean {
        if (!lateReadyPending) return false
        lateReadyPending = false
        return true
    }

    fun getCachedEvolutions(): Map<String, List<EvolutionInfo>> = cachedEvolutions
    fun getCachedSpawns(): Map<String, List<SpawnInfo>> = cachedSpawns
    fun getCachedHabitatSpawns(): Map<String, List<SpawnInfo>> = cachedHabitatSpawns
    fun getCachedTms(): Map<String, TmInfo> = cachedTms
    fun getCachedMarks(): List<MarkInfo> = cachedMarks
    fun hasCachedMarks(): Boolean = cachedMarks.isNotEmpty()

    fun hasCachedEvolutions(): Boolean = cachedEvolutions.isNotEmpty()
    fun hasCachedSpawns(): Boolean = cachedSpawns.isNotEmpty()
    fun hasCachedHabitatSpawns(): Boolean = cachedHabitatSpawns.isNotEmpty()
    fun hasCachedTms(): Boolean = cachedTms.isNotEmpty()
    fun hasCachedMoves(): Boolean = cachedMoves.isNotEmpty()
    fun getCachedMoves(): Map<String, JarMoveData> = cachedMoves
    fun hasCachedFormMoves(): Boolean = cachedFormMoves.isNotEmpty()
    fun getCachedFormMoves(): Map<String, JarMoveData> = cachedFormMoves
    fun hasCachedFossils(): Boolean = cachedFossils.isNotEmpty()
    fun getCachedFossils(): Map<String, List<FossilCombo>> = cachedFossils
    fun hasCachedTraits(): Boolean = cachedTraits.isNotEmpty() || cachedFormTraits.isNotEmpty()
    fun getCachedTraits(): Map<String, JarTraitData> = cachedTraits
    fun getCachedFormTraits(): Map<String, JarTraitData> = cachedFormTraits
    fun getCachedTypeChartOverrides(): Map<String, Map<String, Float>> = cachedTypeChartOverrides
    /** Species name -> the mod id or datapack/resourcepack name that declared it. See [SpeciesJsonScan.provenance]. */
    fun getCachedSpeciesProvenance(): Map<String, String> = cachedSpeciesProvenance
    /** (normalized species, aspect e.g. "mega_x") -> the item id that lets it Mega Evolve. See [parseMegaStonesFromJars]. */
    fun getCachedMegaStones(): Map<Pair<String, String>, String> = cachedMegaStones

    /**
     * Wait for the cache to finish initializing (up to timeout).
     * Returns true if cache is ready, false if timed out.
     */
    fun awaitReady(timeoutMs: Long = 30_000): Boolean {
        if (initialized.get()) return true
        return latch.await(timeoutMs, TimeUnit.MILLISECONDS)
    }

    /**
     * Initialize the cache from mod JAR files. Safe to call multiple times -
     * only the first call does work. Runs synchronously on whatever thread calls it.
     */
    fun initialize(modRootsWithIds: List<SpawnDataLoader.ModRoot>) {
        if (initialized.get()) return
        if (!loading.compareAndSet(false, true)) return

        try {
            val modRoots = modRootsWithIds.map { it.path }
            DebugLog.info("JarDataCache: initializing from ${modRoots.size} mod roots")
            val startTime = System.currentTimeMillis()

            val presets = loadPresetsFromJars(modRoots)
            DebugLog.info("JarDataCache: loaded ${presets.size} spawn presets")

            cachedSpawns = parseSpawnsFromJars(modRoots, presets)
            cachedHabitatSpawns = parseHabitatPoolsFromJars(modRoots)
            cachedTms = parseTmsFromJars(modRoots)
            cachedMarks = parseMarksFromJars(modRoots)
            val scan = parseEvolutionsAndMovesFromJars(modRootsWithIds)
            cachedEvolutions = scan.evolutions
            cachedMoves = scan.moves
            cachedFormMoves = scan.formMoves
            cachedTraits = scan.traits
            cachedFormTraits = scan.formTraits
            cachedSpeciesProvenance = scan.provenance
            cachedFossils = parseFossilsFromJars(modRoots)
            cachedTypeChartOverrides = parseTypeChartOverridesFromJars(modRoots)
            cachedMegaStones = parseMegaStonesFromJars(modRoots)

            val elapsed = System.currentTimeMillis() - startTime
            DebugLog.info("JarDataCache: ready in ${elapsed}ms - " +
                "${cachedSpawns.size} species with spawns, " +
                "${cachedHabitatSpawns.size} species with habitat spawns, " +
                "${cachedEvolutions.size} species with evolutions, " +
                "${cachedMoves.size} species with moves, " +
                "${cachedTraits.size} species with breeding/dex traits, " +
                "${cachedFossils.values.sumOf { it.size }} fossils for ${cachedFossils.size} species, " +
                "${cachedTypeChartOverrides.size} type chart overrides")

            initialized.set(true)
            if (SpawnDataIndex.loadState != SpawnDataIndex.LoadState.NOT_LOADED) {
                lateReadyPending = true
            }
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

        // Also scan local datapacks (directories)
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

                // Scan ZIP datapacks for presets
                scanZipDatapacks(datapacksDir, "spawn_detail_presets") { _, _, entryName, json ->
                    val name = entryName.substringAfterLast('/').removeSuffix(".json")
                    presets[name] = json
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

        // Collect from local datapacks (directories)
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

        // Collect spawns from ZIP datapacks
        try {
            val datapacksDir = com.cobbledex.platform.PlatformHelper.getGameDir().resolve("datapacks")
            if (Files.exists(datapacksDir) && Files.isDirectory(datapacksDir)) {
                scanZipDatapacks(datapacksDir, "spawn_pool_world") { _, _, _, json ->
                    if (json.has("enabled") && !json.get("enabled").asBoolean) return@scanZipDatapacks
                    val spawns = json.getAsJsonArray("spawns") ?: return@scanZipDatapacks
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

        // Weight multipliers - handle both plural array and singular object forms
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

    // ==================== Habitat Pool Parsing ====================

    /**
     * Habitat pools (`data/<ns>/habitat_pools/`, Cobblemon 1.8.0+). Cobblemon's [HabitatPools]
     * registry has an empty `sync()`, so - exactly like `spawn_pool_world` - the client only has these
     * from its own jars / datapacks. Each file is one habitat: `{ name, type, spawns: [ { species,
     * bucket, spawnablePositionType, weight, levelRange, phases, minLight, maxLight } ] }`. `phases`
     * (e.g. `"1-3, 5"`) selects which of the habitat's day-cycle phases the species appears in.
     */
    private fun parseHabitatPoolsFromJars(modRoots: List<Path>): Map<String, List<SpawnInfo>> {
        val result = mutableMapOf<String, MutableList<SpawnInfo>>()
        var poolCount = 0
        var spawnCount = 0

        fun ingest(json: JsonObject) {
            val nameKey = json.optString("name") ?: return
            val spawns = json.optArray("spawns") ?: return
            val spawnObjs = spawns.mapNotNull { if (it.isJsonObject) it.asJsonObject else null }
            val totalPhases = spawnObjs
                .flatMap { parsePhaseSet(it.optString("phases")) }
                .toSet().size.coerceAtLeast(1)
            poolCount++
            for (spawnObj in spawnObjs) {
                val info = parseHabitatSpawnEntry(spawnObj, nameKey, totalPhases) ?: continue
                result.getOrPut(SpeciesNameNormalizer.normalize(info.pokemon)) { mutableListOf() }.add(info)
                spawnCount++
            }
        }

        forEachDataFile(modRoots, "habitat_pools") { json -> try { ingest(json) } catch (_: Exception) {} }

        DebugLog.info("JarDataCache: parsed $spawnCount habitat spawns from $poolCount pools")
        return result
    }

    private fun parseHabitatSpawnEntry(spawn: JsonObject, habitatNameKey: String, totalPhases: Int): SpawnInfo? {
        val rawSpecies = spawn.optString("species") ?: return null
        val species = rawSpecies.split(" ").firstOrNull()?.lowercase()?.substringAfter(':') ?: return null
        val formAspects = rawSpecies.split(" ").drop(1).joinToString(" ").lowercase()

        val phaseSpec = spawn.optString("phases")?.trim().takeUnless { it.isNullOrBlank() }
        val phaseSet = parsePhaseSet(phaseSpec)

        return SpawnInfo.minimal(
            id = "habitat/${habitatNameKey.substringAfterLast('.')}/$species",
            pokemon = species,
            formAspects = formAspects,
            bucket = spawn.optString("bucket") ?: "common",
            weight = spawn.optFloat("weight") ?: 1f,
            levelRange = spawn.optString("levelRange") ?: spawn.optString("level") ?: "1-100",
            context = (spawn.optString("spawnablePositionType") ?: spawn.optString("context") ?: "grounded").lowercase(),
            minLight = spawn.optInt("minLight"),
            maxLight = spawn.optInt("maxLight"),
            habitat = HabitatContext(
                habitatNameKey = habitatNameKey,
                phases = phaseSpec ?: "",
                phaseCount = phaseSet.size.coerceAtLeast(if (phaseSpec == null) totalPhases else 1),
                totalPhases = totalPhases,
            ),
        )
    }

    /** Expands a phase spec like `"1, 3-5"` into `{1, 3, 4, 5}`. Blank / null → empty. */
    internal fun parsePhaseSet(spec: String?): Set<Int> {
        if (spec.isNullOrBlank()) return emptySet()
        val out = sortedSetOf<Int>()
        for (part in spec.split(',')) {
            val t = part.trim()
            if (t.isEmpty()) continue
            val dash = t.indexOf('-', startIndex = if (t.startsWith('-')) 1 else 0)
            if (dash > 0) {
                val lo = t.substring(0, dash).trim().toIntOrNull()
                val hi = t.substring(dash + 1).trim().toIntOrNull()
                if (lo != null && hi != null && lo <= hi) for (i in lo..hi) out.add(i)
            } else {
                t.toIntOrNull()?.let { out.add(it) }
            }
        }
        return out
    }

    // ==================== Native TM Parsing ====================

    /**
     * Cobblemon's native TM registry (`data/<ns>/tms/`, 1.8.0+). Fallback for when the client-synced
     * `TechnicalMachines` registry ([NativeTmDataLoader]) isn't available. Shape per file:
     * `{ moveName, type, obtainMethods: [{variant}], recipe: [{ item | tag, count }] }`.
     */
    private fun parseTmsFromJars(modRoots: List<Path>): Map<String, TmInfo> {
        val result = LinkedHashMap<String, TmInfo>()
        forEachDataFile(modRoots, "tms") { json ->
            try {
                parseTmObject(json)?.let { result[it.moveName] = it }
            } catch (_: Exception) {}
        }
        DebugLog.info("JarDataCache: parsed ${result.size} native TMs")
        return result
    }

    // ==================== Marks Parsing ====================

    /** Cobblemon Mark registry (`data/<ns>/marks/`, 1.8.0+). Fallback for [MarkDataLoader]. */
    private fun parseMarksFromJars(modRoots: List<Path>): List<MarkInfo> {
        val result = LinkedHashMap<String, MarkInfo>()
        forEachDataFile(modRoots, "marks") { json ->
            try {
                parseMarkObject(json)?.let { result[it.id] = it }
            } catch (_: Exception) {}
        }
        DebugLog.info("JarDataCache: parsed ${result.size} marks")
        return result.values.toList()
    }

    internal fun parseMarkObject(json: JsonObject): MarkInfo? {
        val nameKey = json.optString("name") ?: return null
        val id = "cobblemon:${nameKey.substringAfterLast('.')}"
        return MarkInfo(
            id = id,
            nameKey = nameKey,
            descriptionKey = json.optString("description") ?: "$nameKey.desc",
            titleKey = json.optString("title"),
            titleColor = json.optString("titleColor") ?: json.optString("titleColour"),
            chance = json.optFloat("chance") ?: 0f,
            group = json.optString("group"),
            sortOrder = json.optInt("sortOrder") ?: 0,
            indexNumber = json.optInt("indexNumber"),
        )
    }

    internal fun parseTmObject(json: JsonObject): TmInfo? {
        val move = (json.optString("moveName") ?: return null).lowercase()
        val type = json.optString("type")?.lowercase()?.ifBlank { null }
        val variants = json.optArray("obtainMethods")?.mapNotNull {
            if (it.isJsonObject) it.asJsonObject.optString("variant") else null
        } ?: emptyList()
        val passive = variants.any { it.substringAfterLast(':') == "default" }
        val ingredients = json.optArray("recipe")?.mapNotNull { el ->
            if (!el.isJsonObject) return@mapNotNull null
            val o = el.asJsonObject
            val count = o.optInt("count") ?: 1
            val item = o.optString("item")
            val tag = o.optString("tag")
            when {
                item != null -> TmIngredient(listOf(item), count)
                tag != null -> TmIngredient(emptyList(), count, tagId = tag)
                else -> null
            }
        } ?: emptyList()
        return TmInfo(move, type, ingredients, passive, "bundled")
    }

    /**
     * (namespace subdirectory, human-readable source label) pairs for `data/<ns>/$subPath` across
     * mod jars and directory datapacks. Shared by [forEachDataFile] and [forEachDataText] - zip
     * datapacks are collected separately since they don't expose real filesystem directories.
     */
    private fun collectNamespaceDirs(modRoots: List<Path>, subPath: String): List<Pair<Path, String>> {
        val dirs = mutableListOf<Pair<Path, String>>()
        for (root in modRoots) {
            try {
                val dataDir = root.resolve("data")
                if (!Files.isDirectory(dataDir)) continue
                Files.list(dataDir).use { it.filter { ns -> Files.isDirectory(ns) }.forEach { ns ->
                    ns.resolve(subPath).takeIf { d -> Files.isDirectory(d) }
                        ?.let { dirs.add(it to "jar:${root.fileName}") }
                } }
            } catch (_: Exception) {}
        }
        try {
            val datapacksDir = com.cobbledex.platform.PlatformHelper.getGameDir().resolve("datapacks")
            if (Files.isDirectory(datapacksDir)) {
                Files.list(datapacksDir).use { it.filter { p -> Files.isDirectory(p) }.forEach { pack ->
                    val dataDir = pack.resolve("data")
                    if (Files.isDirectory(dataDir)) Files.list(dataDir).use { nss ->
                        nss.filter { ns -> Files.isDirectory(ns) }.forEach { ns ->
                            ns.resolve(subPath).takeIf { d -> Files.isDirectory(d) }
                                ?.let { dirs.add(it to "datapack:${pack.fileName}") }
                        }
                    }
                } }
            }
        } catch (_: Exception) {}
        return dirs
    }

    /** Every `.zip` datapack under [datapacksDir] matching [packFilter], opened and handed to [action]. */
    private fun forEachZipDatapack(datapacksDir: Path, packFilter: (Path) -> Boolean = { true }, action: (ZipFile, Path) -> Unit) {
        try {
            Files.list(datapacksDir).use { packs ->
                packs.filter { it.toString().endsWith(".zip") && Files.isRegularFile(it) && packFilter(it) }.forEach { zipPath ->
                    try {
                        ZipFile(zipPath.toFile()).use { zip -> action(zip, zipPath) }
                    } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}
    }

    /**
     * Walks every JSON file under `data/<ns>/<subDir>` across mod jars, directory datapacks and zip
     * datapacks, handing each parsed object to [handler]. For a flat single-object-per-file shape
     * (like habitat pools); callers that need the `spawns`-array wrapper keep their own loops.
     */
    private inline fun forEachDataFile(modRoots: List<Path>, subDir: String, crossinline handler: (JsonObject) -> Unit) {
        for ((dir, _) in collectNamespaceDirs(modRoots, subDir)) {
            try {
                Files.walk(dir, 10).use { files ->
                    files.filter { it.toString().endsWith(".json") && Files.isRegularFile(it) }.forEach { file ->
                        try {
                            val obj = InputStreamReader(Files.newInputStream(file), Charsets.UTF_8).use { r ->
                                JsonParser.parseReader(r).asJsonObject
                            }
                            handler(obj)
                        } catch (_: Exception) {}
                    }
                }
            } catch (_: Exception) {}
        }

        try {
            val datapacksDir = com.cobbledex.platform.PlatformHelper.getGameDir().resolve("datapacks")
            if (Files.isDirectory(datapacksDir)) {
                scanZipDatapacks(datapacksDir, subDir) { _, _, _, json -> handler(json) }
            }
        } catch (_: Exception) {}
    }

    // ==================== Type Chart Override Parsing ====================

    private const val TYPE_CHART_SUBPATH = "mega_showdown/showdown/typecharts"

    /**
     * Type effectiveness overrides from `data/<ns>/mega_showdown/showdown/typecharts/<type>.js`
     * - the Showdown-style `damageTaken` scripts that mega_showdown reads to patch Cobblemon's
     * battle engine, and how rebalance packs like Project Lazuli redefine type matchups. Each file
     * is named after the *defending* type and maps each attacking type to a Showdown damage code
     * (0 = normal, 1 = super effective, 2 = not very effective, 3 = immune), so the result is keyed
     * defending-type-first to match how [TypeChart.applyOverrides] consumes it.
     *
     * Sources are scanned mod jars first, then directory datapacks, then zip datapacks - if two of
     * them define the same defending type (e.g. two rebalance packs both loaded), the later source
     * wins and a warning is logged naming both, rather than resolving silently.
     */
    private fun parseTypeChartOverridesFromJars(modRoots: List<Path>): Map<String, Map<String, Float>> {
        val result = mutableMapOf<String, Map<String, Float>>()
        val sourceOf = mutableMapOf<String, String>()
        forEachDataText(modRoots, TYPE_CHART_SUBPATH, ".js") { fileName, text, source ->
            applyTypeChartEntry(result, sourceOf, fileName, text, source)
        }
        DebugLog.info("JarDataCache: parsed type chart overrides for ${result.size} defending types")
        return result
    }

    /**
     * Re-reads the type chart overrides from the packs the hosted world actually loaded, replacing
     * whatever [parseTypeChartOverridesFromJars] found at startup.
     *
     * That startup scan runs before a world exists, so it can only ever see mod jars and
     * `<gameDir>/datapacks/` - a rebalance pack installed into the world's own `datapacks/` folder,
     * or served by a loader mod like OpenLoader, is invisible to it. That's why Project Lazuli's
     * type chart applied when it was installed as a mod but not as a datapack: same files, a
     * location the scan never looked in. See [LocalDataSource] for why asking the server is the
     * complete answer rather than one more folder to guess at.
     *
     * Returns whether the overrides actually changed, so a caller only pays for a rebuild when they
     * did - on the overwhelmingly common join, where the startup scan already had it right, this
     * finds the same chart and costs nothing further.
     */
    fun rescanTypeChartOverrides(resourceManager: ResourceManager): Boolean {
        val result = mutableMapOf<String, Map<String, Float>>()
        val sourceOf = mutableMapOf<String, String>()
        for (entry in LocalDataSource.readText(resourceManager, TYPE_CHART_SUBPATH, ".js")) {
            applyTypeChartEntry(result, sourceOf, entry.fileName, entry.text, "pack:${entry.sourcePackId}")
        }
        if (result == cachedTypeChartOverrides) return false
        DebugLog.info(
            "JarDataCache: type chart overrides refreshed from loaded datapacks - " +
                "${cachedTypeChartOverrides.size} -> ${result.size} defending types"
        )
        cachedTypeChartOverrides = result
        return true
    }

    /**
     * Merges one type chart script into [result], logging (via [sourceOf]) when [source] isn't the
     * first to define that defending type - see [parseTypeChartOverridesFromJars]. Pulled out as its
     * own function so the collision behavior is directly unit-testable without touching a filesystem.
     */
    internal fun applyTypeChartEntry(
        result: MutableMap<String, Map<String, Float>>,
        sourceOf: MutableMap<String, String>,
        fileName: String,
        text: String,
        source: String,
    ) {
        val overrides = parseDamageTakenBlock(text)
        if (overrides.isEmpty()) return
        val type = fileName.lowercase()
        sourceOf[type]?.let { existing ->
            DebugLog.warn(
                "Type chart override for '$type' defined by both $existing and $source - " +
                    "using $source (last scanned wins)"
            )
        }
        result[type] = overrides
        sourceOf[type] = source
    }

    private val DAMAGE_TAKEN_BLOCK = Regex("damageTaken\\s*:\\s*\\{([^}]*)}", RegexOption.DOT_MATCHES_ALL)
    private val DAMAGE_TAKEN_ENTRY = Regex("['\"]?(\\w+)['\"]?\\s*:\\s*(\\d)")

    internal fun parseDamageTakenBlock(text: String): Map<String, Float> {
        val block = DAMAGE_TAKEN_BLOCK.find(text)?.groupValues?.get(1) ?: return emptyMap()
        val result = mutableMapOf<String, Float>()
        for (match in DAMAGE_TAKEN_ENTRY.findAll(block)) {
            val multiplier = when (match.groupValues[2].toInt()) {
                1 -> 2f
                2 -> 0.5f
                3 -> 0f
                else -> 1f
            }
            result[match.groupValues[1].lowercase()] = multiplier
        }
        return result
    }

    /**
     * Walks every file directly under `data/<ns>/$subPath` across mod jars, directory datapacks
     * and zip datapacks, handing each file's raw text - plus a human-readable source label, for
     * collision reporting - to [handler]. Mirrors [forEachDataFile]'s three sources, but for
     * plain-text files (like the Showdown-JS type chart scripts) rather than JSON.
     */
    private fun forEachDataText(
        modRoots: List<Path>,
        subPath: String,
        extension: String,
        handler: (fileName: String, text: String, source: String) -> Unit,
    ) {
        for ((dir, source) in collectNamespaceDirs(modRoots, subPath)) {
            try {
                Files.walk(dir, 5).use { files ->
                    files.filter { it.toString().endsWith(extension) && Files.isRegularFile(it) }.forEach { file ->
                        try {
                            val text = Files.readString(file, Charsets.UTF_8)
                            handler(file.fileName.toString().removeSuffix(extension), text, source)
                        } catch (_: Exception) {}
                    }
                }
            } catch (_: Exception) {}
        }

        try {
            val datapacksDir = com.cobbledex.platform.PlatformHelper.getGameDir().resolve("datapacks")
            if (Files.isDirectory(datapacksDir)) {
                scanZipDatapackTexts(datapacksDir, subPath, extension, handler)
            }
        } catch (_: Exception) {}
    }

    private fun scanZipDatapackTexts(
        datapacksDir: Path,
        subPath: String,
        extension: String,
        handler: (fileName: String, text: String, source: String) -> Unit,
    ) {
        val pattern = Regex("^data/[^/]+/${Regex.escape(subPath)}/[^/]+${Regex.escape(extension)}\$")
        forEachZipDatapack(datapacksDir) { zip, zipPath ->
            for (entry in zip.entries()) {
                if (entry.isDirectory || !pattern.matches(entry.name)) continue
                try {
                    val text = zip.getInputStream(entry).use { stream ->
                        InputStreamReader(stream, Charsets.UTF_8).readText()
                    }
                    val fileName = entry.name.substringAfterLast('/').removeSuffix(extension)
                    handler(fileName, text, "zip:${zipPath.fileName}")
                } catch (_: Exception) {}
            }
        }
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

    private class EvoMoveParseCounters {
        var fileCount = 0
        var failCount = 0
        var baseEvoCount = 0
        var formEvoCount = 0
    }

    /**
     * Everything one pass over the species JSON yields. Held as a single object so adding a field
     * doesn't mean widening four method signatures.
     */
    private class SpeciesJsonScan {
        val evolutions = mutableMapOf<String, MutableList<EvolutionInfo>>()
        val moves = mutableMapOf<String, JarMoveData>()
        val formMoves = mutableMapOf<String, JarMoveData>()
        val traits = mutableMapOf<String, JarTraitData>()
        val formTraits = mutableMapOf<String, JarTraitData>()
        val counters = EvoMoveParseCounters()
        /** Species name -> the mod id or datapack/resourcepack name whose "species" json file
         *  declared it. Only ever set from an outright declaration, never a "species_additions"
         *  patch, so a patch never misattributes a base-game species to the patching pack. */
        val provenance = mutableMapOf<String, String>()
    }

    private fun parseEvolutionsAndMovesFromJars(modRoots: List<SpawnDataLoader.ModRoot>): SpeciesJsonScan {
        val scan = SpeciesJsonScan()
        val result = scan.evolutions
        val counters = scan.counters

        // "species/*.json" files declare a species outright via a "name" field.
        // "species_additions/*.json" files patch an *existing* species (added by
        // another mod/base game) and identify their target via a "target" field
        // instead (e.g. "cobblemon:eevee") - no "name" field at all. Addon packs
        // like Extra Eeveelutions/Kazeran Eeveelutions add their evolution
        // branches this way, so both folders must be scanned or those branches
        // are silently invisible even though the base species' own evolutions
        // (from "species/") parse fine.
        for (root in modRoots) {
            try {
                scanLooseSpeciesDataDir(root.path.resolve("data"), scan, root.id)
            } catch (_: Exception) {}
        }

        // modRoots only enumerates Fabric-registered mod jars - content
        // shipped as a loose/zipped datapack or resourcepack (e.g. Fanmade
        // Form Funfair ships as a resourcepack zip, relying on the
        // ResourcePackOverrides mod to also apply its data/ folder as real
        // game data) never showed up here at all, even on setups where
        // Cobblemon's own runtime *did* load that content correctly - so
        // this raw-JSON fallback had nothing to merge in for it (confirmed
        // via /cobbledex evo: Fanmade Form Funfair's Roggenrola/Boldore
        // "Overgrown" species_additions files were invisible to JarDataCache
        // entirely, not just misparsed).
        try {
            val gameDir = com.cobbledex.platform.PlatformHelper.getGameDir()
            // Global datapacks (via the Globalpacks mod) apply unconditionally,
            // but resourcepacks are opt-in per options.txt's "resourcePacks"
            // list - the folder itself commonly holds disabled packs too (this
            // modpack keeps several "DO NOT ENABLE, credits only" packs sitting
            // right next to active ones, with hundreds of their own species
            // files). Scanning those unconditionally would silently blend in
            // evolution/move data for content the player never actually
            // enabled, so resourcepacks are filtered to the enabled set;
            // datapacks are not.
            val enabledResourcePackFiles = readEnabledResourcePackFileNames(gameDir)
            for (folderName in listOf("datapacks", "resourcepacks")) {
                val dir = gameDir.resolve(folderName)
                if (!Files.exists(dir) || !Files.isDirectory(dir)) continue
                val isResourcePacks = folderName == "resourcepacks"

                Files.list(dir).use { entries ->
                    entries.filter { Files.isDirectory(it) }
                        .filter { !isResourcePacks || it.fileName.toString() in enabledResourcePackFiles }
                        .forEach { pack ->
                            scanLooseSpeciesDataDir(pack.resolve("data"), scan, pack.fileName.toString())
                        }
                }

                val zipFilter: (Path) -> Boolean = { path ->
                    !isResourcePacks || path.fileName.toString() in enabledResourcePackFiles
                }
                for ((subFolder, isAddition) in listOf("species" to false, "species_additions" to true)) {
                    scanZipDatapacks(dir, subFolder, zipFilter) { packName, _, _, json ->
                        processSpeciesJsonObject(json, isAddition, scan, packName)
                    }
                }
            }
        } catch (_: Exception) {}

        DebugLog.info("JarDataCache: parsed ${counters.baseEvoCount} base + ${counters.formEvoCount} form evolutions from ${counters.fileCount} species files (${counters.failCount} failed)")
        return scan
    }

    private fun scanLooseSpeciesDataDir(dataDir: Path, scan: SpeciesJsonScan, origin: String? = null) {
        val counters = scan.counters
        if (!Files.exists(dataDir) || !Files.isDirectory(dataDir)) return

        Files.list(dataDir).use { namespaces ->
            namespaces.filter { Files.isDirectory(it) }.forEach { namespace ->
                for ((folderName, isAddition) in listOf("species" to false, "species_additions" to true)) {
                    val speciesDir = namespace.resolve(folderName)
                    if (!Files.exists(speciesDir) || !Files.isDirectory(speciesDir)) continue

                    Files.walk(speciesDir, 10).use { files ->
                        files.filter { it.toString().endsWith(".json") && Files.isRegularFile(it) }.forEach { file ->
                            try {
                                val obj = InputStreamReader(Files.newInputStream(file), Charsets.UTF_8).use { reader ->
                                    JsonParser.parseReader(reader).asJsonObject
                                }
                                processSpeciesJsonObject(obj, isAddition, scan, origin)
                            } catch (_: Exception) { counters.failCount++ }
                        }
                    }
                }
            }
        }
    }

    private fun processSpeciesJsonObject(obj: JsonObject, isAddition: Boolean, scan: SpeciesJsonScan, origin: String? = null) {
        val result = scan.evolutions
        val movesResult = scan.moves
        val formMovesResult = scan.formMoves
        val counters = scan.counters
        try {
            val rawName = if (isAddition) {
                obj.optString("target")?.substringAfter(':')?.lowercase()
            } else {
                obj.optString("name")?.lowercase()
            } ?: return
            // Species' own "name" field is a display name and can contain
            // spaces/punctuation (e.g. "Great Tusk", "Iron Treads", "Ting-Lu",
            // "Mime Jr.") - every other map in the pipeline is keyed by
            // SpeciesNameNormalizer.normalize()'d ids, so leaving this one
            // unnormalized meant movesResult["great tusk"] (with a space)
            // never matched SpeciesTraitMerger's lookup by the real
            // normalized key "greattusk", silently losing that species' TM/
            // tutor/egg move fallback entirely (confirmed via the exported
            // CobbleDex spreadsheet: Great Tusk showed only its 17 level-up
            // moves, none of its 53 TM + 25 tutor moves).
            val name = SpeciesNameNormalizer.normalize(rawName)
            counters.fileCount++

            // Only an outright "species/" declaration identifies who added this species - a
            // "species_additions" patch targets a species someone else already declared, so
            // recording its origin here would misattribute that base species to the patcher.
            // First declaration found wins; it shouldn't matter in practice since a species name
            // is normally declared exactly once across all loaded content.
            if (!isAddition && origin != null) {
                scan.provenance.putIfAbsent(name, origin)
            }

            // Parse moves
            val movesArray = obj.optArray("moves")
            if (movesArray != null) {
                parseMovesArray(movesArray)?.let { movesResult[name] = it }
            }

            // Breeding/dex traits Cobblemon's species sync leaves out entirely. species_additions
            // files patch an existing species, so their traits are merged over any already parsed
            // for that name rather than replacing them wholesale.
            parseTraits(obj)?.let { scan.traits[name] = scan.traits[name]?.overlaidWith(it) ?: it }

            // Base evolutions
            val evolutions = obj.optArray("evolutions")
            if (evolutions != null) {
                for (evoElem in evolutions) {
                    try {
                        val info = parseEvolutionFromJson(name, null, evoElem.asJsonObject)
                        if (info != null) {
                            result.getOrPut(name) { mutableListOf() }.add(info)
                            counters.baseEvoCount++
                        }
                    } catch (_: Exception) {}
                }
            }

            // Form evolutions + moves. Cobblemon's own client-side FormData
            // only reliably syncs a species_additions form's LEVEL-UP moves -
            // egg/tutor/tm entries nested inside "forms[].moves" routinely
            // come back empty at runtime (confirmed via /cobbledex evo:
            // Laser's Fakemon Pack's Fomantis Lunar form defines 102 moves in
            // its JSON, but Cobblemon's runtime form.moves only exposed the
            // 13 level-up ones, losing all 59 TM/20 tutor/10 egg moves).
            // Parsed here from the raw JSON as a fallback source, keyed
            // identically to the runtime form key so SpeciesTraitMerger can
            // fill the gap per-form instead of only for the bare base
            // species.
            val forms = obj.optArray("forms")
            if (forms != null) {
                for (formElem in forms) {
                    try {
                        val form = formElem.asJsonObject
                        val aspects = form.optStringArray("aspects").toSet()
                        val formKey = if (aspects.isEmpty()) name
                            else buildJsonFormEntryKey(name, form)

                        // Unlike the base species name above, a form entry is new content in
                        // whatever file declares it even when that file is a "species_additions"
                        // patch on someone else's base species - that's exactly how add-on/
                        // rebalance packs (e.g. a Mega evolution pack) normally ship a new form:
                        // patching the vanilla species with an extra forms[] entry rather than
                        // declaring a whole new species. So this form's provenance is recorded
                        // regardless of isAddition - but only when it is genuinely a distinct
                        // form. An aspect-less entry resolves formKey back to the base species,
                        // which a patch must never claim authorship of; that key is governed
                        // solely by the declaration-only rule above.
                        if (origin != null && formKey != name) {
                            scan.provenance.putIfAbsent(formKey, origin)
                        }

                        val formMovesArray = form.optArray("moves")
                        if (formMovesArray != null) {
                            parseMovesArray(formMovesArray)?.let { formMovesResult[formKey] = it }
                        }

                        parseTraits(form)?.let {
                            scan.formTraits[formKey] = scan.formTraits[formKey]?.overlaidWith(it) ?: it
                        }

                        val formEvos = form.optArray("evolutions")
                        if (formEvos != null && !formEvos.isEmpty) {
                            for (evoElem in formEvos) {
                                try {
                                    val info = parseEvolutionFromJson(name, aspects, evoElem.asJsonObject)
                                    if (info != null) {
                                        result.getOrPut(formKey) { mutableListOf() }.add(info)
                                        if (formKey != name) {
                                            result.getOrPut(name) { mutableListOf() }.add(info)
                                        }
                                        counters.formEvoCount++
                                    }
                                } catch (_: Exception) {}
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) { counters.failCount++ }
    }

    /**
     * Reads the breeding/dex fields out of a species or form JSON object. Returns null when the
     * object declares none of them, so a form that only overrides (say) its typing doesn't shadow
     * its base species' traits with a row of nulls.
     */
    private fun parseTraits(obj: JsonObject): JarTraitData? {
        val evYield = obj.optObject("evYield")?.let { yields ->
            val parsed = mutableMapOf<String, Int>()
            for ((stat, value) in yields.entrySet()) {
                try {
                    val amount = value.asInt
                    if (amount != 0) parsed[stat.lowercase()] = amount
                } catch (_: Exception) {}
            }
            parsed.ifEmpty { null }
        }
        val labels = obj.optStringArray("labels").takeIf { it.isNotEmpty() }?.map { it.lowercase() }?.toSet()
        val data = JarTraitData(
            catchRate = obj.optInt("catchRate"),
            eggGroups = obj.optStringArray("eggGroups").takeIf { it.isNotEmpty() }?.map { it.lowercase() },
            eggCycles = obj.optInt("eggCycles"),
            baseFriendship = obj.optInt("baseFriendship"),
            baseExperienceYield = obj.optInt("baseExperienceYield"),
            evYield = evYield,
            labels = labels,
        )
        return if (data.isEmpty) null else data
    }

    /** Field-wise overlay: values present in [other] win, everything else is kept. */
    private fun JarTraitData.overlaidWith(other: JarTraitData): JarTraitData = JarTraitData(
        catchRate = other.catchRate ?: catchRate,
        eggGroups = other.eggGroups ?: eggGroups,
        eggCycles = other.eggCycles ?: eggCycles,
        baseFriendship = other.baseFriendship ?: baseFriendship,
        baseExperienceYield = other.baseExperienceYield ?: baseExperienceYield,
        evYield = other.evYield ?: evYield,
        labels = other.labels ?: labels,
    )

    private fun parseMovesArray(movesArray: JsonArray): JarMoveData? {
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
        if (levelUp.isEmpty() && egg.isEmpty() && tutor.isEmpty() && tm.isEmpty()) return null
        return JarMoveData(levelUp, egg, tutor, tm)
    }

    // Mirrors EvolutionDataLoader.buildFormEntryKey's scheme exactly (underscore-
    // joined, regional forms suffixed with no separator) so a form's evolution
    // ends up keyed identically whether it was read from Cobblemon's live API
    // or from this raw-JSON fallback. The two used to diverge - this path
    // joined "$name ${aspects...}" with a SPACE, which normalizeMapKeys' key
    // normalizer (SpeciesNameNormalizer.normalize) then stripped entirely
    // (space isn't a permitted character) instead of turning into a separator,
    // collapsing e.g. "fomantis lunar" into "fomantislunar" - a key that
    // matched nothing else in the pipeline, silently breaking that form's own
    // evolution lookup and leaving a phantom, data-less duplicate species key.
    private val REGIONAL_LABEL_TO_SUFFIX = mapOf(
        "alolan_form" to "alolan",
        "galarian_form" to "galarian",
        "hisuian_form" to "hisuian",
        "paldean_form" to "paldean"
    )

    // Mirrors EvolutionDataLoader.REGIONAL_ASPECT_TO_SUFFIX - falls back to
    // the form's aspect when its labels don't carry a recognized regional
    // marker, in case a mod's own JSON omits/misdeclares "labels" the way
    // Cobblemon's runtime FormData was found to for Farfetch'd Galar
    // (aspect stayed correct even when the runtime label didn't).
    private val REGIONAL_ASPECT_TO_SUFFIX = mapOf(
        "alolan" to "alolan",
        "galarian" to "galarian",
        "hisuian" to "hisuian",
        "paldean" to "paldean"
    )

    private fun buildJsonFormEntryKey(name: String, form: JsonObject): String {
        val labels = form.optStringArray("labels")
        val regionalLabel = labels.firstOrNull { it in REGIONAL_LABEL_TO_SUFFIX }
        val regionalSuffix = if (regionalLabel != null) {
            REGIONAL_LABEL_TO_SUFFIX[regionalLabel]
        } else {
            form.optStringArray("aspects").map { it.lowercase() }.firstNotNullOfOrNull { REGIONAL_ASPECT_TO_SUFFIX[it] }
        }
        if (regionalSuffix != null) {
            return "${SpeciesNameNormalizer.normalize(name)}$regionalSuffix"
        }
        val formName = form.optString("name")?.lowercase()?.replace(Regex("[^a-z0-9]"), "")
        if (formName.isNullOrBlank()) return name
        return "${SpeciesNameNormalizer.normalize(name)}_$formName"
    }

    private fun parseEvolutionFromJson(fromSpecies: String, fromAspects: Set<String>?, evo: JsonObject): EvolutionInfo? {
        val id = evo.optString("id") ?: return null
        val resultStr = evo.optString("result") ?: return null

        // Result can be "species", "species aspect1 aspect2", or use
        // Cobblemon's PokemonProperties key=value syntax for the aspect,
        // e.g. "boldore aspect=overgrown" (confirmed via /cobbledex evo:
        // Fanmade Form Funfair's Roggenrola->Boldore Overgrown evolution
        // uses this form) - naively treating "aspect=overgrown" as the
        // literal aspect string instead of extracting "overgrown" produced
        // a target key that matched no real form, silently falling back to
        // the base (non-Overgrown) Boldore.
        //
        // A boolean SPECIES FEATURE can also be toggled this way -
        // "flaaffy rlm=true" (confirmed via Cobblemon RLM's Mareep->Flaaffy
        // evolution, whose species_additions declares "features": ["rlm"])
        // sets the boolean feature "rlm" to true, which itself contributes
        // an aspect of the same name ("rlm") - unlike "aspect=X" the aspect
        // name here IS the key, not the value, so this needs its own case.
        val resultParts = resultStr.split(" ").filter { it.isNotBlank() }
        val toSpecies = resultParts.first().lowercase()
        val toAspects = resultParts.drop(1).mapNotNull { token ->
            val lower = token.lowercase()
            when {
                lower.startsWith("aspect=") -> lower.removePrefix("aspect=").ifBlank { null }
                "=" in lower -> {
                    val (key, value) = lower.split("=", limit = 2)
                    if (value == "true") key.ifBlank { null } else null
                }
                else -> lower
            }
        }.toSet()

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

    /**
     * Reads one evolution requirement out of species JSON.
     *
     * Table-driven off [EvolutionRequirementSpec] rather than a branch per variant: the variant
     * names and their field names live in exactly one place, shared with the runtime reader in
     * [EvolutionDataLoader], so the two cannot drift apart.
     */
    internal fun parseRequirementFromJson(req: JsonObject): EvolutionRequirement {
        val variant = req.optString("variant") ?: EvolutionRequirementSpec.UNKNOWN
        val spec = EvolutionRequirementSpec.forVariant(variant)

        // "any" is a wrapper: show the first possibility rather than the wrapper itself.
        if (spec?.variant == EvolutionRequirementSpec.ANY) {
            val possibilities = req.optArray("possibilities")
            if (possibilities != null && !possibilities.isEmpty) {
                try {
                    return parseRequirementFromJson(possibilities[0].asJsonObject)
                } catch (_: Exception) {}
            }
            return EvolutionRequirement(EvolutionRequirementSpec.ANY, emptyMap())
        }

        if (spec == null) {
            // Unrecognised variant - keep whatever primitives it carries so the page can still say
            // something useful, rather than dropping the requirement entirely.
            val data = mutableMapOf<String, Any>()
            for ((key, value) in req.entrySet()) {
                if (key == "variant") continue
                val prim = value.takeIf { it.isJsonPrimitive }?.asJsonPrimitive ?: continue
                when {
                    prim.isNumber -> data[key] = prim.asNumber
                    prim.isBoolean -> data[key] = prim.asBoolean
                    prim.isString -> data[key] = prim.asString
                }
            }
            return EvolutionRequirement(variant, data)
        }

        val data = mutableMapOf<String, Any>()
        for (field in spec.fields) {
            val value: Any? = when (field.type) {
                EvolutionRequirementSpec.FieldType.STRING -> req.optString(field.key)
                EvolutionRequirementSpec.FieldType.INT -> req.optInt(field.key)
                EvolutionRequirementSpec.FieldType.BOOLEAN -> req.optBool(field.key)
            }
            if (value != null) data[field.key] = value
        }
        // Always report the canonical name, so an alias like "has_move" renders as "move_set".
        return EvolutionRequirement(spec.variant, data)
    }

    // ==================== Fossil Parsing ====================

    private fun parseFossilsFromJars(modRoots: List<Path>): Map<String, List<FossilCombo>> {
        val result = mutableMapOf<String, MutableList<FossilCombo>>()

        fun processFossilJson(json: JsonObject) {
            val resultStr = json.optString("result") ?: return
            val fossilArr = json.optArray("fossils") ?: return
            val items = fossilArr.mapNotNull { if (it.isJsonPrimitive) it.asString else null }
            if (items.isEmpty()) return

            // Parse "species form=X aspect=Y min_perfect_ivs=2" format
            val parts = resultStr.split(" ")
            val species = parts.first().lowercase()
            val extraParts = parts.drop(1)
            val formPart = extraParts.firstOrNull { it.startsWith("form=") }
            val aspectParts = extraParts.filter { it.startsWith("aspect=") }.map { it.removePrefix("aspect=") }
            val extras = mutableListOf<String>()
            formPart?.let { extras.add(it) }
            extras.addAll(aspectParts)
            val extraTags = extras.joinToString(" ").takeIf { it.isNotBlank() }

            result.getOrPut(species) { mutableListOf() }.add(FossilCombo(species, items, extraTags))
        }

        fun scanFossilDataDir(dataDir: Path) {
            if (!Files.exists(dataDir) || !Files.isDirectory(dataDir)) return
            Files.list(dataDir).use { namespaces ->
                namespaces.filter { Files.isDirectory(it) }.forEach { namespace ->
                    val fossilDir = namespace.resolve("fossils")
                    if (!Files.exists(fossilDir) || !Files.isDirectory(fossilDir)) return@forEach
                    Files.walk(fossilDir, 5).use { files ->
                        files.filter { it.toString().endsWith(".json") && Files.isRegularFile(it) }.forEach { file ->
                            try {
                                val json = InputStreamReader(Files.newInputStream(file), Charsets.UTF_8).use { reader ->
                                    JsonParser.parseReader(reader).asJsonObject
                                }
                                processFossilJson(json)
                            } catch (_: Exception) {}
                        }
                    }
                }
            }
        }

        // Scan mod JARs
        for (root in modRoots) {
            try {
                scanFossilDataDir(root.resolve("data"))
            } catch (_: Exception) {}
        }

        // modRoots only enumerates Fabric-registered mod jars, so fossils
        // shipped as a loose/zipped datapack or resourcepack were invisible -
        // Cobblemon Fossil Hybrids ships its fossils as a resourcepack zip
        // (its Pawleo/Anobuto/etc. work in game but never showed a combination
        // here). Same scan the evolution/species parser already does: both
        // folders, resourcepacks filtered to the set enabled in options.txt so
        // "do not enable" credit-only packs don't blend in.
        try {
            val gameDir = com.cobbledex.platform.PlatformHelper.getGameDir()
            val enabledResourcePackFiles = readEnabledResourcePackFileNames(gameDir)
            for (folderName in listOf("datapacks", "resourcepacks")) {
                val dir = gameDir.resolve(folderName)
                if (!Files.exists(dir) || !Files.isDirectory(dir)) continue
                val isResourcePacks = folderName == "resourcepacks"

                Files.list(dir).use { entries ->
                    entries.filter { Files.isDirectory(it) }
                        .filter { !isResourcePacks || it.fileName.toString() in enabledResourcePackFiles }
                        .forEach { pack -> scanFossilDataDir(pack.resolve("data")) }
                }

                val zipFilter: (Path) -> Boolean = { path ->
                    !isResourcePacks || path.fileName.toString() in enabledResourcePackFiles
                }
                scanZipDatapacks(dir, "fossils", zipFilter) { _, _, _, json ->
                    processFossilJson(json)
                }
            }
        } catch (_: Exception) {}

        DebugLog.info("JarDataCache: parsed ${result.values.sumOf { it.size }} fossils for ${result.size} species")
        return result
    }

    // ==================== Mega Stone Parsing (Mega Showdown) ====================

    /**
     * Mega Showdown's own data-driven mega-stone registry - the "mega_showdown/mega" folder under
     * a namespace's data directory, one json file per stone, e.g. "charizardite_x.json":
     * ```
     * { "pokemons": ["Charizard"], "aspect_conditions": { "apply": { "aspects": ["mega_evolution=mega_x"] } } }
     * ```
     * This is the *only* place "which item lets this Pokémon Mega Evolve" exists at all: Mega
     * Showdown's mega forms carry no Cobblemon Evolution/item requirement of their own (confirmed -
     * `evolutions: []` and `battleOnly: true` on the form itself), which is exactly why
     * [RecipeBuilder]'s aspect-inferred mega/primal/gmax transform path has never had item data to
     * show. Not hardcoded to the "mega_showdown" mod id: this reads whatever data lives at that
     * path/namespace, so a compatible fork or reskin needs no code change here to work.
     *
     * The item's real id is always `<namespace>:<filename>` - confirmed against Mega Showdown's own
     * lang keys (`item.mega_showdown.charizardite_x`). The value after "=" in an "apply" aspect
     * (e.g. "mega_x" from "mega_evolution=mega_x") is what actually matches the target form's real
     * Cobblemon aspect; the part before "=" is Mega Showdown's own internal state-variable name, not
     * a Cobblemon aspect, and never matches anything CobbleDex reads elsewhere.
     */
    private fun parseMegaStonesFromJars(modRoots: List<Path>): Map<Pair<String, String>, String> {
        val result = mutableMapOf<Pair<String, String>, String>()
        val subPath = "mega_showdown/mega"
        var fileCount = 0

        for ((dir, _) in collectNamespaceDirs(modRoots, subPath)) {
            // subPath is exactly two segments ("mega_showdown/mega"), so the namespace is always
            // two levels above this directory - i.e. .../data/<namespace>/mega_showdown/mega.
            val namespace = try { dir.parent?.parent?.fileName?.toString() } catch (_: Exception) { null }
                ?: continue
            try {
                Files.walk(dir, 10).use { files ->
                    files.filter { it.toString().endsWith(".json") && Files.isRegularFile(it) }.forEach { file ->
                        try {
                            val obj = InputStreamReader(Files.newInputStream(file), Charsets.UTF_8).use { reader ->
                                JsonParser.parseReader(reader).asJsonObject
                            }
                            val itemId = "$namespace:${file.fileName.toString().removeSuffix(".json")}"
                            val pokemons = obj.optArray("pokemons")
                                ?.mapNotNull { if (it.isJsonPrimitive) SpeciesNameNormalizer.normalize(it.asString) else null }
                                .orEmpty()
                            val applyAspects = obj.optObject("aspect_conditions")?.optObject("apply")
                                ?.optArray("aspects")
                                ?.mapNotNull { if (it.isJsonPrimitive) it.asString else null }
                                .orEmpty()
                            val aspectValues = applyAspects.mapNotNull { it.substringAfter('=', "").ifBlank { null }?.lowercase() }
                            if (pokemons.isEmpty() || aspectValues.isEmpty()) return@forEach
                            fileCount++
                            for (species in pokemons) {
                                for (aspect in aspectValues) {
                                    result[species to aspect] = itemId
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }
            } catch (_: Exception) {}
        }

        if (fileCount > 0) DebugLog.info("JarDataCache: parsed $fileCount mega stone(s) from Mega Showdown-style data")
        return result
    }

    // ==================== ZIP Datapack Scanning ====================

    // options.txt's "resourcePacks" line is a JSON array where each active
    // resourcepack file is listed as "file/<name>.zip" (built-in packs like
    // "vanilla" or namespaced ones like "cobblemon:uniqueshinyforms" aren't
    // files on disk, so they're irrelevant here). Returns just the bare
    // filenames, matching Path.fileName.toString() for the resourcepacks/
    // folder.
    /** Where one file was found, and a hash of its text, for [rawFileInventory]. */
    internal data class InventoryEntry(val source: String, val hash: String)

    /**
     * Every file under `data/<ns>/[subPath]` ending in [extension] that *any* scan in this object can
     * currently reach, keyed `<namespace>:<file name>`.
     *
     * Deliberately the union of all of them - mod jars, loose and zipped datapacks, and the enabled
     * resourcepacks that only the species and fossil scans read - because its one purpose is to
     * answer whether [LocalDataSource] sees everything they do. Anything listed here and missing
     * from the resolved packs is either content the game didn't actually load, or a gap that has to
     * keep its folder scan; that question can't be settled by reading this code, only by running
     * both against a real modpack.
     */
    internal fun rawFileInventory(
        modRoots: List<Path>,
        subPath: String,
        extension: String,
    ): Map<String, InventoryEntry> {
        val out = mutableMapOf<String, InventoryEntry>()
        fun hashOf(text: String) = Integer.toHexString(text.hashCode())

        fun walkDataDir(dataDir: Path, source: String) {
            if (!Files.isDirectory(dataDir)) return
            Files.list(dataDir).use { namespaces ->
                namespaces.filter { Files.isDirectory(it) }.forEach { ns ->
                    val target = ns.resolve(subPath)
                    if (!Files.isDirectory(target)) return@forEach
                    Files.walk(target, 10).use { files ->
                        files.filter { it.toString().endsWith(extension) && Files.isRegularFile(it) }.forEach { file ->
                            try {
                                val text = Files.readString(file, Charsets.UTF_8)
                                out["${ns.fileName}:${file.fileName}"] = InventoryEntry(source, hashOf(text))
                            } catch (_: Exception) {}
                        }
                    }
                }
            }
        }

        for (root in modRoots) {
            try { walkDataDir(root.resolve("data"), "jar:${root.fileName}") } catch (_: Exception) {}
        }

        try {
            val gameDir = com.cobbledex.platform.PlatformHelper.getGameDir()
            val enabled = readEnabledResourcePackFileNames(gameDir)
            val zipPattern = Regex("^data/([^/]+)/${Regex.escape(subPath)}/.*${Regex.escape(extension)}$")

            for (folderName in listOf("datapacks", "resourcepacks")) {
                val dir = gameDir.resolve(folderName)
                if (!Files.isDirectory(dir)) continue
                val isResourcePacks = folderName == "resourcepacks"
                val allowed: (Path) -> Boolean = { !isResourcePacks || it.fileName.toString() in enabled }

                Files.list(dir).use { entries ->
                    entries.filter { Files.isDirectory(it) }.filter(allowed).forEach { pack ->
                        walkDataDir(pack.resolve("data"), "$folderName:${pack.fileName}")
                    }
                }

                forEachZipDatapack(dir, allowed) { zip, zipPath ->
                    for (entry in zip.entries()) {
                        if (entry.isDirectory) continue
                        val match = zipPattern.find(entry.name) ?: continue
                        try {
                            val text = zip.getInputStream(entry).use {
                                InputStreamReader(it, Charsets.UTF_8).readText()
                            }
                            val key = "${match.groupValues[1]}:${entry.name.substringAfterLast('/')}"
                            out[key] = InventoryEntry("$folderName-zip:${zipPath.fileName}", hashOf(text))
                        } catch (_: Exception) {}
                    }
                }
            }
        } catch (_: Exception) {}

        return out
    }

    private fun readEnabledResourcePackFileNames(gameDir: Path): Set<String> {
        return try {
            val optionsFile = gameDir.resolve("options.txt")
            if (!Files.exists(optionsFile)) return emptySet()
            val line = Files.readAllLines(optionsFile, Charsets.UTF_8)
                .firstOrNull { it.startsWith("resourcePacks:") } ?: return emptySet()
            val arrayJson = line.substringAfter("resourcePacks:")
            val array = JsonParser.parseString(arrayJson).asJsonArray
            array.mapNotNull { it.asString.takeIf { s -> s.startsWith("file/") }?.removePrefix("file/") }.toSet()
        } catch (e: Exception) {
            DebugLog.once("resourcepack-enabled-list") { "Failed to read enabled resourcepacks from options.txt: ${e.message}" }
            emptySet()
        }
    }

    private fun scanZipDatapacks(
        datapacksDir: Path,
        subDir: String,
        packFilter: (Path) -> Boolean = { true },
        handler: (packName: String, namespace: String, entryName: String, json: JsonObject) -> Unit
    ) {
        val pattern = Regex("^data/([^/]+)/${subDir}/.+\\.json\$")
        forEachZipDatapack(datapacksDir, packFilter) { zip, zipPath ->
            // Without the extension: this reaches the player as an attribution line
            // ("Added by ..."), where "Added by Fakemon Pack.zip" reads like a filename slipped
            // into the UI.
            val packName = zipPath.fileName.toString().removeSuffix(".zip")
            for (entry in zip.entries()) {
                if (entry.isDirectory) continue
                val match = pattern.matchEntire(entry.name) ?: continue
                val namespace = match.groupValues[1]
                try {
                    val json = zip.getInputStream(entry).use { stream ->
                        InputStreamReader(stream, Charsets.UTF_8).use { reader ->
                            JsonParser.parseReader(reader).asJsonObject
                        }
                    }
                    handler(packName, namespace, entry.name, json)
                } catch (_: Exception) {}
            }
        }
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
