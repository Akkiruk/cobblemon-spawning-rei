package com.cobbledex

import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@Suppress("ObjectPropertyName")
object SpawnDataIndex {

    enum class LoadState { NOT_LOADED, PARTIAL, FULLY_LOADED }

    private data class DataSnapshot(
        val spawnsBySpecies: Map<String, List<SpawnInfo>> = emptyMap(),
        val evolutionsBySpecies: Map<String, List<EvolutionInfo>> = emptyMap(),
        val evolutionsToSpecies: Map<String, List<EvolutionInfo>> = emptyMap(),
        val speciesInfo: Map<String, EvolutionDataLoader.SpeciesBasicInfo> = emptyMap(),
        val obtainmentBySpecies: Map<String, List<ObtainmentInfo>> = emptyMap(),
        val fossilsBySpecies: Map<String, List<FossilCombo>> = emptyMap(),
        val dropsByItem: Map<String, List<String>> = emptyMap(),
        val speciesByTmMove: Map<String, List<String>> = emptyMap(),
        val jobRules: List<JobRule> = emptyList(),
        val ridingBySpecies: Map<String, RidingInfo> = emptyMap(),
        val allSpeciesNames: List<String> = emptyList(),
    )

    @Volatile
    var loadState = LoadState.NOT_LOADED
        private set

    @Volatile
    private var snapshot = DataSnapshot()

