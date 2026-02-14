package com.cobblemonrei

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
            recipes.add(StatsRecipeData(species, stats, bst, info.primaryType, info.secondaryType))
        }
        return recipes
    }

    fun buildStatsFor(speciesName: String): StatsRecipeData? {
        val info = SpawnDataIndex.getSpeciesInfo(speciesName) ?: return null
        val stats = info.baseStats ?: return null
        val bst = info.baseStatTotal ?: return null
        if (stats.isEmpty()) return null
        return StatsRecipeData(speciesName, stats, bst, info.primaryType, info.secondaryType)
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
            description = info.description
        )
    }
}
