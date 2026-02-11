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
}
