package com.cobbledex

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobbledex.network.DataSerializer
import java.nio.file.Path
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@Suppress("ObjectPropertyName")
object SpawnDataIndex {

    enum class LoadState { NOT_LOADED, PARTIAL, FULLY_LOADED }
    enum class DataSource { NONE, LOCAL, SERVER }

    @Volatile
    var loadState = LoadState.NOT_LOADED
        private set

    @Volatile
    var dataSource = DataSource.NONE
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
    var allSpeciesNames: List<String> = emptyList()
        private set

    @Volatile
    var dataVersion: Long = 0
        private set

    fun isFullyLoaded(): Boolean = loadState == LoadState.FULLY_LOADED

    fun hasData(): Boolean = allSpeciesNames.isNotEmpty()

    fun computeFingerprint(): String {
        return DataSerializer.computeFingerprint(spawnsBySpecies, evolutionsBySpecies, speciesInfo)
    }

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

    fun loadAll(extraDatapacksDir: Path? = null) {
        if (!dataLock.tryLock(10, java.util.concurrent.TimeUnit.SECONDS)) {
            DebugLog.warn("Data load skipped: lock held by another thread for >10s")
            return
        }
        try {
            cancelPendingLoad()
            doLoad(extraDatapacksDir)
        } catch (e: Exception) {
            DebugLog.warn("Data load failed: ${e.message}")
        } finally {
            dataLock.unlock()
        }
    }

    private fun loadAllAsync(extraDatapacksDir: Path? = null) {
        val prev = loadFuture
        if (prev != null && !prev.isDone) return
        loadFuture = executor.submit {
            dataLock.lock()
            try {
                doLoad(extraDatapacksDir)
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

    private fun doLoad(extraDatapacksDir: Path? = null) {
        DebugLog.reset()
        PokemonItemCache.reset()
        SpawnDataLoader.invalidateCache()
        spawnsBySpecies = normalizeMapKeys(SpawnDataLoader.loadFromAllSources(extraDatapacksDir))

        val runtimeCount = try { PokemonSpecies.implemented.count() } catch (_: Exception) { 0 }

        if (runtimeCount > 0) {
            try {
                evolutionsBySpecies = normalizeMapKeys(EvolutionDataLoader.loadFromRuntime())
            } catch (e: Exception) {
                DebugLog.warn("Runtime evolution load failed: ${e.message}")
                evolutionsBySpecies = emptyMap()
            }

            try {
                speciesInfo = normalizeMapKeys(EvolutionDataLoader.loadSpeciesBasicInfoFromRuntime())
            } catch (e: Exception) {
                DebugLog.warn("Runtime species info load failed: ${e.message}")
                speciesInfo = emptyMap()
            }
        } else {
            DebugLog.warn("PokemonSpecies.implemented empty, spawn data only")
            evolutionsBySpecies = emptyMap()
            speciesInfo = emptyMap()
        }

        try {
            obtainmentBySpecies = normalizeMapKeys(ObtainmentDataLoader.loadFromAllSources(
                SpawnDataLoader.getModRootPaths(), extraDatapacksDir
            ))
        } catch (e: Exception) {
            DebugLog.warn("Obtainment data load failed: ${e.message}")
            obtainmentBySpecies = emptyMap()
        }

        rebuildDerivedData()

        loadState = if (runtimeCount > 0) LoadState.FULLY_LOADED else LoadState.PARTIAL
        dataSource = DataSource.LOCAL
        dataVersion++

        DebugLog.info(
            "Load complete (${loadState.name}, ${dataSource.name}): ${allSpeciesNames.size} species " +
            "(${speciesInfo.count { it.value.nationalDexNumber > 0 }} with dex, " +
            "${spawnsBySpecies.size} with spawns, ${evolutionsBySpecies.size} with evolutions, " +
            "${obtainmentBySpecies.size} with obtainment)"
        )
    }

    /**
     * Replace all data with server-provided data.
     * Called from ClientDataReceiver when the full payload arrives.
     * Acquires dataLock to prevent races with an in-progress local load.
     */
    fun applyServerData(
        spawns: Map<String, List<SpawnInfo>>,
        evolutions: Map<String, List<EvolutionInfo>>,
        species: Map<String, EvolutionDataLoader.SpeciesBasicInfo>
    ) {
        cancelPendingLoad()
        dataLock.withLock {
            spawnsBySpecies = normalizeMapKeys(spawns)
            evolutionsBySpecies = normalizeMapKeys(evolutions)
            speciesInfo = normalizeMapKeys(species)
            // Obtainment stays locally loaded — no server sync needed for bundled/datapack entries
            if (obtainmentBySpecies.isEmpty()) {
                try {
                    obtainmentBySpecies = normalizeMapKeys(ObtainmentDataLoader.loadFromAllSources(SpawnDataLoader.getModRootPaths()))
                } catch (_: Exception) {}
            }
            rebuildDerivedData()
            loadState = LoadState.FULLY_LOADED
            dataSource = DataSource.SERVER
            dataVersion++

            DebugLog.info(
                "Server data applied: ${allSpeciesNames.size} species " +
                "(${spawns.size} with spawns, ${evolutions.size} with evolutions)"
            )
        }
    }

    /** Mark data stale on disconnect, keep as warm cache for instant availability on reconnect */
    fun onDisconnect() {
        cancelPendingLoad()
        PokemonItemCache.reset()
        dataLock.withLock {
            loadState = LoadState.NOT_LOADED
            dataSource = DataSource.NONE
        }
        DebugLog.info("Marked data stale on disconnect (${allSpeciesNames.size} species cached)")
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

        allSpeciesNames = allNames.sortedWith(
            compareBy<String> {
                val dex = speciesInfo[it]?.nationalDexNumber ?: 0
                if (dex == 0) Int.MAX_VALUE else dex
            }.thenBy { it }
        )
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
}
