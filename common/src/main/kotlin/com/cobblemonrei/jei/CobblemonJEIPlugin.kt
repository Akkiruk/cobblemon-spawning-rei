package com.cobblemonrei.jei

import com.cobblemonrei.CobblemonSpawningMod
import com.cobblemonrei.DebugLog
import com.cobblemonrei.PokemonItemCache
import com.cobblemonrei.SpawnDataIndex
import com.cobblemonrei.SpawnDisplayHelper
import com.cobblemonrei.SpawnInfo
import com.cobblemonrei.config.CobblemonSpawningConfig
import com.cobblemonrei.jei.evolution.JeiEvolutionCategory
import com.cobblemonrei.jei.evolution.JeiEvolutionRecipe
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
        registration.addRecipeCatalyst(ItemStack(Items.GRASS_BLOCK), JeiSpawnCategory.RECIPE_TYPE)
        if (CobblemonSpawningConfig.get().showEvolutions) {
            registration.addRecipeCatalyst(ItemStack(Items.EXPERIENCE_BOTTLE), JeiEvolutionCategory.RECIPE_TYPE)
        }
    }

    // --- Recipe builders (matching REI logic) ---

    private fun buildAllEvolutionRecipes(): List<JeiEvolutionRecipe> {
        return SpawnDisplayHelper.deduplicateEvolutions(SpawnDataIndex.evolutionsBySpecies)
            .map { (evo, idx, total) -> JeiEvolutionRecipe(evo, idx, total) }
    }

    private fun buildSpawnRecipes(species: String, spawns: List<SpawnInfo>): List<JeiSpawnRecipe> {
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
                JeiSpawnRecipe(species, ms.spawn, ms.formVariants, idx, bucketCounts[b]!!)
            } catch (e: Exception) {
                DebugLog.once("jei-spawn-$species-${ms.spawn.id}") { "Failed: ${e.message}" }
                null
            }
        }
    }
}

