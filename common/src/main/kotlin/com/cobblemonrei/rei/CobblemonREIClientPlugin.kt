package com.cobblemonrei.rei

import com.cobblemonrei.CobblemonSpawningMod
import com.cobblemonrei.DebugLog
import com.cobblemonrei.EvolutionInfo
import com.cobblemonrei.PokemonItemCache
import com.cobblemonrei.SpawnDataIndex
import com.cobblemonrei.SpawnDisplayHelper
import com.cobblemonrei.SpawnInfo
import com.cobblemonrei.config.CobblemonSpawningConfig
import com.cobblemonrei.platform.PlatformHelper
import com.cobblemonrei.rei.entry.PokemonEntry
import com.cobblemonrei.rei.entry.PokemonEntryDefinition
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
            } catch (e: Exception) {
                DebugLog.warnOnce("rei-entry-type") { "Failed to register PokemonEntryType: ${e.message}" }
            }
        }
    }

    override fun registerCategories(registry: CategoryRegistry) {
        ensureEntryTypeAvailable()
        registry.add(SpawnCategory())
        if (CobblemonSpawningConfig.get().showEvolutions) {
            registry.add(EvolutionCategory())
        }
        DebugLog.info("REI categories registered (spawns${if (CobblemonSpawningConfig.get().showEvolutions) " + evolution" else ""})")
    }

    override fun registerDisplays(registry: DisplayRegistry) {
        ensureEntryTypeAvailable()
        SpawnDataIndex.ensureLoaded()

        registry.registerDisplayGenerator(SpawnCategory.ID, SpawnDisplayGenerator())
        if (CobblemonSpawningConfig.get().showEvolutions) {
            registry.registerDisplayGenerator(EvolutionCategory.ID, EvolutionDisplayGenerator())
        }

        DebugLog.info("Registered dynamic display generators")
    }

    override fun registerEntries(registry: EntryRegistry) {
        ensureEntryTypeAvailable()
        SpawnDataIndex.ensureLoaded()

        var registered = 0
        var hidden = 0

        for (species in SpawnDataIndex.allSpeciesNames) {
            if (!PokemonItemCache.canRender(species)) {
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

        @Volatile private var cachedVersion = -1L
        @Volatile private var cachedDisplays: List<SpawnDisplay>? = null

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
            val version = SpawnDataIndex.dataVersion
            cachedDisplays?.let { if (cachedVersion == version) return Optional.of(it) }

            val all = mutableListOf<SpawnDisplay>()
            for ((species, spawns) in SpawnDataIndex.spawnsBySpecies) {
                if (spawns.isEmpty()) continue
                all.addAll(buildSpawnDisplays(species, spawns))
            }
            cachedDisplays = all
            cachedVersion = version
            return if (all.isEmpty()) Optional.empty() else Optional.of(all)
        }
    }

    private inner class EvolutionDisplayGenerator : DynamicDisplayGenerator<EvolutionDisplay> {

        @Volatile private var cachedVersion = -1L
        @Volatile private var cachedDisplays: List<EvolutionDisplay>? = null

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
            val version = SpawnDataIndex.dataVersion
            cachedDisplays?.let { if (cachedVersion == version) return Optional.of(it) }

            val displays = SpawnDisplayHelper.deduplicateEvolutions(SpawnDataIndex.evolutionsBySpecies)
                .map { (evo, idx, total) -> EvolutionDisplay(evo, idx, total) }
            cachedDisplays = displays
            cachedVersion = version
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
                SpawnDisplay(species, ms.spawn, ms.formVariants, idx, bucketCounts[b]!!)
            } catch (e: Exception) {
                DebugLog.once("spawn-display-$species-${ms.spawn.id}") { "Failed: ${e.message}" }
                null
            }
        }
    }
}
