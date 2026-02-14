package com.cobbledex

data class SpawnRecipeData(
    val speciesName: String,
    val spawn: SpawnInfo,
    val mergedFormVariants: List<String> = emptyList(),
    val bucketIndex: Int = 1,
    val bucketTotal: Int = 1
)

data class EvolutionRecipeData(
    val evolution: EvolutionInfo,
    val branchIndex: Int = 0,
    val branchTotal: Int = 0
)

data class ObtainmentRecipeData(
    val speciesName: String,
    val obtainment: ObtainmentInfo,
    val entryIndex: Int = 1,
    val entryTotal: Int = 1
)
