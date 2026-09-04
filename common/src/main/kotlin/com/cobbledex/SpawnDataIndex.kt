package com.cobbledex

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
    private var snapshot: CobbleDexDataSnapshot = CobbleDexDataSnapshot()

    @Volatile
    private var cachedQueriesSnapshot: CobbleDexDataSnapshot? = null

    @Volatile
    private var cachedQueries: CobbleDexDataQueries? = null

    var loadState: LoadState
        get() = snapshot.loadState
        private set(value) { snapshot = snapshot.copy(loadState = value) }

    private val dataLock = ReentrantLock()
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "CobbleDex-DataLoad").apply { isDaemon = true }
    }
    private var loadFuture: Future<*>? = null

    var spawnsBySpecies: Map<String, List<SpawnInfo>>
        get() = snapshot.spawnsBySpecies
        private set(value) { snapshot = snapshot.copy(spawnsBySpecies = value) }

    var evolutionsBySpecies: Map<String, List<EvolutionInfo>>
        get() = snapshot.evolutionsBySpecies
        private set(value) { snapshot = snapshot.copy(evolutionsBySpecies = value) }

    var evolutionsToSpecies: Map<String, List<EvolutionInfo>>
        get() = snapshot.evolutionsToSpecies
        private set(value) { snapshot = snapshot.copy(evolutionsToSpecies = value) }

    var speciesInfo: Map<String, EvolutionDataLoader.SpeciesBasicInfo>
        get() = snapshot.speciesInfo
        private set(value) { snapshot = snapshot.copy(speciesInfo = value) }

    var obtainmentBySpecies: Map<String, List<ObtainmentInfo>>
        get() = snapshot.obtainmentBySpecies
        private set(value) { snapshot = snapshot.copy(obtainmentBySpecies = value) }

    var fossilsBySpecies: Map<String, List<FossilCombo>>
        get() = snapshot.fossilsBySpecies
        private set(value) { snapshot = snapshot.copy(fossilsBySpecies = value) }

    var dropsByItem: Map<String, List<String>>
        get() = snapshot.dropsByItem
        private set(value) { snapshot = snapshot.copy(dropsByItem = value) }

    var speciesByMove: Map<String, List<String>>
        get() = snapshot.speciesByMove
        private set(value) { snapshot = snapshot.copy(speciesByMove = value) }

    var jobRules: List<JobRule>
        get() = snapshot.jobRules
        private set(value) { snapshot = snapshot.copy(jobRules = value) }

    var ridingBySpecies: Map<String, RidingInfo>
        get() = snapshot.ridingBySpecies
        private set(value) { snapshot = snapshot.copy(ridingBySpecies = value) }

    var spawnRegionsBySpecies: Map<String, List<SpawnRegionInfo>>
        get() = snapshot.spawnRegionsBySpecies
        private set(value) { snapshot = snapshot.copy(spawnRegionsBySpecies = value) }

    var allSpeciesNames: List<String>
        get() = snapshot.allSpeciesNames
        private set(value) { snapshot = snapshot.copy(allSpeciesNames = value) }

    var spawnSourceTier: DataSourceTier
        get() = snapshot.spawnSourceTier
        private set(value) { snapshot = snapshot.copy(spawnSourceTier = value) }

    var evolutionSourceTier: DataSourceTier
        get() = snapshot.evolutionSourceTier
        private set(value) { snapshot = snapshot.copy(evolutionSourceTier = value) }

    var speciesInfoSourceTier: DataSourceTier
        get() = snapshot.speciesInfoSourceTier
        private set(value) { snapshot = snapshot.copy(speciesInfoSourceTier = value) }

    var obtainmentSourceTier: DataSourceTier
        get() = snapshot.obtainmentSourceTier
        private set(value) { snapshot = snapshot.copy(obtainmentSourceTier = value) }

    var fossilSourceTier: DataSourceTier
        get() = snapshot.fossilSourceTier
        private set(value) { snapshot = snapshot.copy(fossilSourceTier = value) }

    var ridingSourceTier: DataSourceTier
        get() = snapshot.ridingSourceTier
        private set(value) { snapshot = snapshot.copy(ridingSourceTier = value) }

    var dataVersion: Long
        get() = snapshot.dataVersion
        private set(value) { snapshot = snapshot.copy(dataVersion = value) }

    fun isFullyLoaded(): Boolean = loadState == LoadState.FULLY_LOADED

    fun hasData(): Boolean = allSpeciesNames.isNotEmpty()

    fun spawnRegionsForSpecies(speciesId: String): List<SpawnRegionInfo> =
        spawnRegionsBySpecies[SpeciesNameNormalizer.normalize(speciesId)].orEmpty()

    fun currentSnapshot(): CobbleDexDataSnapshot = snapshot

    fun currentQueries(): CobbleDexDataQueries {
        val current = snapshot
        val cachedSnapshot = cachedQueriesSnapshot
        val cached = cachedQueries
        if (cachedSnapshot === current && cached != null) return cached

        val queries = CobbleDexDataQueries(current)
        cachedQueriesSnapshot = current
        cachedQueries = queries
        return queries
    }

    /** Load now on the calling thread if nothing has been loaded yet. */
    fun ensureLoaded() {
        if (loadState == LoadState.NOT_LOADED) loadAll()
    }

    /**
     * Rebuild off the current Cobblemon data, on the background loader thread.
     *
     * Called by [CobbleDexMod.tickClient] when [CobblemonDataSignal] reports Cobblemon's registries
     * actually changed — which is both the initial `species_sync` landing and any later reload.
     * There is no retry loop: if nothing changed there is nothing to rebuild.
     */
    fun rebuildAsync() {
        loadAllAsync()
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

    /**
     * Rebuild the whole index from the sources available on this client.
     *
     * CobbleDex sends no packets of its own. There is exactly one load path, and it merges
     * **per field** from three sources, best first:
     *
     *  1. [DataSourceTier.COBBLEMON] — Cobblemon's own client registries. On a server these are
     *     what Cobblemon's `species_sync` delivered, so they already reflect the server's
     *     datapacks. This covers base stats, types, abilities, learnsets, drops, forms, riding,
     *     dex numbers and dex-entry keys.
     *  2. [DataSourceTier.LOCAL_FILES] — this client's mod JARs, `datapacks/` and resource packs
     *     ([JarDataCache]). Consulted **only** for what Cobblemon does not put on the wire:
     *     evolutions, pre-evolutions, egg groups/cycles, catch rate, base friendship, EV yield,
     *     base experience yield, spawn pools, and fossil item predicates. (Verified against
     *     Cobblemon 1.7's `Species.encode`/`FormData.encode`: none of those fields are encoded.)
     *  3. [DataSourceTier.BUILT_IN] — tables compiled into CobbleDex (type chart, natures), which
     *     never need a source at all.
     *
     * Fields are never taken from a lower tier when a higher one supplied them, and a lower tier
     * is never rejected wholesale just because a higher one returned something — each gap is
     * filled on its own.
     */
    private fun doLoad() {
        DebugLog.reset()
        PokemonItemCache.reset()

        // The local-file layer is read once at launch on a background thread; give it a moment if
        // a very early rebuild beat it.
        JarDataCache.awaitReady(5_000)

        val speciesCount = try { PokemonSpecies.implemented.count() } catch (_: Exception) { 0 }

        loadSpawns()
        loadSpeciesInfo(speciesCount)
        loadEvolutions(speciesCount)
        loadObtainment()
        loadFossils()
        loadRiding()
        loadSpawnRegions()

        rebuildDerivedData()

        // Cobblemon populates its species registry in one shot, so any species at all means the
        // sync (or the singleplayer datapack load) has completed. No retry bookkeeping: if more
        // data arrives later, CobblemonDataSignal notices and calls us again.
        loadState = if (speciesCount > 0 || allSpeciesNames.isNotEmpty()) {
            LoadState.FULLY_LOADED
        } else {
            LoadState.PARTIAL
        }
        dataVersion++

        // REI reads live data through its dynamic generator, but JEI and EMI register their
        // recipes statically — they must be told the index changed or they keep showing the
        // previous version (or nothing, on the first load).
        RecipeViewerReloader.scheduleReload()

        DebugLog.info(
            "Load complete (${loadState.name}): ${allSpeciesNames.size} species " +
            "(${speciesInfo.count { it.value.nationalDexNumber > 0 }} with dex, " +
            "${spawnsBySpecies.size} with spawns [${spawnSourceTier.displayName}], " +
            "${evolutionsBySpecies.size} with evolutions [${evolutionSourceTier.displayName}], " +
            "${obtainmentBySpecies.size} with obtainment)"
        )
    }

    /**
     * Spawns. Cobblemon never syncs `WORLD_SPAWN_POOL` to clients (it has no packet at all), so
     * the runtime read only succeeds in singleplayer/LAN. On a dedicated server this falls to the
     * local files, which are right whenever the client carries the same spawn packs as the server.
     */
    private fun loadSpawns() {
        SpawnDataLoader.invalidateCache()
        val runtime = normalizeMapKeys(SpawnDataLoader.loadFromRuntime())
        if (runtime.isNotEmpty()) {
            spawnsBySpecies = runtime
            spawnSourceTier = DataSourceTier.COBBLEMON
            return
        }
        if (JarDataCache.hasCachedSpawns()) {
            val local = normalizeMapKeys(JarDataCache.getCachedSpawns())
            DebugLog.info("Spawns from local files (${local.size} species) — Cobblemon syncs no spawn pool")
            spawnsBySpecies = local
            spawnSourceTier = DataSourceTier.LOCAL_FILES
            return
        }
        spawnsBySpecies = emptyMap()
        spawnSourceTier = DataSourceTier.UNAVAILABLE
    }

    /**
     * Species facts. The bulk comes straight from Cobblemon; [SpeciesTraitMerger] then fills the
     * individual fields Cobblemon's `Species.encode` leaves out, per species, from local files.
     */
    private fun loadSpeciesInfo(speciesCount: Int) {
        if (speciesCount <= 0) {
            DebugLog.warn("Cobblemon has no species yet — species info deferred")
            speciesInfo = emptyMap()
            speciesInfoSourceTier = DataSourceTier.UNAVAILABLE
            return
        }

        val runtime = try {
            normalizeMapKeys(EvolutionDataLoader.loadSpeciesBasicInfoFromRuntime())
        } catch (e: Exception) {
            DebugLog.warn("Cobblemon species info read failed: ${e.message}")
            emptyMap()
        }

        if (runtime.isEmpty()) {
            speciesInfo = emptyMap()
            speciesInfoSourceTier = DataSourceTier.UNAVAILABLE
            return
        }

        val merged = SpeciesTraitMerger.fillGaps(runtime)
        speciesInfo = merged.speciesInfo
        speciesInfoSourceTier = DataSourceTier.COBBLEMON
        if (merged.filledFieldCount > 0) {
            DebugLog.info(
                "Filled ${merged.filledFieldCount} species fields from local files across " +
                "${merged.filledSpeciesCount} species (not carried by Cobblemon's species sync)"
            )
        }
    }

    /**
     * Evolutions. `Species.encode` carries no `evolutions`/`preEvolution`, so on a dedicated
     * server Cobblemon's client registry has none and local files are the only source. In
     * singleplayer the runtime has them, but even then a form's own nested evolutions are
     * routinely missing from `FormData`, so local edges are merged in on top rather than skipped.
     */
    private fun loadEvolutions(speciesCount: Int) {
        val runtime = if (speciesCount > 0) {
            try {
                normalizeMapKeys(EvolutionDataLoader.loadFromRuntime())
            } catch (e: Exception) {
                DebugLog.warn("Cobblemon evolution read failed: ${e.message}")
                emptyMap()
            }
        } else emptyMap()

        val local = if (JarDataCache.hasCachedEvolutions()) {
            normalizeMapKeys(JarDataCache.getCachedEvolutions())
        } else emptyMap()

        if (runtime.isEmpty() && local.isEmpty()) {
            evolutionsBySpecies = emptyMap()
            evolutionSourceTier = DataSourceTier.UNAVAILABLE
            return
        }

        if (runtime.isEmpty()) {
            DebugLog.info("Evolutions from local files (${local.size} species) — Cobblemon's species sync omits them")
            evolutionsBySpecies = local
            evolutionSourceTier = DataSourceTier.LOCAL_FILES
            return
        }

        // Runtime had data: keep it and add any edge local files know about that it is missing.
        // Cobblemon's client-side FormData for a species_additions-nested form routinely exposes
        // no evolutions even when the JSON defines a real one (confirmed via /cobbledex evo:
        // Form Funfair's Roggenrola/Boldore "Overgrown" forms define level_up/trade evolutions
        // that never reached the runtime API).
        val merged = runtime.mapValues { it.value.toMutableList() }.toMutableMap()
        var added = 0
        for ((key, localEvos) in local) {
            val existing = merged[key].orEmpty()
            for (evo in localEvos) {
                val alreadyPresent = existing.any {
                    it.fromAspects == evo.fromAspects &&
                        SpeciesNameNormalizer.normalize(it.toSpecies) == SpeciesNameNormalizer.normalize(evo.toSpecies)
                }
                if (!alreadyPresent) {
                    merged.getOrPut(key) { mutableListOf() }.add(evo)
                    added++
                }
            }
        }
        evolutionsBySpecies = merged
        evolutionSourceTier = DataSourceTier.COBBLEMON
        if (added > 0) {
            DebugLog.info("Merged $added evolution edges from local files that Cobblemon's runtime didn't expose")
        }
    }

    /** Special obtainment methods, defined by addon mods and datapacks — never a Cobblemon concept. */
    private fun loadObtainment() {
        try {
            obtainmentBySpecies = normalizeMapKeys(
                ObtainmentDataLoader.loadFromAllSources(SpawnDataLoader.getModRootPaths())
            )
            obtainmentSourceTier = if (obtainmentBySpecies.isEmpty()) DataSourceTier.UNAVAILABLE
                else DataSourcePolicy.preferredSource(obtainmentBySpecies.values.flatten().map { it.source })
        } catch (e: Exception) {
            DebugLog.warn("Obtainment data load failed: ${e.message}")
            obtainmentBySpecies = emptyMap()
            obtainmentSourceTier = DataSourceTier.UNAVAILABLE
        }
    }

    /**
     * Fossils. Cobblemon does sync its fossil registry, but `FossilRegistrySyncPacket.decodeEntry`
     * drops the `ItemPredicate`s, so the client's copy has empty ingredient lists and the local
     * files are the only place the material requirements survive.
     */
    private fun loadFossils() {
        if (JarDataCache.hasCachedFossils()) {
            fossilsBySpecies = normalizeMapKeys(JarDataCache.getCachedFossils())
            fossilSourceTier = DataSourceTier.LOCAL_FILES
        } else {
            fossilsBySpecies = emptyMap()
            fossilSourceTier = DataSourceTier.UNAVAILABLE
        }
    }

    /** Riding properties — fully carried by Cobblemon's species sync. */
    private fun loadRiding() {
        try {
            ridingBySpecies = RidingDataLoader.loadFromRuntime()
            ridingSourceTier = if (ridingBySpecies.isNotEmpty()) DataSourceTier.COBBLEMON
                else DataSourceTier.UNAVAILABLE
        } catch (e: Exception) {
            DebugLog.warn("Riding data load failed: ${e.message}")
            ridingBySpecies = emptyMap()
            ridingSourceTier = DataSourceTier.UNAVAILABLE
        }
    }

    /** Optional CobbleRegions region names — that mod exposes them to the client itself. */
    private fun loadSpawnRegions() {
        spawnRegionsBySpecies = try {
            CobbleRegionsIntegration.regionsBySpecies(spawnsBySpecies.keys)
        } catch (e: Exception) {
            DebugLog.warn("Spawn region lookup failed: ${e.message}")
            emptyMap()
        }
    }

    /**
     * Mark data stale on disconnect. The local-file layer is preserved (it is per-client and
     * doesn't change with the session); everything read out of Cobblemon's registries is dropped,
     * because the next world may have entirely different datapacks.
     */
    fun onDisconnect() {
        cancelPendingLoad()
        PokemonItemCache.reset()
        RecipeViewerReloader.reset()
        jobRules = emptyList()
        spawnRegionsBySpecies = emptyMap()

        dataLock.withLock {
            loadState = LoadState.NOT_LOADED
        }
        DebugLog.info("Marked data stale on disconnect (${allSpeciesNames.size} species cached)")
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
        val result = DerivedDataBuilder.rebuild(snapshot)
        snapshot = result.snapshot
        if (result.backfilledSpeciesInfoCount > 0) {
            DebugLog.info("Backfilled ${result.backfilledSpeciesInfoCount} orphan evolution keys into speciesInfo")
        }
        result.speciesEnumerationError?.let { message ->
            DebugLog.warn("Species enumeration interrupted: $message")
        }
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

    fun getSpawnsFor(species: String): List<SpawnInfo> = currentQueries().getSpawnsFor(species)

    fun getEvolutionsFrom(species: String): List<EvolutionInfo> = currentQueries().getEvolutionsFrom(species)

    fun getEvolutionsTo(species: String): List<EvolutionInfo> = currentQueries().getEvolutionsTo(species)

    fun getSpeciesInfo(species: String): EvolutionDataLoader.SpeciesBasicInfo? = currentQueries().getSpeciesInfo(species)

    fun getObtainmentFor(species: String): List<ObtainmentInfo> = currentQueries().getObtainmentFor(species)

    fun getFossilsFor(species: String): List<FossilCombo> = currentQueries().getFossilsFor(species)

    fun getSpeciesDroppingItem(itemId: String): List<String> = currentQueries().getSpeciesDroppingItem(itemId)

    fun getJobsFor(species: String): List<JobMatch> = currentQueries().getJobsFor(species)

    fun hasJobRules(): Boolean = currentQueries().hasJobRules()

    fun isForm(species: String): Boolean = currentQueries().isForm(species)

    fun materialFormDecision(species: String): MaterialFormPolicy.Decision? = currentQueries().materialFormDecision(species)

    fun shouldSurfaceSpecies(species: String): Boolean = currentQueries().shouldSurfaceSpecies(species)

    fun getFormsOf(baseSpecies: String): List<EvolutionDataLoader.SpeciesBasicInfo> = currentQueries().getFormsOf(baseSpecies)

    fun getBaseOf(formSpecies: String): String? = currentQueries().getBaseOf(formSpecies)

    fun getSpeciesWithMove(moveName: String): List<String> = currentQueries().getSpeciesWithMove(moveName)

    fun getRidingFor(species: String): RidingInfo? = currentQueries().getRidingFor(species)
}