    private val dataLock = ReentrantLock()
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "CobbleDex-DataLoad").apply { isDaemon = true }
    }
    private var loadFuture: Future<*>? = null
    private val loadGeneration = AtomicLong(0)

    val spawnsBySpecies: Map<String, List<SpawnInfo>> get() = snapshot.spawnsBySpecies

    val evolutionsBySpecies: Map<String, List<EvolutionInfo>> get() = snapshot.evolutionsBySpecies

    val evolutionsToSpecies: Map<String, List<EvolutionInfo>> get() = snapshot.evolutionsToSpecies

    val speciesInfo: Map<String, EvolutionDataLoader.SpeciesBasicInfo> get() = snapshot.speciesInfo

    val obtainmentBySpecies: Map<String, List<ObtainmentInfo>> get() = snapshot.obtainmentBySpecies

    val fossilsBySpecies: Map<String, List<FossilCombo>> get() = snapshot.fossilsBySpecies

    val dropsByItem: Map<String, List<String>> get() = snapshot.dropsByItem

    val speciesByTmMove: Map<String, List<String>> get() = snapshot.speciesByTmMove

    val jobRules: List<JobRule> get() = snapshot.jobRules

    val ridingBySpecies: Map<String, RidingInfo> get() = snapshot.ridingBySpecies

    val allSpeciesNames: List<String> get() = snapshot.allSpeciesNames

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

    fun hasData(): Boolean = loadState != LoadState.NOT_LOADED && allSpeciesNames.isNotEmpty()

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
            doLoad(loadGeneration.incrementAndGet())
        } catch (_: CancellationException) {
            DebugLog.info("Data load cancelled")
        } catch (e: Exception) {
            DebugLog.warn("Data load failed: ${e.message}")
        } finally {
            dataLock.unlock()
        }
    }

    private fun loadAllAsync() {
        val prev = loadFuture
        if (prev != null && !prev.isDone) return
        val generation = loadGeneration.incrementAndGet()
        loadFuture = executor.submit {
            try {
                dataLock.lockInterruptibly()
                doLoad(generation)
            } catch (_: InterruptedException) {
                DebugLog.info("Async data load interrupted before completion")
                Thread.currentThread().interrupt()
            } catch (_: CancellationException) {
                DebugLog.info("Async data load superseded before completion")
            } catch (e: Exception) {
                DebugLog.warn("Async data load failed: ${e.message}")
            } finally {
                if (dataLock.isHeldByCurrentThread) {
                    dataLock.unlock()
                }
            }
        }
    }

    private fun cancelPendingLoad() {
        loadGeneration.incrementAndGet()
        loadFuture?.cancel(true)
        loadFuture = null
    }

    private fun ensureGenerationCurrent(generation: Long) {
        if (generation != loadGeneration.get() || Thread.currentThread().isInterrupted) {
            throw CancellationException("Superseded load generation $generation")
        }
    }

    private fun clearActiveDataLocked(clearJobRules: Boolean) {
        snapshot = if (clearJobRules) {
            DataSnapshot()
        } else {
            DataSnapshot(jobRules = snapshot.jobRules)
        }
    }

    private fun doLoad(generation: Long) {
        ensureGenerationCurrent(generation)
        DebugLog.reset()
        PokemonItemCache.reset()
        PokemonSpriteService.reset()

        val current = snapshot

        if (hasServerSync) {
            // Server sync is authoritative for multiplayer-visible data.
            // Only fall back for data the server did not provide.
            var fossils = current.fossilsBySpecies
            if (fossilsBySpecies.isEmpty() && JarDataCache.hasCachedFossils()) {
                DebugLog.info("Using JarDataCache fossils (${JarDataCache.getCachedFossils().size} species)")
                fossils = normalizeMapKeys(JarDataCache.getCachedFossils())
            }

            ensureGenerationCurrent(generation)
            val published = buildSnapshot(
                spawnsBySpecies = current.spawnsBySpecies,
                evolutionsBySpecies = current.evolutionsBySpecies,
                speciesInfo = current.speciesInfo,
                obtainmentBySpecies = current.obtainmentBySpecies,
                fossilsBySpecies = fossils,
                jobRules = current.jobRules,
                ridingBySpecies = current.ridingBySpecies,
            )
            snapshot = published
            if (loadState != LoadState.FULLY_LOADED) {
                loadState = LoadState.FULLY_LOADED
            }
            dataVersion++

            DebugLog.info(
                "Load complete (server-synced, ${loadState.name}): ${published.allSpeciesNames.size} species " +
                "(${published.speciesInfo.count { it.value.nationalDexNumber > 0 }} with dex, " +
                "${published.spawnsBySpecies.size} with spawns, ${published.evolutionsBySpecies.size} with evolutions, " +
                "${published.obtainmentBySpecies.size} with obtainment)"
            )
            return
        }

        // --- Baseline: start with JarDataCache (loaded on game launch) ---
        // Wait briefly for cache if it's still initializing
        JarDataCache.awaitReady(5_000)
        ensureGenerationCurrent(generation)

        // Try Cobblemon's runtime spawn pool (populated in singleplayer or by server)
        SpawnDataLoader.invalidateCache()
        val runtimeSpawns = normalizeMapKeys(SpawnDataLoader.loadFromRuntime())
        val spawns = if (runtimeSpawns.isNotEmpty()) {
            runtimeSpawns
        } else if (JarDataCache.hasCachedSpawns()) {
            DebugLog.info("Using JarDataCache spawns (${JarDataCache.getCachedSpawns().size} species)")
            normalizeMapKeys(JarDataCache.getCachedSpawns())
        } else {
            emptyMap()
        }
        ensureGenerationCurrent(generation)

        val runtimeCount = try { PokemonSpecies.implemented.count() } catch (_: Exception) { 0 }

        if (runtimeCount > 0) {
            // Try runtime evolutions (works in singleplayer, empty on dedicated servers)
            var evolutions: Map<String, List<EvolutionInfo>>
            try {
                evolutions = normalizeMapKeys(EvolutionDataLoader.loadFromRuntime())
            } catch (e: Exception) {
                DebugLog.warn("Runtime evolution load failed: ${e.message}")
                evolutions = emptyMap()
            }

            // Fall back to JarDataCache evolutions if runtime is empty
            if (evolutions.isEmpty() && JarDataCache.hasCachedEvolutions()) {
                DebugLog.info("Using JarDataCache evolutions (${JarDataCache.getCachedEvolutions().size} species)")
                evolutions = normalizeMapKeys(JarDataCache.getCachedEvolutions())
            }

            var info: Map<String, EvolutionDataLoader.SpeciesBasicInfo>
            try {
                info = normalizeMapKeys(EvolutionDataLoader.loadSpeciesBasicInfoFromRuntime())
            } catch (e: Exception) {
                DebugLog.warn("Runtime species info load failed: ${e.message}")
                info = emptyMap()
            }
            val obtainment = try {
                normalizeMapKeys(ObtainmentDataLoader.loadFromAllSources(
                    SpawnDataLoader.getModRootPaths()
                ))
            } catch (e: Exception) {
                DebugLog.warn("Obtainment data load failed: ${e.message}")
                emptyMap()
            }
            ensureGenerationCurrent(generation)
            val fossils = if (JarDataCache.hasCachedFossils()) {
                normalizeMapKeys(JarDataCache.getCachedFossils())
            } else {
                emptyMap()
            }
            val enrichedInfo = enrichWithJarMoves(info)
            val riding = try {
                normalizeMapKeys(RidingDataLoader.loadFromRuntime())
            } catch (e: Exception) {
                DebugLog.warn("Riding data load failed: ${e.message}")
                emptyMap()
            }
            ensureGenerationCurrent(generation)

            val published = buildSnapshot(
                spawnsBySpecies = spawns,
                evolutionsBySpecies = evolutions,
                speciesInfo = enrichedInfo,
                obtainmentBySpecies = obtainment,
                fossilsBySpecies = fossils,
                jobRules = current.jobRules,
                ridingBySpecies = riding,
            )
            snapshot = published

            val hasEvolutions = published.evolutionsBySpecies.isNotEmpty()
            val hasSpawns = published.spawnsBySpecies.isNotEmpty()
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

            if (published.spawnsBySpecies.isNotEmpty() && published.speciesInfo.isNotEmpty()) {
                val orphanSpawns = published.spawnsBySpecies.keys.filter { it !in published.speciesInfo }
                if (orphanSpawns.isNotEmpty()) {
                    DebugLog.info("Spawn-only species (no speciesInfo): ${orphanSpawns.take(20).joinToString(", ")}${if (orphanSpawns.size > 20) " (+${orphanSpawns.size - 20} more)" else ""}")
                }
            }

            DebugLog.info(
                "Load complete (${loadState.name}): ${published.allSpeciesNames.size} species " +
                "(${published.speciesInfo.count { it.value.nationalDexNumber > 0 }} with dex, " +
                "${published.spawnsBySpecies.size} with spawns, ${published.evolutionsBySpecies.size} with evolutions, " +
                "${published.obtainmentBySpecies.size} with obtainment)"
            )
        } else {
            DebugLog.warn("PokemonSpecies.implemented empty, spawn data only")
            val evolutions = if (JarDataCache.hasCachedEvolutions()) {
                normalizeMapKeys(JarDataCache.getCachedEvolutions())
            } else {
                emptyMap()
            }
            val obtainment = try {
                normalizeMapKeys(ObtainmentDataLoader.loadFromAllSources(
                    SpawnDataLoader.getModRootPaths()
                ))
            } catch (e: Exception) {
                DebugLog.warn("Obtainment data load failed: ${e.message}")
                emptyMap()
            }
            ensureGenerationCurrent(generation)
            val fossils = if (JarDataCache.hasCachedFossils()) {
                normalizeMapKeys(JarDataCache.getCachedFossils())
            } else {
                emptyMap()
            }
            val riding = try {
                normalizeMapKeys(RidingDataLoader.loadFromRuntime())
            } catch (e: Exception) {
                DebugLog.warn("Riding data load failed: ${e.message}")
                emptyMap()
            }
            ensureGenerationCurrent(generation)

            val published = buildSnapshot(
                spawnsBySpecies = spawns,
                evolutionsBySpecies = evolutions,
                speciesInfo = emptyMap(),
                obtainmentBySpecies = obtainment,
                fossilsBySpecies = fossils,
                jobRules = current.jobRules,
                ridingBySpecies = riding,
            )
            snapshot = published
            loadState = if (published.evolutionsBySpecies.isEmpty() && published.spawnsBySpecies.isEmpty()) {
                LoadState.PARTIAL
            } else {
                LoadState.PARTIAL
            }
            dataVersion++

            DebugLog.info(
                "Load complete (${loadState.name}): ${published.allSpeciesNames.size} species " +
                "(${published.speciesInfo.count { it.value.nationalDexNumber > 0 }} with dex, " +
                "${published.spawnsBySpecies.size} with spawns, ${published.evolutionsBySpecies.size} with evolutions, " +
                "${published.obtainmentBySpecies.size} with obtainment)"
            )
        }
    }

    /** Mark data stale on disconnect. Cached JAR data is preserved —
     *  only runtime/server data is cleared. */
    fun onDisconnect() {
        cancelPendingLoad()
        PokemonItemCache.reset()
        PokemonSpriteService.reset()
        RecipeViewerReloader.reset()
        emptyEvoRetries = 0
        hasServerSync = false

        dataLock.withLock {
            clearActiveDataLocked(clearJobRules = true)
            loadState = LoadState.NOT_LOADED
            dataVersion++
        }
        DebugLog.info("Cleared active CobbleDex session data on disconnect")
    }

    /** Accept all data synced from the server via networking */
    fun applyServerSync(syncedSpawns: Map<String, List<SpawnInfo>>,
                        syncedEvolutions: Map<String, List<EvolutionInfo>>,
                        syncedSpeciesInfo: Map<String, EvolutionDataLoader.SpeciesBasicInfo>,
                        syncedObtainment: Map<String, List<ObtainmentInfo>> = emptyMap(),
                        syncedRiding: Map<String, RidingInfo> = emptyMap(),
                        syncedJobRules: List<JobRule>? = null,
                        syncedFossils: Map<String, List<FossilCombo>>? = null) {
        cancelPendingLoad()
        dataLock.withLock {
            val current = snapshot
            snapshot = buildSnapshot(
                spawnsBySpecies = normalizeMapKeys(syncedSpawns),
                evolutionsBySpecies = normalizeMapKeys(syncedEvolutions),
                speciesInfo = normalizeMapKeys(syncedSpeciesInfo),
                obtainmentBySpecies = normalizeMapKeys(syncedObtainment),
                fossilsBySpecies = syncedFossils?.let(::normalizeMapKeys) ?: current.fossilsBySpecies,
                jobRules = syncedJobRules ?: emptyList(),
                ridingBySpecies = normalizeMapKeys(syncedRiding),
            )
            hasServerSync = true
            dataVersion++

            loadState = if (snapshot.spawnsBySpecies.isNotEmpty() || snapshot.evolutionsBySpecies.isNotEmpty()) {
                LoadState.FULLY_LOADED
            } else {
                LoadState.PARTIAL
            }
        }
        val jobMsg = if (jobRules.isNotEmpty()) ", ${jobRules.size} job rules" else ""
        val totalSpawnEntries = syncedSpawns.values.sumOf { it.size }
        CobbleDexMod.LOGGER.info("[CobbleDex] Server sync received: ${syncedSpawns.size} species ($totalSpawnEntries spawn entries), " +
            "${syncedEvolutions.size} evolutions, ${syncedSpeciesInfo.size} species info, " +
            "${syncedObtainment.size} obtainment, ${syncedRiding.size} riding$jobMsg — scheduling recipe viewer reload")
        DebugLog.info("Applied server sync: ${syncedSpawns.size} species with spawns, " +
            "${syncedEvolutions.size} with evolutions, ${syncedSpeciesInfo.size} with info, " +
            "${syncedObtainment.size} with obtainment, ${syncedRiding.size} with riding$jobMsg " +
            "(loadState=${loadState.name}, dataVersion=$dataVersion)")

        RecipeViewerReloader.scheduleReload()

        if (loadState != LoadState.FULLY_LOADED) {
            ensureLoadedAsync()
        }
    }

    /** Accept job rules from Cobbleworkers' own network packet (independent of CobbleDex server sync) */
    fun applyJobRules(rules: List<JobRule>) {
        dataLock.withLock {
            snapshot = snapshot.copy(jobRules = rules)
            dataVersion++
        }
        DebugLog.info("Applied ${rules.size} job rules from Cobbleworkers packet")
        RecipeViewerReloader.scheduleReload()
    }

    private fun buildSnapshot(
        spawnsBySpecies: Map<String, List<SpawnInfo>>,
        evolutionsBySpecies: Map<String, List<EvolutionInfo>>,
        speciesInfo: Map<String, EvolutionDataLoader.SpeciesBasicInfo>,
        obtainmentBySpecies: Map<String, List<ObtainmentInfo>>,
        fossilsBySpecies: Map<String, List<FossilCombo>>,
        jobRules: List<JobRule>,
        ridingBySpecies: Map<String, RidingInfo>,
    ): DataSnapshot {
        val reverseMap = mutableMapOf<String, MutableList<EvolutionInfo>>()
        for ((_, evolutions) in evolutionsBySpecies) {
            for (evo in evolutions) {
                val normalizedTo = SpeciesNameNormalizer.normalize(evo.toSpecies)
                reverseMap.getOrPut(normalizedTo) { mutableListOf() }.add(evo)
            }
        }

        // Backfill: any evolution key that doesn't have a speciesInfo entry gets a
        // thin inherited entry from its base species so it's properly marked as a form
        val enrichedSpeciesInfo = speciesInfo.toMutableMap()
        var backfilled = 0
        for (key in evolutionsBySpecies.keys) {
            if (key in enrichedSpeciesInfo) continue
            // Try to resolve the base species from the evolution's fromSpecies
            val evos = evolutionsBySpecies[key] ?: continue
            val baseName = SpeciesNameNormalizer.normalize(evos.firstOrNull()?.fromSpecies ?: continue)
            val baseInfo = enrichedSpeciesInfo[baseName] ?: continue
            enrichedSpeciesInfo[key] = baseInfo.copy(
                name = key,
                baseSpeciesName = baseName,
                formAspects = evos.firstOrNull()?.fromAspects ?: emptySet()
            )
            backfilled++
        }
        if (backfilled > 0) {
            DebugLog.info("Backfilled $backfilled orphan evolution keys into speciesInfo")
        }

        val allNames = mutableSetOf<String>()
        allNames.addAll(spawnsBySpecies.keys)
        allNames.addAll(evolutionsBySpecies.keys)
        for ((_, evos) in evolutionsBySpecies) {
            for (evo in evos) allNames.add(SpeciesNameNormalizer.normalize(evo.toSpecies))
        }
        allNames.addAll(speciesInfo.keys)
        allNames.addAll(obtainmentBySpecies.keys)
        allNames.addAll(fossilsBySpecies.keys)
        allNames.addAll(ridingBySpecies.keys)

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
        for ((species, info) in enrichedSpeciesInfo) {
            val drops = info.drops ?: continue
            for (drop in drops) {
                dropIndex.getOrPut(drop.itemId) { mutableListOf() }.add(species)
            }
        }

        val tmIndex = mutableMapOf<String, MutableList<String>>()
        for ((species, info) in enrichedSpeciesInfo) {
            val tms = info.tmMoves ?: continue
            for (move in tms) {
                tmIndex.getOrPut(move.name.lowercase()) { mutableListOf() }.add(species)
            }
        }
        val sortedNames = allNames.sortedWith(
            compareBy<String> {
                val dex = enrichedSpeciesInfo[it]?.nationalDexNumber ?: 0
                if (dex == 0) Int.MAX_VALUE else dex
            }.thenBy { it }
        )

        return DataSnapshot(
            spawnsBySpecies = spawnsBySpecies,
            evolutionsBySpecies = evolutionsBySpecies,
            evolutionsToSpecies = reverseMap,
            speciesInfo = enrichedSpeciesInfo,
            obtainmentBySpecies = obtainmentBySpecies,
            fossilsBySpecies = fossilsBySpecies,
            dropsByItem = dropIndex,
            speciesByTmMove = tmIndex,
            jobRules = jobRules,
            ridingBySpecies = ridingBySpecies,
            allSpeciesNames = sortedNames,
        )
    }

    /**
     * Fill in missing move data from JarDataCache when the runtime API
     * didn't provide egg/tutor/tm/level-up moves (common on dedicated-server clients).
     */
    private fun enrichWithJarMoves(
        speciesInfo: Map<String, EvolutionDataLoader.SpeciesBasicInfo>
    ): Map<String, EvolutionDataLoader.SpeciesBasicInfo> {
        if (!JarDataCache.hasCachedMoves()) return speciesInfo
        if (speciesInfo.isEmpty()) return speciesInfo
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
            DebugLog.info("Enriched $enrichCount species with JAR-cached move data")
        }

        return enriched
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

    fun isForm(species: String): Boolean = getSpeciesInfo(species)?.isForm == true

    fun getFormsOf(baseSpecies: String): List<EvolutionDataLoader.SpeciesBasicInfo> {
        val normalized = SpeciesNameNormalizer.normalize(baseSpecies)
        return speciesInfo.values.filter {
            it.isForm && SpeciesNameNormalizer.normalize(it.baseSpeciesName!!) == normalized
        }
    }

    fun getBaseOf(formSpecies: String): String? =
        getSpeciesInfo(formSpecies)?.baseSpeciesName

    fun getSpeciesWithTmMove(moveName: String): List<String> =
        speciesByTmMove[moveName.lowercase()] ?: emptyList()

    fun getRidingFor(species: String): RidingInfo? = ridingBySpecies[SpeciesNameNormalizer.normalize(species)]
}
