package com.cobbledex

object RecipeBuilder {

    fun buildSpawnRecipes(species: String, spawns: List<SpawnInfo>): List<SpawnRecipeData> {
        return SpawnDisplayHelper.buildSortedSpawns(spawns).mapNotNull { entry ->
            try {
                SpawnRecipeData(species, entry.spawn, entry.formVariants, entry.bucketIndex, entry.bucketTotal)
            } catch (e: Exception) {
                DebugLog.once("recipe-spawn-$species-${entry.spawn.id}") { "Failed: ${e.message}" }
                null
            }
        }
    }

    fun buildAllEvolutionRecipes(): List<EvolutionRecipeData> {
        return SpawnDisplayHelper.deduplicateEvolutions(SpawnDataIndex.evolutionsBySpecies)
            .map { (evo, idx, total) -> EvolutionRecipeData(evo, idx, total) }
    }

    fun buildAllObtainmentRecipes(): List<ObtainmentRecipeData> {
        val recipes = mutableListOf<ObtainmentRecipeData>()
        for ((species, obtainments) in SpawnDataIndex.obtainmentBySpecies) {
            if (obtainments.isEmpty()) continue
            obtainments.forEachIndexed { i, info ->
                recipes.add(ObtainmentRecipeData(species, info, i + 1, obtainments.size))
            }
        }
        return recipes
    }

    fun buildEvolutionsFor(evos: List<EvolutionInfo>): List<EvolutionRecipeData> {
        if (evos.isEmpty()) return emptyList()
        val unique = evos.distinctBy { it.id }
        val grouped = unique.groupBy { it.fromSpecies }
        return unique.map { evo ->
            val siblings = grouped[evo.fromSpecies] ?: listOf(evo)
            EvolutionRecipeData(evo, siblings.indexOf(evo) + 1, siblings.size)
        }
    }

    fun buildObtainmentsFor(species: String, obtainments: List<ObtainmentInfo>): List<ObtainmentRecipeData> {
        if (obtainments.isEmpty()) return emptyList()
        return obtainments.mapIndexed { i, info ->
            ObtainmentRecipeData(species, info, i + 1, obtainments.size)
        }
    }

    fun buildAllDropRecipes(): List<DropRecipeData> {
        val recipes = mutableListOf<DropRecipeData>()
        for ((species, info) in SpawnDataIndex.speciesInfo) {
            val drops = info.drops ?: continue
            if (drops.isEmpty()) continue
            recipes.add(DropRecipeData(species, drops))
        }
        return recipes
    }

    fun buildDropsFor(speciesName: String): List<DropRecipeData> {
        val info = SpawnDataIndex.getSpeciesInfo(speciesName) ?: return emptyList()
        val drops = info.drops ?: return emptyList()
        if (drops.isEmpty()) return emptyList()
        return listOf(DropRecipeData(speciesName, drops))
    }

    fun buildAllStatsRecipes(): List<StatsRecipeData> {
        val recipes = mutableListOf<StatsRecipeData>()
        for ((species, info) in SpawnDataIndex.speciesInfo) {
            val stats = info.baseStats ?: continue
            val bst = info.baseStatTotal ?: continue
            if (stats.isEmpty()) continue
            recipes.add(StatsRecipeData(species, stats, bst, info.primaryType, info.secondaryType, info.evYield))
        }
        return recipes
    }

    fun buildStatsFor(speciesName: String): StatsRecipeData? {
        val info = SpawnDataIndex.getSpeciesInfo(speciesName) ?: return null
        val stats = info.baseStats ?: return null
        val bst = info.baseStatTotal ?: return null
        if (stats.isEmpty()) return null
        return StatsRecipeData(speciesName, stats, bst, info.primaryType, info.secondaryType, info.evYield)
    }

    fun buildAllPokedexInfoRecipes(): List<PokedexInfoRecipeData> {
        val recipes = mutableListOf<PokedexInfoRecipeData>()
        for ((species, info) in SpawnDataIndex.speciesInfo) {
            recipes.add(buildPokedexInfoFrom(species, info))
        }
        return recipes
    }

    fun buildPokedexInfoFor(speciesName: String): PokedexInfoRecipeData? {
        val info = SpawnDataIndex.getSpeciesInfo(speciesName) ?: return null
        return buildPokedexInfoFrom(speciesName, info)
    }

    private fun buildPokedexInfoFrom(species: String, info: EvolutionDataLoader.SpeciesBasicInfo): PokedexInfoRecipeData {
        return PokedexInfoRecipeData(
            speciesName = species,
            abilities = info.abilities ?: emptyList(),
            hiddenAbility = info.hiddenAbility,
            eggGroups = info.eggGroups ?: emptyList(),
            maleRatio = info.maleRatio,
            eggCycles = info.eggCycles,
            catchRate = info.catchRate,
            baseFriendship = info.baseFriendship,
            experienceGroup = info.experienceGroup,
            baseExperienceYield = info.baseExperienceYield,
            height = info.height,
            weight = info.weight,
            description = info.description,
            shoulderMountable = info.shoulderMountable
        )
    }

