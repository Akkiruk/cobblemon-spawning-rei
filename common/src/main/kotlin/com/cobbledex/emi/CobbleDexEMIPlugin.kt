package com.cobbledex.emi

import com.cobbledex.CobbleDexMod
import com.cobbledex.DebugLog
import com.cobbledex.PokemonItemCache
import com.cobbledex.RecipeBuilder
import com.cobbledex.SpawnDataIndex
import com.cobbledex.config.CobbleDexConfig
import dev.emi.emi.api.EmiPlugin
import dev.emi.emi.api.EmiRegistry
import dev.emi.emi.api.recipe.EmiRecipeCategory
import dev.emi.emi.api.stack.Comparison
import dev.emi.emi.api.stack.EmiStack
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.Items

open class CobbleDexEMIPlugin : EmiPlugin {

    companion object {
        val SPAWN_CATEGORY = EmiRecipeCategory(
            ResourceLocation.fromNamespaceAndPath(CobbleDexMod.MOD_ID, "emi_spawns"),
            EmiStack.of(Items.GRASS_BLOCK)
        )
        val EVOLUTION_CATEGORY = EmiRecipeCategory(
            ResourceLocation.fromNamespaceAndPath(CobbleDexMod.MOD_ID, "emi_evolution"),
            EmiStack.of(Items.EXPERIENCE_BOTTLE)
        )
        val OBTAINMENT_CATEGORY = EmiRecipeCategory(
            ResourceLocation.fromNamespaceAndPath(CobbleDexMod.MOD_ID, "emi_obtainment"),
            EmiStack.of(Items.NETHER_STAR)
        )
        val DROP_CATEGORY = EmiRecipeCategory(
            ResourceLocation.fromNamespaceAndPath(CobbleDexMod.MOD_ID, "emi_drops"),
            EmiStack.of(Items.DIAMOND)
        )
        val STATS_CATEGORY = EmiRecipeCategory(
            ResourceLocation.fromNamespaceAndPath(CobbleDexMod.MOD_ID, "emi_stats"),
            EmiStack.of(Items.BOOK)
        )
        val POKEDEX_INFO_CATEGORY = EmiRecipeCategory(
            ResourceLocation.fromNamespaceAndPath(CobbleDexMod.MOD_ID, "emi_pokedex_info"),
            EmiStack.of(Items.WRITABLE_BOOK)
        )
        val MOVES_CATEGORY = EmiRecipeCategory(
            ResourceLocation.fromNamespaceAndPath(CobbleDexMod.MOD_ID, "emi_moves"),
            EmiStack.of(Items.PAPER)
        )
        val FOSSIL_CATEGORY = EmiRecipeCategory(
            ResourceLocation.fromNamespaceAndPath(CobbleDexMod.MOD_ID, "emi_fossils"),
            EmiStack.of(Items.BONE)
        )
        val TYPE_CHART_CATEGORY = EmiRecipeCategory(
            ResourceLocation.fromNamespaceAndPath(CobbleDexMod.MOD_ID, "emi_type_chart"),
            EmiStack.of(Items.SHIELD)
        )
        val NATURE_CATEGORY = EmiRecipeCategory(
            ResourceLocation.fromNamespaceAndPath(CobbleDexMod.MOD_ID, "emi_natures"),
            EmiStack.of(Items.WRITABLE_BOOK)
        )
    }

    override fun register(registry: EmiRegistry) {
        SpawnDataIndex.ensureLoaded()
        val config = CobbleDexConfig.get()

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

        if (config.showMoves) {
            registry.addCategory(MOVES_CATEGORY)
            registry.addWorkstation(MOVES_CATEGORY, EmiStack.of(Items.PAPER))
        }

        if (config.showFossils) {
            registry.addCategory(FOSSIL_CATEGORY)
            registry.addWorkstation(FOSSIL_CATEGORY, EmiStack.of(Items.BONE))
        }

        if (config.showTypeChart) {
            registry.addCategory(TYPE_CHART_CATEGORY)
            registry.addWorkstation(TYPE_CHART_CATEGORY, EmiStack.of(Items.SHIELD))
        }

        if (config.showNatures) {
            registry.addCategory(NATURE_CATEGORY)
            registry.addWorkstation(NATURE_CATEGORY, EmiStack.of(Items.WRITABLE_BOOK))
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

        if (config.showMoves) {
            val movesRecipes = RecipeBuilder.buildAllMovesRecipes().map { EmiMovesRecipe(it) }
            for (recipe in movesRecipes) registry.addRecipe(recipe)
            DebugLog.info("EMI: Registered ${movesRecipes.size} moves recipes")
        }

        if (config.showFossils) {
            val fossilRecipes = RecipeBuilder.buildAllFossilRecipes().map { EmiFossilRecipe(it) }
            for (recipe in fossilRecipes) registry.addRecipe(recipe)
            DebugLog.info("EMI: Registered ${fossilRecipes.size} fossil recipes")
        }

        if (config.showTypeChart) {
            val typeChartRecipes = RecipeBuilder.buildAllTypeChartRecipes().map { EmiTypeChartRecipe(it) }
            for (recipe in typeChartRecipes) registry.addRecipe(recipe)
            DebugLog.info("EMI: Registered ${typeChartRecipes.size} type chart recipes")
        }

        if (config.showNatures) {
            val natureRecipes = RecipeBuilder.buildNatureRecipes().map { EmiNatureRecipe(it) }
            for (recipe in natureRecipes) registry.addRecipe(recipe)
            DebugLog.info("EMI: Registered ${natureRecipes.size} nature recipes")
        }
    }

    private fun pokemonStack(speciesName: String): EmiStack? {
        val item = PokemonItemCache.getItem(speciesName) ?: return null
        return if (!item.isEmpty) EmiStack.of(item) else null
    }
}
