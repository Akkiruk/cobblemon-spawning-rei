package com.cobblemonrei.emi

import com.cobblemonrei.CobblemonSpawningMod
import com.cobblemonrei.DebugLog
import com.cobblemonrei.PokemonItemCache
import com.cobblemonrei.RecipeBuilder
import com.cobblemonrei.SpawnDataIndex
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
        val OBTAINMENT_CATEGORY = EmiRecipeCategory(
            ResourceLocation.fromNamespaceAndPath(CobblemonSpawningMod.MOD_ID, "emi_obtainment"),
            EmiStack.of(Items.NETHER_STAR)
        )
        val DROP_CATEGORY = EmiRecipeCategory(
            ResourceLocation.fromNamespaceAndPath(CobblemonSpawningMod.MOD_ID, "emi_drops"),
            EmiStack.of(Items.DIAMOND)
        )
    }

    override fun register(registry: EmiRegistry) {
        SpawnDataIndex.ensureLoaded()
        val config = CobblemonSpawningConfig.get()

        registry.addCategory(SPAWN_CATEGORY)
        registry.addWorkstation(SPAWN_CATEGORY, EmiStack.of(Items.GRASS_BLOCK))

        if (config.showEvolutions) {
            registry.addCategory(EVOLUTION_CATEGORY)
            registry.addWorkstation(EVOLUTION_CATEGORY, EmiStack.of(Items.EXPERIENCE_BOTTLE))
        }

        if (config.showObtainment) {
            registry.addCategory(OBTAINMENT_CATEGORY)
            registry.addWorkstation(OBTAINMENT_CATEGORY, EmiStack.of(Items.NETHER_STAR))
        }

        if (config.showDrops) {
            registry.addCategory(DROP_CATEGORY)
            registry.addWorkstation(DROP_CATEGORY, EmiStack.of(Items.DIAMOND))
        }

        var comparisonSet = false
        var speciesCount = 0
        var spawnCount = 0

        for (species in SpawnDataIndex.allSpeciesNames) {
            val stack = pokemonStack(species) ?: continue

            if (!comparisonSet) {
                registry.setDefaultComparison(stack, Comparison.compareComponents())
                comparisonSet = true
            }
            registry.addEmiStack(stack)
            speciesCount++

            val spawns = SpawnDataIndex.getSpawnsFor(species)
            if (spawns.isNotEmpty()) {
                for (recipe in RecipeBuilder.buildSpawnRecipes(species, spawns).map { EmiSpawnRecipe(it) }) {
                    registry.addRecipe(recipe)
                    spawnCount++
                }
            }
        }
        DebugLog.info("EMI: Registered $speciesCount Pokémon stacks, $spawnCount spawn recipes")

        if (config.showEvolutions) {
            val evoRecipes = RecipeBuilder.buildAllEvolutionRecipes().map { EmiEvolutionRecipe(it) }
            for (recipe in evoRecipes) registry.addRecipe(recipe)
            DebugLog.info("EMI: Registered ${evoRecipes.size} evolution recipes")
        }

        if (config.showObtainment) {
            val obtainRecipes = RecipeBuilder.buildAllObtainmentRecipes().map { EmiObtainmentRecipe(it) }
            for (recipe in obtainRecipes) registry.addRecipe(recipe)
            DebugLog.info("EMI: Registered ${obtainRecipes.size} obtainment recipes")
        }

        if (config.showDrops) {
            val dropRecipes = RecipeBuilder.buildAllDropRecipes().map { EmiDropRecipe(it) }
            for (recipe in dropRecipes) registry.addRecipe(recipe)
            DebugLog.info("EMI: Registered ${dropRecipes.size} drop recipes")
        }
    }

    private fun pokemonStack(speciesName: String): EmiStack? {
        val item = PokemonItemCache.getItem(speciesName) ?: return null
        return if (!item.isEmpty) EmiStack.of(item) else null
    }
}
