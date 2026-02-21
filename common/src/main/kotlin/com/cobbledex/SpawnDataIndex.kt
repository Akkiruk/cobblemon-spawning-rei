package com.cobbledex

import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@Suppress("ObjectPropertyName")
object SpawnDataIndex {

    enum class LoadState { NOT_LOADED, PARTIAL, FULLY_LOADED }

    @Volatile
    var loadState = LoadState.NOT_LOADED
        private set

    private val dataLock = ReentrantLock()
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "CobbleDex-DataLoad").apply { isDaemon = true }
    }
    private var loadFuture: Future<*>? = null

    @Volatile
    var spawnsBySpecies: Map<String, List<SpawnInfo>> = emptyMap()
        private set

    @Volatile
    var evolutionsBySpecies: Map<String, List<EvolutionInfo>> = emptyMap()
        private set

    @Volatile
    var evolutionsToSpecies: Map<String, List<EvolutionInfo>> = emptyMap()
        private set

    @Volatile
    var speciesInfo: Map<String, EvolutionDataLoader.SpeciesBasicInfo> = emptyMap()
        private set

    @Volatile
    var obtainmentBySpecies: Map<String, List<ObtainmentInfo>> = emptyMap()
        private set

    @Volatile
    var fossilsBySpecies: Map<String, List<FossilCombo>> = emptyMap()
        private set

    @Volatile
    var dropsByItem: Map<String, List<String>> = emptyMap()
        private set

    @Volatile
    var jobRules: List<JobRule> = emptyList()
        private set

    @Volatile
    var allSpeciesNames: List<String> = emptyList()
        private set

    @Volatile
    var dataVersion: Long = 0
        private set

    /** Tracks local load attempts where species exist but evolutions are empty */
    @Volatile
    private var emptyEvoRetries = 0
    private const val MAX_EMPTY_EVO_RETRIES = 5

    /** True when spawn data was received from the server via networking */
    @Volatile
    private var hasServerSync = false

    fun isFullyLoaded(): Boolean = loadState == LoadState.FULLY_LOADED

    fun hasData(): Boolean = allSpeciesNames.isNotEmpty()

    fun ensureLoaded() {
        when (loadState) {
            LoadState.FULLY_LOADED -> return
            LoadState.PARTIAL -> {
                val count = try { PokemonSpecies.implemented.count() } catch (_: Exception) { 0 }
                if (count > 0) {
                    DebugLog.info("Runtime API now has $count species, reloading")
                    loadAll()
                }
            }
            LoadState.NOT_LOADED -> loadAll()
        }
    }

    fun ensureLoadedAsync() {
        when (loadState) {
            LoadState.FULLY_LOADED -> return
            LoadState.PARTIAL -> {
                val count = try { PokemonSpecies.implemented.count() } catch (_: Exception) { 0 }
                if (count > 0) {
                    DebugLog.info("Runtime API now has $count species, reloading async")
                    loadAllAsync()
                }
            }
            LoadState.NOT_LOADED -> loadAllAsync()
        }
    }

    fun loadAll() {
        if (!dataLock.tryLock(10, java.util.concurrent.TimeUnit.SECONDS)) {
            DebugLog.warn("Data load skipped: lock held by another thread for >10s")
            return
        }
        try {
            cancelPendingLoad()
            doLoad()
        } catch (e: Exception) {
            DebugLog.warn("Data load failed: ${e.message}")
        } finally {
            dataLock.unlock()
        }
    }

    private fun loadAllAsync() {
        val prev = loadFuture
        if (prev != null && !prev.isDone) return
        loadFuture = executor.submit {
            dataLock.lock()
            try {
                doLoad()
            } catch (e: Exception) {
                DebugLog.warn("Async data load failed: ${e.message}")
            } finally {
                dataLock.unlock()
            }
        }
    }

    private fun cancelPendingLoad() {
        loadFuture?.cancel(true)
        loadFuture = null
    }

    private fun doLoad() {
        DebugLog.reset()
        PokemonItemCache.reset()

        if (hasServerSync) {
            // Server already sent spawns, evolutions, and species info — just load supplementary data
            try {
                obtainmentBySpecies = normalizeMapKeys(ObtainmentDataLoader.loadFromAllSources(
                    SpawnDataLoader.getModRootPaths()
                ))
            } catch (e: Exception) {
                DebugLog.warn("Obtainment data load failed: ${e.message}")
                obtainmentBySpecies = emptyMap()
            }

            try {
                fossilsBySpecies = normalizeMapKeys(FossilDataLoader.loadFromRuntime())
            } catch (e: Exception) {
                DebugLog.warn("Fossil data load failed: ${e.message}")
                fossilsBySpecies = emptyMap()
            }

            rebuildDerivedData()
            if (loadState != LoadState.FULLY_LOADED) {
                loadState = LoadState.FULLY_LOADED
            }
            dataVersion++

            DebugLog.info(
                "Load complete (server-synced, ${loadState.name}): ${allSpeciesNames.size} species " +
                "(${speciesInfo.count { it.value.nationalDexNumber > 0 }} with dex, " +
                "${spawnsBySpecies.size} with spawns, ${evolutionsBySpecies.size} with evolutions, " +
                "${obtainmentBySpecies.size} with obtainment)"
            )
            return
        }

        // --- Baseline: start with JarDataCache (loaded on game launch) ---
        // Wait briefly for cache if it's still initializing
        JarDataCache.awaitReady(5_000)

        // Try Cobblemon's runtime spawn pool (populated in singleplayer or by server)
        SpawnDataLoader.invalidateCache()
        val runtimeSpawns = normalizeMapKeys(SpawnDataLoader.loadFromRuntime())
        spawnsBySpecies = if (runtimeSpawns.isNotEmpty()) {
            runtimeSpawns
        } else if (JarDataCache.hasCachedSpawns()) {
            DebugLog.info("Using JarDataCache spawns (${JarDataCache.getCachedSpawns().size} species)")
            normalizeMapKeys(JarDataCache.getCachedSpawns())
        } else {
            emptyMap()
        }

        val runtimeCount = try { PokemonSpecies.implemented.count() } catch (_: Exception) { 0 }

        if (runtimeCount > 0) {
            // Try runtime evolutions (works in singleplayer, empty on dedicated servers)
            try {
                evolutionsBySpecies = normalizeMapKeys(EvolutionDataLoader.loadFromRuntime())
            } catch (e: Exception) {
                DebugLog.warn("Runtime evolution load failed: ${e.message}")
                evolutionsBySpecies = emptyMap()
            }

            // Fall back to JarDataCache evolutions if runtime is empty
            if (evolutionsBySpecies.isEmpty() && JarDataCache.hasCachedEvolutions()) {
                DebugLog.info("Using JarDataCache evolutions (${JarDataCache.getCachedEvolutions().size} species)")
                evolutionsBySpecies = normalizeMapKeys(JarDataCache.getCachedEvolutions())
            }

            try {
                speciesInfo = normalizeMapKeys(EvolutionDataLoader.loadSpeciesBasicInfoFromRuntime())
            } catch (e: Exception) {
                DebugLog.warn("Runtime species info load failed: ${e.message}")
                speciesInfo = emptyMap()
            }
        } else {
            DebugLog.warn("PokemonSpecies.implemented empty, spawn data only")
            // Still use cached evolutions even without runtime species
            if (JarDataCache.hasCachedEvolutions()) {
                evolutionsBySpecies = normalizeMapKeys(JarDataCache.getCachedEvolutions())
            } else {
                evolutionsBySpecies = emptyMap()
            }
            speciesInfo = emptyMap()
        }

        try {
            obtainmentBySpecies = normalizeMapKeys(ObtainmentDataLoader.loadFromAllSources(
                SpawnDataLoader.getModRootPaths()
            ))
        } catch (e: Exception) {
            DebugLog.warn("Obtainment data load failed: ${e.message}")
            obtainmentBySpecies = emptyMap()
        }

        try {
            fossilsBySpecies = normalizeMapKeys(FossilDataLoader.loadFromRuntime())
        } catch (e: Exception) {
            DebugLog.warn("Fossil data load failed: ${e.message}")
            fossilsBySpecies = emptyMap()
        }

        // Try loading Cobbleworkers job rules (singleplayer or if on same JVM)
        if (jobRules.isEmpty()) {
            try {
                jobRules = JobDataLoader.loadFromCobbleworkers()
            } catch (e: Throwable) {
                DebugLog.once("jobdata-load") { "Job data load failed: ${e.message}" }
            }
        }

        // Enrich speciesInfo with JAR-cached moves when runtime API returned null
        enrichWithJarMoves()

        rebuildDerivedData()

        val hasEvolutions = evolutionsBySpecies.isNotEmpty()
        val hasSpawns = spawnsBySpecies.isNotEmpty()
        loadState = when {
            runtimeCount == 0 && !hasEvolutions && !hasSpawns -> LoadState.PARTIAL
            runtimeCount == 0 -> {
                // Have cached data but no runtime species yet
                LoadState.PARTIAL
            }
            !hasEvolutions && emptyEvoRetries < MAX_EMPTY_EVO_RETRIES -> {
                emptyEvoRetries++
                DebugLog.info("Species loaded ($runtimeCount) but no evolutions found (attempt $emptyEvoRetries/$MAX_EMPTY_EVO_RETRIES) — staying PARTIAL for retry")
                LoadState.PARTIAL
            }
            !hasEvolutions -> {
                DebugLog.warn("Species loaded ($runtimeCount) but evolutions still empty after $MAX_EMPTY_EVO_RETRIES retries — accepting as final state")
                LoadState.FULLY_LOADED
            }
            else -> {
                emptyEvoRetries = 0
                LoadState.FULLY_LOADED
            }
        }
        dataVersion++

        DebugLog.info(
            "Load complete (${loadState.name}): ${allSpeciesNames.size} species " +
            "(${speciesInfo.count { it.value.nationalDexNumber > 0 }} with dex, " +
            "${spawnsBySpecies.size} with spawns, ${evolutionsBySpecies.size} with evolutions, " +
            "${obtainmentBySpecies.size} with obtainment)"
        )
    }

    /** Mark data stale on disconnect. Cached JAR data is preserved —
     *  only runtime/server data is cleared. */
    fun onDisconnect() {
        cancelPendingLoad()
        PokemonItemCache.reset()
        emptyEvoRetries = 0
        hasServerSync = false
        jobRules = emptyList()

        // Clear stale data from Cobblemon's spawn pool (prevents singleplayer data persisting into server sessions)
        try {
            val pool = com.cobblemon.mod.common.api.spawning.CobblemonSpawnPools.WORLD_SPAWN_POOL
            val staleCount = pool.details.size
            if (staleCount > 0) {
                pool.reload(emptyMap())
                DebugLog.info("Cleared $staleCount stale spawn entries from Cobblemon pool")
            }
        } catch (e: Exception) {
            DebugLog.once("disconnect-pool-clear") { "Failed to clear spawn pool on disconnect: ${e.message}" }
        }

        dataLock.withLock {
            loadState = LoadState.NOT_LOADED
        }
        DebugLog.info("Marked data stale on disconnect (${allSpeciesNames.size} species cached)")
    }

    /** Accept all data synced from the server via networking */
    fun applyServerSync(syncedSpawns: Map<String, List<SpawnInfo>>,
                        syncedEvolutions: Map<String, List<EvolutionInfo>>,
                        syncedSpeciesInfo: Map<String, EvolutionDataLoader.SpeciesBasicInfo>,
                        syncedJobRules: List<JobRule>? = null) {
        dataLock.withLock {
            spawnsBySpecies = normalizeMapKeys(syncedSpawns)
            evolutionsBySpecies = normalizeMapKeys(syncedEvolutions)
            speciesInfo = normalizeMapKeys(syncedSpeciesInfo)
            jobRules = syncedJobRules ?: emptyList()
            hasServerSync = true
            rebuildDerivedData()
            dataVersion++

            loadState = if (spawnsBySpecies.isNotEmpty() || evolutionsBySpecies.isNotEmpty()) {
                LoadState.FULLY_LOADED
            } else {
                LoadState.PARTIAL
            }
        }
        val jobMsg = if (jobRules.isNotEmpty()) ", ${jobRules.size} job rules" else ""
        DebugLog.info("Applied server sync: ${syncedSpawns.size} species with spawns, " +
            "${syncedEvolutions.size} with evolutions, ${syncedSpeciesInfo.size} with info$jobMsg")

        RecipeViewerReloader.scheduleReload()

        if (loadState != LoadState.FULLY_LOADED) {
            ensureLoadedAsync()
        }
    }

    /** Accept job rules from Cobbleworkers' own network packet (independent of CobbleDex server sync) */
    fun applyJobRules(rules: List<JobRule>) {
        dataLock.withLock {
            jobRules = rules
            dataVersion++
        }
        DebugLog.info("Applied ${rules.size} job rules from Cobbleworkers packet")
        RecipeViewerReloader.scheduleReload()
    }

    private fun rebuildDerivedData() {
        val reverseMap = mutableMapOf<String, MutableList<EvolutionInfo>>()
        for ((_, evolutions) in evolutionsBySpecies) {
            for (evo in evolutions) {
                val normalizedTo = SpeciesNameNormalizer.normalize(evo.toSpecies)
                reverseMap.getOrPut(normalizedTo) { mutableListOf() }.add(evo)
            }
        }
        evolutionsToSpecies = reverseMap

        val allNames = mutableSetOf<String>()
        allNames.addAll(spawnsBySpecies.keys)
        allNames.addAll(evolutionsBySpecies.keys)
        for ((_, evos) in evolutionsBySpecies) {
            for (evo in evos) allNames.add(SpeciesNameNormalizer.normalize(evo.toSpecies))
        }
        allNames.addAll(speciesInfo.keys)
        allNames.addAll(obtainmentBySpecies.keys)
        allNames.addAll(fossilsBySpecies.keys)

        val runtimeCount = try { PokemonSpecies.implemented.count() } catch (_: Exception) { 0 }
        if (runtimeCount > 0) {
            try {
                for (species in PokemonSpecies.implemented) {
                    allNames.add(SpeciesNameNormalizer.normalize(species.name))
                }
            } catch (e: Exception) {
                DebugLog.warn("Species enumeration interrupted: ${e.message}")
            }
        }

        val dropIndex = mutableMapOf<String, MutableList<String>>()
        for ((species, info) in speciesInfo) {
            val drops = info.drops ?: continue
            for (drop in drops) {
                dropIndex.getOrPut(drop.itemId) { mutableListOf() }.add(species)
            }
        }
        dropsByItem = dropIndex

        allSpeciesNames = allNames.sortedWith(
            compareBy<String> {
                val dex = speciesInfo[it]?.nationalDexNumber ?: 0
                if (dex == 0) Int.MAX_VALUE else dex
            }.thenBy { it }
        )
    }

    /**
     * Fill in missing move data from JarDataCache when the runtime API
     * didn't provide egg/tutor/tm/level-up moves (common on dedicated-server clients).
     */
    private fun enrichWithJarMoves() {
        if (!JarDataCache.hasCachedMoves()) return
        if (speciesInfo.isEmpty()) return
        val jarMoves = JarDataCache.getCachedMoves()

        val enriched = speciesInfo.toMutableMap()
        var enrichCount = 0

        for ((species, info) in enriched) {
            val jarData = jarMoves[species] ?: continue
            val needsEnrichment = info.levelUpMoves == null || info.eggMoves == null ||
                info.tutorMoves == null || info.tmMoves == null
            if (!needsEnrichment) continue

            val resolvedLevelUp = if (info.levelUpMoves == null && jarData.levelUp.isNotEmpty()) {
                jarData.levelUp.entries.sortedBy { it.key }.mapNotNull { (level, names) ->
                    val moves = names.mapNotNull { resolveMoveByName(it) }
                    if (moves.isEmpty()) null else LevelUpMove(level, moves)
                }.ifEmpty { null }
            } else info.levelUpMoves

            val resolvedEgg = if (info.eggMoves == null && jarData.egg.isNotEmpty()) {
                jarData.egg.mapNotNull { resolveMoveByName(it) }.ifEmpty { null }
            } else info.eggMoves

            val resolvedTutor = if (info.tutorMoves == null && jarData.tutor.isNotEmpty()) {
                jarData.tutor.mapNotNull { resolveMoveByName(it) }.ifEmpty { null }
            } else info.tutorMoves

            val resolvedTm = if (info.tmMoves == null && jarData.tm.isNotEmpty()) {
                jarData.tm.mapNotNull { resolveMoveByName(it) }.ifEmpty { null }
            } else info.tmMoves

            enriched[species] = info.copy(
                levelUpMoves = resolvedLevelUp,
                eggMoves = resolvedEgg,
                tutorMoves = resolvedTutor,
                tmMoves = resolvedTm,
            )
            enrichCount++
        }

        if (enrichCount > 0) {
            speciesInfo = enriched
            DebugLog.info("Enriched $enrichCount species with JAR-cached move data")
        }
    }

    private fun resolveMoveByName(name: String): MoveDetail? {
        return try {
            val template = Moves.getByName(name) ?: return null
            MoveDetail(
                name = template.name,
                type = try { template.elementalType.name.lowercase() } catch (_: Exception) { "normal" },
                category = try { template.damageCategory.name } catch (_: Exception) { "PHYSICAL" },
                power = try { template.power.toInt() } catch (_: Exception) { 0 },
                accuracy = try { template.accuracy.toInt() } catch (_: Exception) { 0 },
                pp = try { template.pp } catch (_: Exception) { 0 },
            )
        } catch (_: Exception) { null }
    }

    private fun <T> normalizeMapKeys(map: Map<String, T>): Map<String, T> {
        val result = mutableMapOf<String, T>()
        for ((key, value) in map) {
            val normalized = SpeciesNameNormalizer.normalize(key)
            // If there's a collision, merge lists if applicable
            val existing = result[normalized]
            if (existing != null && existing is List<*> && value is List<*>) {
                @Suppress("UNCHECKED_CAST")
                result[normalized] = (existing + value) as T
            } else {
                result[normalized] = value
            }
        }
        return result
    }

    fun getSpawnsFor(species: String): List<SpawnInfo> = spawnsBySpecies[SpeciesNameNormalizer.normalize(species)] ?: emptyList()

    fun getEvolutionsFrom(species: String): List<EvolutionInfo> = evolutionsBySpecies[SpeciesNameNormalizer.normalize(species)] ?: emptyList()

    fun getEvolutionsTo(species: String): List<EvolutionInfo> = evolutionsToSpecies[SpeciesNameNormalizer.normalize(species)] ?: emptyList()

    fun getSpeciesInfo(species: String): EvolutionDataLoader.SpeciesBasicInfo? = speciesInfo[SpeciesNameNormalizer.normalize(species)]

    fun getObtainmentFor(species: String): List<ObtainmentInfo> = obtainmentBySpecies[SpeciesNameNormalizer.normalize(species)] ?: emptyList()

    fun getFossilsFor(species: String): List<FossilCombo> = fossilsBySpecies[SpeciesNameNormalizer.normalize(species)] ?: emptyList()

    fun getSpeciesDroppingItem(itemId: String): List<String> = dropsByItem[itemId] ?: emptyList()

    fun getJobsFor(species: String): List<JobMatch> {
        if (jobRules.isEmpty()) return emptyList()
        val info = getSpeciesInfo(species) ?: return emptyList()
        val allMoves = JobDataLoader.collectAllMoves(info)
        val allAbilities = JobDataLoader.collectAllAbilities(info)
        return JobDataLoader.evaluateJobs(
            jobRules, info.primaryType, info.secondaryType,
            allAbilities, allMoves, species
        )
    }

    fun hasJobRules(): Boolean = jobRules.isNotEmpty()
}
