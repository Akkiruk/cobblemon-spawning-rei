package com.cobblemonrei.emi

import com.cobblemonrei.CobblemonSpawningMod
import com.cobblemonrei.DebugLog
import com.cobblemonrei.PokemonItemCache
import com.cobblemonrei.SpawnDataIndex
import com.cobblemonrei.SpawnDisplayHelper
import com.cobblemonrei.SpawnInfo
import com.cobblemonrei.config.CobblemonSpawningConfig
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
        val item = PokemonItemCache.getItem(speciesName) ?: return null
        return if (!item.isEmpty) EmiStack.of(item) else null
    }

    // --- Spawn recipe builders (same merge/sort logic as JEI) ---

    private fun buildSpawnRecipes(species: String, spawns: List<SpawnInfo>): List<EmiSpawnRecipe> {
        return SpawnDisplayHelper.buildSortedSpawns(spawns).mapNotNull { entry ->
            try {
                EmiSpawnRecipe(species, entry.spawn, entry.formVariants, entry.bucketIndex, entry.bucketTotal)
            } catch (e: Exception) {
                DebugLog.once("emi-spawn-$species-${entry.spawn.id}") { "Failed: ${e.message}" }
                null
            }
        }
    }

    private fun buildAllEvolutionRecipes(): List<EmiEvolutionRecipe> {
        return SpawnDisplayHelper.deduplicateEvolutions(SpawnDataIndex.evolutionsBySpecies)
            .map { (evo, idx, total) -> EmiEvolutionRecipe(evo, idx, total) }
    }
}
