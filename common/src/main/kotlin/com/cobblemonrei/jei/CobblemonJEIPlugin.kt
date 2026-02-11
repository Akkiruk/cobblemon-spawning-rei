package com.cobblemonrei.jei

import com.cobblemonrei.CobblemonSpawningMod
import com.cobblemonrei.DebugLog
import com.cobblemonrei.PokemonItemCache
import com.cobblemonrei.RecipeBuilder
import com.cobblemonrei.SpawnDataIndex
import com.cobblemonrei.config.CobblemonSpawningConfig
import com.cobblemonrei.jei.evolution.JeiEvolutionCategory
import com.cobblemonrei.jei.evolution.JeiEvolutionRecipe
import com.cobblemonrei.jei.obtainment.JeiObtainmentCategory
import com.cobblemonrei.jei.obtainment.JeiObtainmentRecipe
import com.cobblemonrei.jei.spawn.JeiSpawnCategory
import com.cobblemonrei.jei.spawn.JeiSpawnRecipe
import mezz.jei.api.IModPlugin
import mezz.jei.api.registration.*
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

open class CobblemonJEIPlugin : IModPlugin {

    override fun getPluginUid(): ResourceLocation =
        ResourceLocation.fromNamespaceAndPath(CobblemonSpawningMod.MOD_ID, "jei_plugin")

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
        val config = CobblemonSpawningConfig.get()
        registration.addRecipeCategories(JeiSpawnCategory(guiHelper))
        if (config.showEvolutions) registration.addRecipeCategories(JeiEvolutionCategory(guiHelper))
        if (config.showObtainment) registration.addRecipeCategories(JeiObtainmentCategory(guiHelper))
        DebugLog.info("JEI: Categories registered")
    }

    override fun registerRecipes(registration: IRecipeRegistration) {
        SpawnDataIndex.ensureLoaded()
        val config = CobblemonSpawningConfig.get()

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
    }

    override fun registerRecipeCatalysts(registration: IRecipeCatalystRegistration) {
        val config = CobblemonSpawningConfig.get()
        registration.addRecipeCatalyst(ItemStack(Items.GRASS_BLOCK), JeiSpawnCategory.RECIPE_TYPE)
        if (config.showEvolutions) registration.addRecipeCatalyst(ItemStack(Items.EXPERIENCE_BOTTLE), JeiEvolutionCategory.RECIPE_TYPE)
        if (config.showObtainment) registration.addRecipeCatalyst(ItemStack(Items.NETHER_STAR), JeiObtainmentCategory.RECIPE_TYPE)
    }
}

