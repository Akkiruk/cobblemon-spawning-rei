package com.cobbledex.jei

import com.cobbledex.CobbleDexMod
import com.cobbledex.DebugLog
import com.cobbledex.PokemonItemCache
import com.cobbledex.RecipeBuilder
import com.cobbledex.SpawnDataIndex
import com.cobbledex.config.CobbleDexConfig
import com.cobbledex.jei.drops.JeiDropCategory
import com.cobbledex.jei.drops.JeiDropRecipe
import com.cobbledex.jei.evolution.JeiEvolutionCategory
import com.cobbledex.jei.evolution.JeiEvolutionRecipe
import com.cobbledex.jei.moves.JeiMovesCategory
import com.cobbledex.jei.moves.JeiMovesRecipe
import com.cobbledex.jei.obtainment.JeiObtainmentCategory
import com.cobbledex.jei.obtainment.JeiObtainmentRecipe
import com.cobbledex.jei.pokedex.JeiPokedexInfoCategory
import com.cobbledex.jei.pokedex.JeiPokedexInfoRecipe
import com.cobbledex.jei.spawn.JeiSpawnCategory
import com.cobbledex.jei.spawn.JeiSpawnRecipe
import com.cobbledex.jei.stats.JeiStatsCategory
import com.cobbledex.jei.stats.JeiStatsRecipe
import mezz.jei.api.IModPlugin
import mezz.jei.api.registration.*
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

open class CobbleDexJEIPlugin : IModPlugin {

    override fun getPluginUid(): ResourceLocation =
        ResourceLocation.fromNamespaceAndPath(CobbleDexMod.MOD_ID, "jei_plugin")

    @Suppress("DEPRECATION")
    override fun registerIngredients(registration: IModIngredientRegistration) {
        SpawnDataIndex.ensureLoaded()

        val allIngredients = SpawnDataIndex.allSpeciesNames
            .filter { PokemonItemCache.canRender(it) }
            .map { PokemonIngredient(it) }

        registration.register(PokemonIngredientType, allIngredients, PokemonIngredientHelper(), PokemonIngredientRenderer())
        DebugLog.info("JEI: Registered ${allIngredients.size} Pokémon ingredients")
    }

    override fun registerCategories(registration: IRecipeCategoryRegistration) {
        val guiHelper = registration.jeiHelpers.guiHelper
        val config = CobbleDexConfig.get()
        registration.addRecipeCategories(JeiSpawnCategory(guiHelper))
        if (config.showEvolutions) registration.addRecipeCategories(JeiEvolutionCategory(guiHelper))
        if (config.showObtainment) registration.addRecipeCategories(JeiObtainmentCategory(guiHelper))
        if (config.showDrops) registration.addRecipeCategories(JeiDropCategory(guiHelper))
        if (config.showStats) registration.addRecipeCategories(JeiStatsCategory(guiHelper))
        if (config.showMoves) registration.addRecipeCategories(JeiMovesCategory(guiHelper))
        if (config.showPokedexInfo) registration.addRecipeCategories(JeiPokedexInfoCategory(guiHelper))
        DebugLog.info("JEI: Categories registered")
    }

    override fun registerRecipes(registration: IRecipeRegistration) {
        SpawnDataIndex.ensureLoaded()
        val config = CobbleDexConfig.get()

        val spawnRecipes = mutableListOf<JeiSpawnRecipe>()
        for ((species, spawns) in SpawnDataIndex.spawnsBySpecies) {
            if (spawns.isEmpty()) continue
            spawnRecipes.addAll(RecipeBuilder.buildSpawnRecipes(species, spawns).map { JeiSpawnRecipe(it) })
        }
        registration.addRecipes(JeiSpawnCategory.RECIPE_TYPE, spawnRecipes)
        DebugLog.info("JEI: Registered ${spawnRecipes.size} spawn recipes")

        if (config.showEvolutions) {
            val evoRecipes = RecipeBuilder.buildAllEvolutionRecipes().map { JeiEvolutionRecipe(it) }
            registration.addRecipes(JeiEvolutionCategory.RECIPE_TYPE, evoRecipes)
            DebugLog.info("JEI: Registered ${evoRecipes.size} evolution recipes")
        }

        if (config.showObtainment) {
            val obtainRecipes = RecipeBuilder.buildAllObtainmentRecipes().map { JeiObtainmentRecipe(it) }
            registration.addRecipes(JeiObtainmentCategory.RECIPE_TYPE, obtainRecipes)
            DebugLog.info("JEI: Registered ${obtainRecipes.size} obtainment recipes")
        }

        if (config.showDrops) {
            val dropRecipes = RecipeBuilder.buildAllDropRecipes().map { JeiDropRecipe(it) }
            registration.addRecipes(JeiDropCategory.RECIPE_TYPE, dropRecipes)
            DebugLog.info("JEI: Registered ${dropRecipes.size} drop recipes")
        }

        if (config.showStats) {
            val statsRecipes = RecipeBuilder.buildAllStatsRecipes().map { JeiStatsRecipe(it) }
            registration.addRecipes(JeiStatsCategory.RECIPE_TYPE, statsRecipes)
            DebugLog.info("JEI: Registered ${statsRecipes.size} stats recipes")
        }

        if (config.showMoves) {
            val movesRecipes = RecipeBuilder.buildAllMovesRecipes().map { JeiMovesRecipe(it) }
            registration.addRecipes(JeiMovesCategory.RECIPE_TYPE, movesRecipes)
            DebugLog.info("JEI: Registered ${movesRecipes.size} moves recipes")
        }

        if (config.showPokedexInfo) {
            val pokedexRecipes = RecipeBuilder.buildAllPokedexInfoRecipes().map { JeiPokedexInfoRecipe(it) }
            registration.addRecipes(JeiPokedexInfoCategory.RECIPE_TYPE, pokedexRecipes)
            DebugLog.info("JEI: Registered ${pokedexRecipes.size} pokédex info recipes")
        }
    }

    override fun registerRecipeCatalysts(registration: IRecipeCatalystRegistration) {
        val config = CobbleDexConfig.get()
        registration.addRecipeCatalyst(ItemStack(Items.GRASS_BLOCK), JeiSpawnCategory.RECIPE_TYPE)
        if (config.showEvolutions) registration.addRecipeCatalyst(ItemStack(Items.EXPERIENCE_BOTTLE), JeiEvolutionCategory.RECIPE_TYPE)
        if (config.showObtainment) registration.addRecipeCatalyst(ItemStack(Items.NETHER_STAR), JeiObtainmentCategory.RECIPE_TYPE)
        if (config.showDrops) registration.addRecipeCatalyst(ItemStack(Items.DIAMOND), JeiDropCategory.RECIPE_TYPE)
        if (config.showStats) registration.addRecipeCatalyst(ItemStack(Items.BOOK), JeiStatsCategory.RECIPE_TYPE)
        if (config.showMoves) registration.addRecipeCatalyst(ItemStack(Items.PAPER), JeiMovesCategory.RECIPE_TYPE)
        if (config.showPokedexInfo) registration.addRecipeCatalyst(ItemStack(Items.WRITABLE_BOOK), JeiPokedexInfoCategory.RECIPE_TYPE)
    }
}

