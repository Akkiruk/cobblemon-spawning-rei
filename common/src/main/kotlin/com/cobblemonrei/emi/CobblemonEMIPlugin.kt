package com.cobblemonrei.emi

import com.cobblemonrei.CobblemonSpawningMod
import com.cobblemonrei.DebugLog
import com.cobblemonrei.EvolutionInfo
import com.cobblemonrei.SpawnDataIndex
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
        } catch (_: Exception) { null }
    }

    // --- Spawn recipe builders (same merge/sort logic as JEI) ---

    private fun buildSpawnRecipes(species: String, spawns: List<SpawnInfo>): List<EmiSpawnRecipe> {
        val merged = mergeVariantSpawns(spawns)
        val sorted = merged.sortedWith(
            compareBy<MergedSpawn> { EmiSpawnRecipe.bucketSortOrder(it.spawn.bucket) }
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
            EmiEvolutionRecipe(evo, siblings.indexOf(evo) + 1, siblings.size)
        }
    }

    // --- Variant merge ---

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
