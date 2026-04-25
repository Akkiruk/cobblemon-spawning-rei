package com.cobbledex

data class CobbleDexDataSnapshot(
    val loadState: SpawnDataIndex.LoadState = SpawnDataIndex.LoadState.NOT_LOADED,
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
    val spawnSourceTier: DataSourceTier = DataSourceTier.UNKNOWN,
    val evolutionSourceTier: DataSourceTier = DataSourceTier.UNKNOWN,
    val speciesInfoSourceTier: DataSourceTier = DataSourceTier.UNKNOWN,
    val obtainmentSourceTier: DataSourceTier = DataSourceTier.UNKNOWN,
    val fossilSourceTier: DataSourceTier = DataSourceTier.UNKNOWN,
    val ridingSourceTier: DataSourceTier = DataSourceTier.UNKNOWN,
    val dataVersion: Long = 0,
) {
    val hasData: Boolean get() = allSpeciesNames.isNotEmpty()
    val isFullyLoaded: Boolean get() = loadState == SpawnDataIndex.LoadState.FULLY_LOADED
}