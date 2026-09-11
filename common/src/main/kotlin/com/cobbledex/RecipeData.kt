package com.cobbledex

data class SpawnRecipeData(
    val speciesName: String,
    val spawn: SpawnInfo,
    val mergedFormVariants: List<String> = emptyList(),
    val habitats: List<HabitatContext> = emptyList(),
    /** Position of this spawn among all of the species' spawns, and the total. */
    val spawnIndex: Int = 1,
    val spawnTotal: Int = 1,
)

/** The "N ways to spawn" summary shown as page 1 when a species has more than one spawn. */
data class SpawnIndexRecipeData(
    val speciesName: String,
    val rows: List<SpawnIndexRow>,
    val hiddenCount: Int = 0,
    /** Friendly name of the biome where this species has its best overall odds; null if unknown. */
    val bestBiome: String? = null,
)

data class SpawnIndexRow(
    val bucket: String,
    val locator: String,
    val time: SpawnPageModel.TimeLabel,
    val pokeSnack: Boolean,
    val herd: Boolean,
    val fishing: Boolean = false,
    val formNote: String? = null,
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

data class TmRecipeData(
    val tm: TmInfo,
    /** Number of species that can learn this move (any method), for a summary line. */
    val learnerCount: Int,
)

data class MarkRecipeData(
    val marks: List<MarkInfo>,
    val pageIndex: Int = 0,
    val pageTotal: Int = 1,
)

data class HerdRecipeData(
    val herd: HerdInfo,
    /** The species the viewer navigated from, marked with a ◄ on the roster; null when browsing. */
    val fromSpecies: String? = null,
)
