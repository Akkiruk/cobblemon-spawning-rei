package com.cobblemonrei

object SpawnDataIndex {

    var spawnsBySpecies: Map<String, List<SpawnInfo>> = emptyMap()
        private set

    var evolutionsBySpecies: Map<String, List<EvolutionInfo>> = emptyMap()
        private set

    // Reverse lookup: what evolves INTO this species
    var evolutionsToSpecies: Map<String, List<EvolutionInfo>> = emptyMap()
        private set

    var speciesInfo: Map<String, EvolutionDataLoader.SpeciesBasicInfo> = emptyMap()
        private set

    var allSpeciesNames: List<String> = emptyList()
        private set

    fun loadAll() {
        spawnsBySpecies = SpawnDataLoader.loadFromCobblemonJar()

        // Try runtime API for evolutions and species info
        try {
            evolutionsBySpecies = EvolutionDataLoader.loadFromRuntime()
        } catch (e: Exception) {
            CobblemonSpawningMod.LOGGER.warn("[CobblemonSpawningREI] Runtime evolution load failed: ${e.message}")
            evolutionsBySpecies = emptyMap()
        }

        try {
            speciesInfo = EvolutionDataLoader.loadSpeciesBasicInfoFromRuntime()
        } catch (e: Exception) {
            CobblemonSpawningMod.LOGGER.warn("[CobblemonSpawningREI] Runtime species info load failed: ${e.message}")
            speciesInfo = emptyMap()
        }

        // Build reverse evolution index
        val reverseMap = mutableMapOf<String, MutableList<EvolutionInfo>>()
        for ((_, evolutions) in evolutionsBySpecies) {
            for (evo in evolutions) {
                reverseMap.getOrPut(evo.toSpecies) { mutableListOf() }.add(evo)
            }
        }
        evolutionsToSpecies = reverseMap

        // Collect ALL species from every source
        val allNames = mutableSetOf<String>()
        allNames.addAll(spawnsBySpecies.keys)
        allNames.addAll(evolutionsBySpecies.keys)
        for ((_, evos) in evolutionsBySpecies) {
            for (evo in evos) allNames.add(evo.toSpecies)
        }
        allNames.addAll(speciesInfo.keys)

        // Create placeholder info for species found in spawns but missing from runtime API
        val mutableInfo = speciesInfo.toMutableMap()
        for (name in allNames) {
            if (name !in mutableInfo) {
                mutableInfo[name] = EvolutionDataLoader.SpeciesBasicInfo(
                    name = name,
                    nationalDexNumber = 0,
                    primaryType = "normal",
                    secondaryType = null,
                    catchRate = 45,
                    weight = 0f,
                    height = 0f
                )
            }
        }
        speciesInfo = mutableInfo

        // Sort by dex number; unknown (dex=0) at end, alphabetically
        allSpeciesNames = allNames.sortedWith(
            compareBy<String> {
                val dex = speciesInfo[it]?.nationalDexNumber ?: 0
                if (dex == 0) Int.MAX_VALUE else dex
            }.thenBy { it }
        )

        CobblemonSpawningMod.LOGGER.info(
            "[CobblemonSpawningREI] Total species: ${allSpeciesNames.size} " +
            "(${speciesInfo.count { it.value.nationalDexNumber > 0 }} with dex info, " +
            "${spawnsBySpecies.size} with spawns, ${evolutionsBySpecies.size} with evolutions)"
        )
    }

    fun getSpawnsFor(species: String): List<SpawnInfo> {
        return spawnsBySpecies[species.lowercase()] ?: emptyList()
    }

    fun getEvolutionsFrom(species: String): List<EvolutionInfo> {
        return evolutionsBySpecies[species.lowercase()] ?: emptyList()
    }

    fun getEvolutionsTo(species: String): List<EvolutionInfo> {
        return evolutionsToSpecies[species.lowercase()] ?: emptyList()
    }

    fun getSpeciesInfo(species: String): EvolutionDataLoader.SpeciesBasicInfo? {
        return speciesInfo[species.lowercase()]
    }
}
