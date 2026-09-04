package com.cobbledex

data class SpawnRecipeData(
    val speciesName: String,
    val spawn: SpawnInfo,
    val mergedFormVariants: List<String> = emptyList(),
    val bucketIndex: Int = 1,
    val bucketTotal: Int = 1
)

data class PokemonOverviewRecipeData(
    val projection: PokemonPageProjection,
) {
    val speciesName: String get() = projection.speciesName
}

data class EvolutionMethodRecipeData(
    val requirementText: String,
    val itemRequirements: List<EvolutionItemInfo> = emptyList(),
)

data class EvolutionRecipeData(
    val sourceSpeciesName: String,
    val sourceAspects: Set<String> = emptySet(),
    val targetSpeciesName: String? = null,
    val targetAspects: Set<String> = emptySet(),
    val methods: List<EvolutionMethodRecipeData> = emptyList(),
    val pageIndex: Int = 1,
    val pageTotal: Int = 1,
    val totalOutcomes: Int = if (targetSpeciesName == null) 0 else 1,
) {
    val isTerminal: Boolean get() = targetSpeciesName == null
}

data class UnifiedObtainmentRecipeData(
    val speciesName: String,
    val routes: List<ObtainmentRoute>,
    val pageIndex: Int = 1,
    val pageTotal: Int = 1,
    val totalRoutes: Int = routes.size,
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
