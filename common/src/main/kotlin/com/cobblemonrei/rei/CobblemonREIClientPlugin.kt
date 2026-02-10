package com.cobblemonrei.rei

import com.cobblemonrei.CobblemonSpawningMod
import com.cobblemonrei.DebugLog
import com.cobblemonrei.EvolutionInfo
import com.cobblemonrei.SpawnDataIndex
import com.cobblemonrei.SpawnInfo
import com.cobblemonrei.rei.entry.PokemonEntry
import com.cobblemonrei.rei.entry.PokemonEntryDefinition
import com.cobblemonrei.rei.entry.PokemonEntryRenderer
import com.cobblemonrei.rei.entry.PokemonEntryType
import com.cobblemonrei.rei.evolution.EvolutionCategory
import com.cobblemonrei.rei.evolution.EvolutionDisplay
import com.cobblemonrei.rei.spawn.SpawnCategory
import com.cobblemonrei.rei.spawn.SpawnDisplay
import me.shedaniel.rei.api.client.plugins.REIClientPlugin
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry
import me.shedaniel.rei.api.client.registry.display.DynamicDisplayGenerator
import me.shedaniel.rei.api.client.registry.entry.EntryRegistry
import me.shedaniel.rei.api.common.entry.EntryStack
import me.shedaniel.rei.api.common.entry.type.EntryTypeRegistry
import me.shedaniel.rei.api.client.view.ViewSearchBuilder
import java.util.Optional

open class CobblemonREIClientPlugin : REIClientPlugin {

    override fun registerEntryTypes(registry: EntryTypeRegistry) {
        try {
            registry.register(PokemonEntryType.POKEMON.id, PokemonEntryDefinition())
            DebugLog.info("Pokémon entry type registered")
        } catch (e: Exception) {
            DebugLog.warn("registerEntryTypes failed: ${e.message}")
        }
    }

    private fun ensureEntryTypeAvailable() {
        try {
            PokemonEntryType.POKEMON.definition
        } catch (_: Exception) {
            try {
                EntryTypeRegistry.getInstance().register(PokemonEntryType.POKEMON.id, PokemonEntryDefinition())
            } catch (_: Exception) { }
        }
    }

    override fun registerCategories(registry: CategoryRegistry) {
        ensureEntryTypeAvailable()
        registry.add(SpawnCategory())
        registry.add(EvolutionCategory())
        DebugLog.info("REI categories registered (spawns + evolution)")
    }

    override fun registerDisplays(registry: DisplayRegistry) {
        ensureEntryTypeAvailable()
        SpawnDataIndex.ensureLoaded()

        // Use dynamic generators so displays pull live data from SpawnDataIndex.
        // When server data arrives (replacing local data), subsequent searches
        // automatically reflect the updated data without needing a REI reload.
        registry.registerDisplayGenerator(SpawnCategory.ID, SpawnDisplayGenerator())
        registry.registerDisplayGenerator(EvolutionCategory.ID, EvolutionDisplayGenerator())

        DebugLog.info("Registered dynamic display generators for spawns + evolution")
    }

    override fun registerEntries(registry: EntryRegistry) {
        ensureEntryTypeAvailable()
        SpawnDataIndex.ensureLoaded()

        val renderer = PokemonEntryRenderer()
        var registered = 0
        var hidden = 0

        for (species in SpawnDataIndex.allSpeciesNames) {
            if (!renderer.canRender(species)) {
                DebugLog.trackMissingModel(species)
                hidden++
                continue
            }
            try {
                val entry = PokemonEntry(species)
                val stack = EntryStack.of(PokemonEntryType.POKEMON, entry)
                registry.addEntry(stack)
                registered++
            } catch (e: Exception) {
                DebugLog.once("entry-fail-$species") { "Entry registration failed for $species: ${e.message}" }
                hidden++
            }
        }

        DebugLog.info("Registered $registered Pokémon entries ($hidden hidden — no model)")
        DebugLog.printSummary()
    }

    // --- Dynamic Display Generators ---

    private inner class SpawnDisplayGenerator : DynamicDisplayGenerator<SpawnDisplay> {

        override fun getRecipeFor(entry: EntryStack<*>): Optional<List<SpawnDisplay>> {
            val value = entry.value ?: return Optional.empty()
            if (value !is PokemonEntry) return Optional.empty()
            val spawns = SpawnDataIndex.getSpawnsFor(value.species)
            if (spawns.isEmpty()) return Optional.empty()
            return Optional.of(buildSpawnDisplays(value.species, spawns))
        }

        override fun getUsageFor(entry: EntryStack<*>): Optional<List<SpawnDisplay>> = Optional.empty()

        override fun generate(builder: ViewSearchBuilder): Optional<List<SpawnDisplay>> {
            if (!SpawnDataIndex.isFullyLoaded()) return Optional.empty()
            val all = mutableListOf<SpawnDisplay>()
            for ((species, spawns) in SpawnDataIndex.spawnsBySpecies) {
                if (spawns.isEmpty()) continue
                all.addAll(buildSpawnDisplays(species, spawns))
            }
            return if (all.isEmpty()) Optional.empty() else Optional.of(all)
        }
    }

    private inner class EvolutionDisplayGenerator : DynamicDisplayGenerator<EvolutionDisplay> {

        override fun getRecipeFor(entry: EntryStack<*>): Optional<List<EvolutionDisplay>> {
            val value = entry.value ?: return Optional.empty()
            if (value !is PokemonEntry) return Optional.empty()
            return buildEvoDisplays(SpawnDataIndex.getEvolutionsTo(value.species))
        }

        override fun getUsageFor(entry: EntryStack<*>): Optional<List<EvolutionDisplay>> {
            val value = entry.value ?: return Optional.empty()
            if (value !is PokemonEntry) return Optional.empty()
            return buildEvoDisplays(SpawnDataIndex.getEvolutionsFrom(value.species))
        }

        override fun generate(builder: ViewSearchBuilder): Optional<List<EvolutionDisplay>> {
            if (!SpawnDataIndex.isFullyLoaded()) return Optional.empty()
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
            val displays = allEvos.map { evo ->
                val siblings = grouped[evo.fromSpecies] ?: listOf(evo)
                EvolutionDisplay(evo, siblings.indexOf(evo) + 1, siblings.size)
            }
            return if (displays.isEmpty()) Optional.empty() else Optional.of(displays)
        }

        private fun buildEvoDisplays(evos: List<com.cobblemonrei.EvolutionInfo>): Optional<List<EvolutionDisplay>> {
            if (evos.isEmpty()) return Optional.empty()
            val grouped = evos.groupBy { it.fromSpecies }
            val displays = evos.mapIndexed { _, evo ->
                val siblings = grouped[evo.fromSpecies] ?: listOf(evo)
                EvolutionDisplay(evo, siblings.indexOf(evo) + 1, siblings.size)
            }
            return Optional.of(displays)
        }
    }

    // --- Spawn display builder ---

    private fun buildSpawnDisplays(species: String, spawns: List<SpawnInfo>): List<SpawnDisplay> {
        val merged = mergeVariantSpawns(spawns)
        val sorted = merged.sortedWith(
            compareBy<MergedSpawn> { SpawnCategory.bucketSortOrder(it.spawn.bucket) }
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
                SpawnDisplay(species, ms.spawn, ms.formVariants, idx, bucketCounts[b]!!)
            } catch (e: Exception) {
                DebugLog.once("spawn-display-$species-${ms.spawn.id}") { "Failed: ${e.message}" }
                null
            }
        }
    }

    // --- Variant merge helpers ---

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