    private const val MOVES_PER_PAGE = 14

    fun buildAllMovesRecipes(): List<MovesRecipeData> {
        val recipes = mutableListOf<MovesRecipeData>()
        for ((species, info) in SpawnDataIndex.speciesInfo) {
            recipes.addAll(buildMovesPages(species, info))
        }
        return recipes
    }

    fun buildMovesFor(speciesName: String): List<MovesRecipeData> {
        val info = SpawnDataIndex.getSpeciesInfo(speciesName) ?: return emptyList()
        return buildMovesPages(speciesName, info)
    }

    private fun buildMovesPages(species: String, info: EvolutionDataLoader.SpeciesBasicInfo): List<MovesRecipeData> {
        val levelUp = info.levelUpMoves ?: emptyList()
        val egg = info.eggMoves ?: emptyList()
        val tutor = info.tutorMoves ?: emptyList()
        if (levelUp.isEmpty() && egg.isEmpty() && tutor.isEmpty()) return emptyList()

        // Flatten level-up moves into individual lines for pagination
        val totalLevelUpLines = levelUp.sumOf { it.moves.size }
        val eggLines = if (egg.isNotEmpty()) 1 + ((egg.size + 2) / 3) else 0 // header + rows of 3
        val tutorLines = if (tutor.isNotEmpty()) 1 + ((tutor.size + 2) / 3) else 0
        val totalLines = totalLevelUpLines + eggLines + tutorLines

        if (totalLines <= MOVES_PER_PAGE) {
            return listOf(MovesRecipeData(species, levelUp, egg, tutor, 1, 1))
        }

        // Paginate level-up moves
        val pages = mutableListOf<MovesRecipeData>()
        var remaining = levelUp.toMutableList()
        var pageNum = 1
        val pageCount = ((totalLevelUpLines + MOVES_PER_PAGE - 1) / MOVES_PER_PAGE).coerceAtLeast(1) +
            (if (eggLines + tutorLines > 0) 1 else 0)

        while (remaining.isNotEmpty()) {
            val pageMoves = mutableListOf<LevelUpMove>()
            var linesUsed = 0
            while (remaining.isNotEmpty() && linesUsed < MOVES_PER_PAGE) {
                val entry = remaining.first()
                if (linesUsed + entry.moves.size <= MOVES_PER_PAGE) {
                    pageMoves.add(entry)
                    linesUsed += entry.moves.size
                    remaining.removeAt(0)
                } else {
                    val canFit = MOVES_PER_PAGE - linesUsed
                    pageMoves.add(LevelUpMove(entry.level, entry.moves.take(canFit)))
                    remaining[0] = LevelUpMove(entry.level, entry.moves.drop(canFit))
                    linesUsed = MOVES_PER_PAGE
                }
            }
            pages.add(MovesRecipeData(species, pageMoves, emptyList(), emptyList(), pageNum, pageCount))
            pageNum++
        }

        // Last page with egg/tutor moves
        if (egg.isNotEmpty() || tutor.isNotEmpty()) {
            pages.add(MovesRecipeData(species, emptyList(), egg, tutor, pageNum, pageCount))
        }

        return pages
    }

    // --- Fossil recipes ---

    fun buildAllFossilRecipes(): List<FossilRecipeData> {
        val recipes = mutableListOf<FossilRecipeData>()
        for ((species, combos) in SpawnDataIndex.fossilsBySpecies) {
            for (combo in combos) {
                recipes.add(FossilRecipeData(species, combo.fossilItems, combo.extraTags))
            }
        }
        return recipes
    }

    fun buildFossilsFor(speciesName: String): List<FossilRecipeData> {
        val combos = SpawnDataIndex.getFossilsFor(speciesName)
        if (combos.isEmpty()) return emptyList()
        return combos.map { FossilRecipeData(speciesName, it.fossilItems, it.extraTags) }
    }

    // --- Type chart recipes ---

    fun buildAllTypeChartRecipes(): List<TypeChartRecipeData> {
        val recipes = mutableListOf<TypeChartRecipeData>()
        for ((species, info) in SpawnDataIndex.speciesInfo) {
            val matchups = TypeChart.getMatchups(info.primaryType, info.secondaryType)
            recipes.add(TypeChartRecipeData(
                species, info.primaryType, info.secondaryType,
                matchups.weaknesses, matchups.resistances, matchups.immunities
            ))
        }
        return recipes
    }

    fun buildTypeChartFor(speciesName: String): TypeChartRecipeData? {
        val info = SpawnDataIndex.getSpeciesInfo(speciesName) ?: return null
        val matchups = TypeChart.getMatchups(info.primaryType, info.secondaryType)
        return TypeChartRecipeData(
            speciesName, info.primaryType, info.secondaryType,
            matchups.weaknesses, matchups.resistances, matchups.immunities
        )
    }

    // --- Nature table recipes ---

    fun buildNatureRecipes(): List<NatureRecipeData> {
        return listOf(NatureRecipeData(NatureData.NATURES))
    }
}
