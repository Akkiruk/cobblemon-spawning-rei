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
        evolutionsBySpecies = EvolutionDataLoader.loadFromCobblemonJar()
        speciesInfo = EvolutionDataLoader.loadSpeciesBasicInfo()

        // Build reverse evolution index
        val reverseMap = mutableMapOf<String, MutableList<EvolutionInfo>>()
        for ((_, evolutions) in evolutionsBySpecies) {
            for (evo in evolutions) {
                reverseMap.getOrPut(evo.toSpecies) { mutableListOf() }.add(evo)
            }
        }
        evolutionsToSpecies = reverseMap

        // Build sorted species list (by dex number)
        allSpeciesNames = speciesInfo.entries
            .sortedBy { it.value.nationalDexNumber }
            .map { it.key }
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
