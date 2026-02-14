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
        val STATS_CATEGORY = EmiRecipeCategory(
            ResourceLocation.fromNamespaceAndPath(CobblemonSpawningMod.MOD_ID, "emi_stats"),
            EmiStack.of(Items.BOOK)
        )
        val POKEDEX_INFO_CATEGORY = EmiRecipeCategory(
            ResourceLocation.fromNamespaceAndPath(CobblemonSpawningMod.MOD_ID, "emi_pokedex_info"),
            EmiStack.of(Items.WRITABLE_BOOK)
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

        if (config.showStats) {
            registry.addCategory(STATS_CATEGORY)
            registry.addWorkstation(STATS_CATEGORY, EmiStack.of(Items.BOOK))
        }

        if (config.showPokedexInfo) {
            registry.addCategory(POKEDEX_INFO_CATEGORY)
            registry.addWorkstation(POKEDEX_INFO_CATEGORY, EmiStack.of(Items.WRITABLE_BOOK))
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

        if (config.showStats) {
            val statsRecipes = RecipeBuilder.buildAllStatsRecipes().map { EmiStatsRecipe(it) }
            for (recipe in statsRecipes) registry.addRecipe(recipe)
            DebugLog.info("EMI: Registered ${statsRecipes.size} stats recipes")
        }

        if (config.showPokedexInfo) {
            val pokedexRecipes = RecipeBuilder.buildAllPokedexInfoRecipes().map { EmiPokedexInfoRecipe(it) }
            for (recipe in pokedexRecipes) registry.addRecipe(recipe)
            DebugLog.info("EMI: Registered ${pokedexRecipes.size} pokédex info recipes")
        }
    }

    private fun pokemonStack(speciesName: String): EmiStack? {
        val item = PokemonItemCache.getItem(speciesName) ?: return null
        return if (!item.isEmpty) EmiStack.of(item) else null
    }
}
