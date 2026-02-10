package com.cobblemonrei.jei

import com.cobblemonrei.CobblemonSpawningMod
import com.cobblemonrei.DebugLog
import com.cobblemonrei.EvolutionInfo
import com.cobblemonrei.SpawnDataIndex
import com.cobblemonrei.SpawnInfo
import com.cobblemonrei.config.CobblemonSpawningConfig
import com.cobblemonrei.jei.evolution.JeiEvolutionCategory
import com.cobblemonrei.jei.evolution.JeiEvolutionRecipe
import com.cobblemonrei.jei.spawn.JeiSpawnCategory
import com.cobblemonrei.jei.spawn.JeiSpawnRecipe
import mezz.jei.api.IModPlugin
import mezz.jei.api.JeiPlugin
import mezz.jei.api.registration.*
import net.minecraft.resources.ResourceLocation

@JeiPlugin
open class CobblemonJEIPlugin : IModPlugin {

    override fun getPluginUid(): ResourceLocation =
        ResourceLocation.fromNamespaceAndPath(CobblemonSpawningMod.MOD_ID, "jei_plugin")

    @Suppress("DEPRECATION")
    override fun registerIngredients(registration: IModIngredientRegistration) {
        SpawnDataIndex.ensureLoaded()

        val renderer = PokemonIngredientRenderer()
        val allIngredients = SpawnDataIndex.allSpeciesNames
            .filter { renderer.canRender(it) }
            .map { PokemonIngredient(it) }

        registration.register(PokemonIngredientType, allIngredients, PokemonIngredientHelper(), renderer)
        DebugLog.info("JEI: Registered ${allIngredients.size} Pokémon ingredients")
    }

    override fun registerCategories(registration: IRecipeCategoryRegistration) {
        val guiHelper = registration.jeiHelpers.guiHelper
        registration.addRecipeCategories(JeiSpawnCategory(guiHelper))
        if (CobblemonSpawningConfig.get().showEvolutions) {
            registration.addRecipeCategories(JeiEvolutionCategory(guiHelper))
        }
        DebugLog.info("JEI: Categories registered")
    }

    override fun registerRecipes(registration: IRecipeRegistration) {
        SpawnDataIndex.ensureLoaded()

        val spawnRecipes = mutableListOf<JeiSpawnRecipe>()
        for ((species, spawns) in SpawnDataIndex.spawnsBySpecies) {
            if (spawns.isEmpty()) continue
            spawnRecipes.addAll(buildSpawnRecipes(species, spawns))
        }
        registration.addRecipes(JeiSpawnCategory.RECIPE_TYPE, spawnRecipes)
        DebugLog.info("JEI: Registered ${spawnRecipes.size} spawn recipes")

        if (CobblemonSpawningConfig.get().showEvolutions) {
            val evoRecipes = buildAllEvolutionRecipes()
            registration.addRecipes(JeiEvolutionCategory.RECIPE_TYPE, evoRecipes)
            DebugLog.info("JEI: Registered ${evoRecipes.size} evolution recipes")
        }
    }

    override fun registerRecipeCatalysts(registration: IRecipeCatalystRegistration) {
        // Pokémon ingredients act as catalysts for their own spawn/evo lookups
        // JEI handles this via ingredient focus, no explicit catalysts needed
    }

    // --- Recipe builders (matching REI logic) ---

    private fun buildSpawnRecipes(species: String, spawns: List<SpawnInfo>): List<JeiSpawnRecipe> {
        val merged = mergeVariantSpawns(spawns)
        val sorted = merged.sortedWith(
            compareBy<MergedSpawn> { JeiSpawnCategory.bucketSortOrder(it.spawn.bucket) }
                .thenBy { it.spawn.context }
                .thenByDescending { it.spawn.weight }
        )
        val bucketCounts = sorted.groupBy { it.spawn.bucket.lowercase() }.mapValues { it.value.size }
        val bucketIdx = mutableMapOf<String, Int>()
        return sorted.mapNotNull { ms ->
            val b = ms.spawn.bucket.lowercase()
            val idx = (bucketIdx[b] ?: 0) + 1
            bucketIdx[b] = idx
            try {
                JeiSpawnRecipe(species, ms.spawn, ms.formVariants, idx, bucketCounts[b]!!)
            } catch (e: Exception) {
                DebugLog.once("jei-spawn-$species-${ms.spawn.id}") { "Failed: ${e.message}" }
                null
            }
        }
    }

    private fun buildAllEvolutionRecipes(): List<JeiEvolutionRecipe> {
        val seen = mutableSetOf<String>()
        val allEvos = mutableListOf<EvolutionInfo>()
        for ((_, evos) in SpawnDataIndex.evolutionsBySpecies) {
            for (evo in evos) {
                if (evo.id in seen) continue
                seen.add(evo.id)
                allEvos.add(evo)
            }
        }
        val grouped = allEvos.groupBy { it.fromSpecies }
        return allEvos.map { evo ->
            val siblings = grouped[evo.fromSpecies] ?: listOf(evo)
            JeiEvolutionRecipe(evo, siblings.indexOf(evo) + 1, siblings.size)
        }
    }

    // --- Variant merge (same as REI) ---

    private data class MergedSpawn(val spawn: SpawnInfo, val formVariants: List<String>)

    private fun mergeVariantSpawns(spawns: List<SpawnInfo>): List<MergedSpawn> {
        val groups = spawns.groupBy { spawnMergeKey(it) }
        return groups.map { (_, group) ->
            val primary = group.first()
            val variants = group
                .filter { it.formAspects.isNotBlank() }
                .map {
                    it.formAspects
                        .replace("region_bias=", "")
                        .replace("_", " ")
                        .split(" ")
                        .filter { w -> w.isNotBlank() }
                        .joinToString(" ") { w -> w.replaceFirstChar { c -> c.uppercase() } }
                }
                .distinct()
            MergedSpawn(primary, variants)
        }
    }

    private fun spawnMergeKey(s: SpawnInfo): String {
        return "${s.pokemon}|${s.bucket}|${s.weight}|${s.levelRange}|${s.context}|" +
            "${s.biomes.sorted()}|${s.timeRange}|${s.weather}|${s.dimensions.sorted()}|" +
            "${s.structures.sorted()}|${s.canSeeSky}|${s.minLight}|${s.maxLight}|" +
            "${s.minSkyLight}|${s.maxSkyLight}|${s.minY}|${s.maxY}|" +
            "${s.neededNearbyBlocks.sorted()}|${s.neededBaseBlocks.sorted()}|" +
            "${s.moonPhase}|${s.presets.sorted()}|${s.fluid}"
    }
}

