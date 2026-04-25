package com.cobbledex

data class SpawnRecipeData(
    val speciesName: String,
    val spawn: SpawnInfo,
    val mergedFormVariants: List<String> = emptyList(),
    val bucketIndex: Int = 1,
    val bucketTotal: Int = 1
)

data class ObtainmentRecipeData(
    val speciesName: String,
    val formAspects: Set<String> = emptySet(),
    val obtainment: ObtainmentInfo,
    val entryIndex: Int = 1,
    val entryTotal: Int = 1
)

data class TypeChartRecipeData(
    val speciesName: String,
    val primaryType: String,
    val secondaryType: String?,
    val weaknesses: Map<String, Float>,
    val resistances: Map<String, Float>,
    val immunities: List<String>
)

data class NatureRecipeData(
    val natures: List<NatureInfo>,
    val pageIndex: Int = 0,
    val pageTotal: Int = 1
)
