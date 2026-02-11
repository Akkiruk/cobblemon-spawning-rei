package com.cobblemonrei.emi

import com.cobblemonrei.CobblemonSpawningMod
import com.cobblemonrei.DebugLog
import com.cobblemonrei.SpawnDataIndex
import com.cobblemonrei.SpawnDisplayHelper
import com.cobblemonrei.SpawnInfo
import com.cobblemonrei.config.CobblemonSpawningConfig
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.item.PokemonItem
import dev.emi.emi.api.EmiPlugin
import dev.emi.emi.api.EmiRegistry
import dev.emi.emi.api.recipe.EmiRecipeCategory
import dev.emi.emi.api.stack.Comparison
import dev.emi.emi.api.stack.EmiStack
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.Items

open class CobblemonEMIPlugin : EmiPlugin {

    companion object {
        val SPAWN_CATEGORY = EmiRecipeCategory(
            ResourceLocation.fromNamespaceAndPath(CobblemonSpawningMod.MOD_ID, "emi_spawns"),
            EmiStack.of(Items.GRASS_BLOCK)
        )
        val EVOLUTION_CATEGORY = EmiRecipeCategory(
            ResourceLocation.fromNamespaceAndPath(CobblemonSpawningMod.MOD_ID, "emi_evolution"),
            EmiStack.of(Items.EXPERIENCE_BOTTLE)
        )
    }

    override fun register(registry: EmiRegistry) {
        SpawnDataIndex.ensureLoaded()

        registry.addCategory(SPAWN_CATEGORY)
        registry.addWorkstation(SPAWN_CATEGORY, EmiStack.of(Items.GRASS_BLOCK))

        if (CobblemonSpawningConfig.get().showEvolutions) {
            registry.addCategory(EVOLUTION_CATEGORY)
            registry.addWorkstation(EVOLUTION_CATEGORY, EmiStack.of(Items.EXPERIENCE_BOTTLE))
        }

        var comparisonSet = false
        var speciesCount = 0
        var spawnCount = 0

        for ((species, spawns) in SpawnDataIndex.spawnsBySpecies) {
            if (spawns.isEmpty()) continue
            val stack = pokemonStack(species) ?: continue

            if (!comparisonSet) {
                registry.setDefaultComparison(stack, Comparison.compareComponents())
                comparisonSet = true
            }
            registry.addEmiStack(stack)
            speciesCount++

            val recipes = buildSpawnRecipes(species, spawns)
            for (recipe in recipes) {
                registry.addRecipe(recipe)
                spawnCount++
            }
        }
        DebugLog.info("EMI: Registered $speciesCount Pokémon stacks, $spawnCount spawn recipes")

        if (CobblemonSpawningConfig.get().showEvolutions) {
            val evoRecipes = buildAllEvolutionRecipes()
            for (recipe in evoRecipes) {
                registry.addRecipe(recipe)
            }
            DebugLog.info("EMI: Registered ${evoRecipes.size} evolution recipes")
        }
    }

    private fun pokemonStack(speciesName: String): EmiStack? {
        return try {
            val species = PokemonSpecies.getByName(speciesName) ?: return null
            EmiStack.of(PokemonItem.from(species))
        } catch (e: Exception) {
            DebugLog.once("emi-stack-$speciesName") { "Failed to create EmiStack for $speciesName: ${e.message}" }
            null
        }
    }

    // --- Spawn recipe builders (same merge/sort logic as JEI) ---

    private fun buildSpawnRecipes(species: String, spawns: List<SpawnInfo>): List<EmiSpawnRecipe> {
        val merged = SpawnDisplayHelper.mergeVariantSpawns(spawns)
        val sorted = merged.sortedWith(
            compareBy<SpawnDisplayHelper.MergedSpawn> { SpawnDisplayHelper.bucketSortOrder(it.spawn.bucket) }
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
                EmiSpawnRecipe(species, ms.spawn, ms.formVariants, idx, bucketCounts[b]!!)
            } catch (e: Exception) {
                DebugLog.once("emi-spawn-$species-${ms.spawn.id}") { "Failed: ${e.message}" }
                null
            }
        }
    }

    private fun buildAllEvolutionRecipes(): List<EmiEvolutionRecipe> {
        return SpawnDisplayHelper.deduplicateEvolutions(SpawnDataIndex.evolutionsBySpecies)
            .map { (evo, idx, total) -> EmiEvolutionRecipe(evo, idx, total) }
    }
}
