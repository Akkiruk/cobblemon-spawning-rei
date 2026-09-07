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
    val speciesByMove: Map<String, List<String>> = emptyMap(),
    val jobRules: List<JobRule> = emptyList(),
    val ridingBySpecies: Map<String, RidingInfo> = emptyMap(),
    /** Native Cobblemon TMs keyed by lower-cased move id (1.8.0+); empty on older Cobblemon. */
    val tmInfoByMove: Map<String, TmInfo> = emptyMap(),
    /** Pokémon Marks (1.8.0+); empty on older Cobblemon. */
    val marks: List<MarkInfo> = emptyList(),
    val spawnRegionsBySpecies: Map<String, List<SpawnRegionInfo>> = emptyMap(),
    val allSpeciesNames: List<String> = emptyList(),
    val spawnSourceTier: DataSourceTier = DataSourceTier.UNAVAILABLE,
    val evolutionSourceTier: DataSourceTier = DataSourceTier.UNAVAILABLE,
    val speciesInfoSourceTier: DataSourceTier = DataSourceTier.UNAVAILABLE,
    val obtainmentSourceTier: DataSourceTier = DataSourceTier.UNAVAILABLE,
    val fossilSourceTier: DataSourceTier = DataSourceTier.UNAVAILABLE,
    val ridingSourceTier: DataSourceTier = DataSourceTier.UNAVAILABLE,
    val dataVersion: Long = 0,
) {
    val hasData: Boolean get() = allSpeciesNames.isNotEmpty()
    val isFullyLoaded: Boolean get() = loadState == SpawnDataIndex.LoadState.FULLY_LOADED
}