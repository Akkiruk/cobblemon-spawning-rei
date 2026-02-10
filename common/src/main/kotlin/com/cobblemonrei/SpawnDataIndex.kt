package com.cobblemonrei

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import java.util.concurrent.atomic.AtomicBoolean

@Suppress("ObjectPropertyName")
object SpawnDataIndex {

    enum class LoadState { NOT_LOADED, PARTIAL, FULLY_LOADED }

    @Volatile
    var loadState = LoadState.NOT_LOADED
        private set

    private val isLoading = AtomicBoolean(false)

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
    var allSpeciesNames: List<String> = emptyList()
        private set

    fun isFullyLoaded(): Boolean = loadState == LoadState.FULLY_LOADED

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

    fun loadAll() {
        if (!isLoading.compareAndSet(false, true)) return
        try {
            doLoad()
        } catch (e: Exception) {
            DebugLog.warn("Data load failed: ${e.message}")
        } finally {
            isLoading.set(false)
        }
    }

    private fun doLoad() {
        DebugLog.reset()
        spawnsBySpecies = SpawnDataLoader.loadFromAllSources()

        val runtimeCount = try { PokemonSpecies.implemented.count() } catch (_: Exception) { 0 }

        if (runtimeCount > 0) {
            try {
                evolutionsBySpecies = EvolutionDataLoader.loadFromRuntime()
            } catch (e: Exception) {
                DebugLog.warn("Runtime evolution load failed: ${e.message}")
                evolutionsBySpecies = emptyMap()
            }

            try {
                speciesInfo = EvolutionDataLoader.loadSpeciesBasicInfoFromRuntime()
            } catch (e: Exception) {
                DebugLog.warn("Runtime species info load failed: ${e.message}")
                speciesInfo = emptyMap()
            }
        } else {
            DebugLog.warn("PokemonSpecies.implemented empty, spawn data only")
            evolutionsBySpecies = emptyMap()
            speciesInfo = emptyMap()
        }

        val reverseMap = mutableMapOf<String, MutableList<EvolutionInfo>>()
        for ((_, evolutions) in evolutionsBySpecies) {
            for (evo in evolutions) {
                reverseMap.getOrPut(evo.toSpecies) { mutableListOf() }.add(evo)
            }
        }
        evolutionsToSpecies = reverseMap

        val allNames = mutableSetOf<String>()
        allNames.addAll(spawnsBySpecies.keys)
        allNames.addAll(evolutionsBySpecies.keys)
        for ((_, evos) in evolutionsBySpecies) {
            for (evo in evos) allNames.add(evo.toSpecies)
        }
        allNames.addAll(speciesInfo.keys)

        if (runtimeCount > 0) {
            try {
                for (species in PokemonSpecies.implemented) {
                    allNames.add(species.name.lowercase())
                }
            } catch (_: Exception) {}
        }

        allSpeciesNames = allNames.sortedWith(
            compareBy<String> {
                val dex = speciesInfo[it]?.nationalDexNumber ?: 0
                if (dex == 0) Int.MAX_VALUE else dex
            }.thenBy { it }
        )

        loadState = if (runtimeCount > 0) LoadState.FULLY_LOADED else LoadState.PARTIAL

        DebugLog.info(
            "Load complete (${loadState.name}): ${allSpeciesNames.size} species " +
            "(${speciesInfo.count { it.value.nationalDexNumber > 0 }} with dex, " +
            "${spawnsBySpecies.size} with spawns, ${evolutionsBySpecies.size} with evolutions)"
        )
    }

    fun getSpawnsFor(species: String): List<SpawnInfo> = spawnsBySpecies[species.lowercase()] ?: emptyList()

    fun getEvolutionsFrom(species: String): List<EvolutionInfo> = evolutionsBySpecies[species.lowercase()] ?: emptyList()

    fun getEvolutionsTo(species: String): List<EvolutionInfo> = evolutionsToSpecies[species.lowercase()] ?: emptyList()

    fun getSpeciesInfo(species: String): EvolutionDataLoader.SpeciesBasicInfo? = speciesInfo[species.lowercase()]
}
