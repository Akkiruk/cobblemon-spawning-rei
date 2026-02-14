package com.cobbledex.rei

import com.cobbledex.CobbleDexMod
import com.cobbledex.DebugLog
import com.cobbledex.PokemonItemCache
import com.cobbledex.RecipeBuilder
import com.cobbledex.SpawnDataIndex
import com.cobbledex.SpawnDisplayHelper
import com.cobbledex.config.CobbleDexConfig
import com.cobbledex.rei.drops.DropCategory
import com.cobbledex.rei.drops.DropDisplay
import com.cobbledex.rei.entry.PokemonEntry
import com.cobbledex.rei.entry.PokemonEntryDefinition
import com.cobbledex.rei.entry.PokemonEntryType
import com.cobbledex.rei.evolution.EvolutionCategory
import com.cobbledex.rei.evolution.EvolutionDisplay
import com.cobbledex.rei.obtainment.ObtainmentCategory
import com.cobbledex.rei.obtainment.ObtainmentDisplay
import com.cobbledex.rei.pokedex.PokedexInfoCategory
import com.cobbledex.rei.pokedex.PokedexInfoDisplay
import com.cobbledex.rei.spawn.SpawnCategory
import com.cobbledex.rei.spawn.SpawnDisplay
import com.cobbledex.rei.stats.StatsCategory
import com.cobbledex.rei.stats.StatsDisplay
import me.shedaniel.rei.api.client.plugins.REIClientPlugin
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry
import me.shedaniel.rei.api.client.registry.display.DynamicDisplayGenerator
import me.shedaniel.rei.api.client.registry.entry.EntryRegistry
import me.shedaniel.rei.api.common.entry.EntryStack
import me.shedaniel.rei.api.common.entry.type.EntryTypeRegistry
import me.shedaniel.rei.api.client.view.ViewSearchBuilder
import java.util.Optional

open class CobbleDexREIPlugin : REIClientPlugin {

    private val emiActive: Boolean by lazy {
        try {
            Class.forName("dev.emi.emi.api.EmiPlugin")
            DebugLog.info("EMI detected — skipping REI plugin (native EMI plugin handles registration)")
            true
        } catch (_: ClassNotFoundException) {
            false
        }
    }

    override fun registerEntryTypes(registry: EntryTypeRegistry) {
        if (emiActive) return
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
        if (emiActive) return
        ensureEntryTypeAvailable()
        val config = CobbleDexConfig.get()
        registry.add(SpawnCategory())
        if (config.showEvolutions) registry.add(EvolutionCategory())
        if (config.showObtainment) registry.add(ObtainmentCategory())
        if (config.showDrops) registry.add(DropCategory())
        if (config.showStats) registry.add(StatsCategory())
        if (config.showPokedexInfo) registry.add(PokedexInfoCategory())
        DebugLog.info("REI categories registered (spawns${if (config.showEvolutions) " + evolution" else ""}${if (config.showObtainment) " + obtainment" else ""}${if (config.showDrops) " + drops" else ""}${if (config.showStats) " + stats" else ""}${if (config.showPokedexInfo) " + pokedex" else ""})")
    }

    override fun registerDisplays(registry: DisplayRegistry) {
        if (emiActive) return
        ensureEntryTypeAvailable()
        SpawnDataIndex.ensureLoaded()
        val config = CobbleDexConfig.get()

        registry.registerDisplayGenerator(SpawnCategory.ID, SpawnDisplayGenerator())
        if (config.showEvolutions) registry.registerDisplayGenerator(EvolutionCategory.ID, EvolutionDisplayGenerator())
        if (config.showObtainment) registry.registerDisplayGenerator(ObtainmentCategory.ID, ObtainmentDisplayGenerator())
        if (config.showDrops) registry.registerDisplayGenerator(DropCategory.ID, DropDisplayGenerator())
        if (config.showStats) registry.registerDisplayGenerator(StatsCategory.ID, StatsDisplayGenerator())
        if (config.showPokedexInfo) registry.registerDisplayGenerator(PokedexInfoCategory.ID, PokedexInfoDisplayGenerator())

        DebugLog.info("Registered dynamic display generators")
    }

    override fun registerEntries(registry: EntryRegistry) {
        if (emiActive) return
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
            return Optional.of(RecipeBuilder.buildSpawnRecipes(value.species, spawns).map { SpawnDisplay(it) })
        }

        override fun getUsageFor(entry: EntryStack<*>): Optional<List<SpawnDisplay>> = Optional.empty()

        override fun generate(builder: ViewSearchBuilder): Optional<List<SpawnDisplay>> {
            if (builder.recipesFor.isNotEmpty() || builder.usagesFor.isNotEmpty()) return Optional.empty()
            if (!SpawnDataIndex.hasData()) return Optional.empty()
            val version = SpawnDataIndex.dataVersion
            cachedDisplays?.let { if (cachedVersion == version) return Optional.of(it) }

            val all = mutableListOf<SpawnDisplay>()
            for ((species, spawns) in SpawnDataIndex.spawnsBySpecies) {
                if (spawns.isEmpty()) continue
                all.addAll(RecipeBuilder.buildSpawnRecipes(species, spawns).map { SpawnDisplay(it) })
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
            val evos = SpawnDataIndex.getEvolutionsTo(value.species)
            if (evos.isEmpty()) return Optional.empty()
            return Optional.of(RecipeBuilder.buildEvolutionsFor(evos).map { EvolutionDisplay(it) })
        }

        override fun getUsageFor(entry: EntryStack<*>): Optional<List<EvolutionDisplay>> {
            val value = entry.value ?: return Optional.empty()
            if (value !is PokemonEntry) return Optional.empty()
            val evos = SpawnDataIndex.getEvolutionsFrom(value.species)
            if (evos.isEmpty()) return Optional.empty()
            return Optional.of(RecipeBuilder.buildEvolutionsFor(evos).map { EvolutionDisplay(it) })
        }

        override fun generate(builder: ViewSearchBuilder): Optional<List<EvolutionDisplay>> {
            if (builder.recipesFor.isNotEmpty() || builder.usagesFor.isNotEmpty()) return Optional.empty()
            if (!SpawnDataIndex.hasData()) return Optional.empty()
            val version = SpawnDataIndex.dataVersion
            cachedDisplays?.let { if (cachedVersion == version) return Optional.of(it) }

            val displays = RecipeBuilder.buildAllEvolutionRecipes().map { EvolutionDisplay(it) }
            cachedDisplays = displays
            cachedVersion = version
            return if (displays.isEmpty()) Optional.empty() else Optional.of(displays)
        }
    }

    private inner class ObtainmentDisplayGenerator : DynamicDisplayGenerator<ObtainmentDisplay> {

        @Volatile private var cachedVersion = -1L
        @Volatile private var cachedDisplays: List<ObtainmentDisplay>? = null

        override fun getRecipeFor(entry: EntryStack<*>): Optional<List<ObtainmentDisplay>> {
            val value = entry.value ?: return Optional.empty()
            if (value !is PokemonEntry) return Optional.empty()
            val obtainments = SpawnDataIndex.getObtainmentFor(value.species)
            if (obtainments.isEmpty()) return Optional.empty()
            return Optional.of(RecipeBuilder.buildObtainmentsFor(value.species, obtainments).map { ObtainmentDisplay(it) })
        }

        override fun getUsageFor(entry: EntryStack<*>): Optional<List<ObtainmentDisplay>> = Optional.empty()

        override fun generate(builder: ViewSearchBuilder): Optional<List<ObtainmentDisplay>> {
            if (builder.recipesFor.isNotEmpty() || builder.usagesFor.isNotEmpty()) return Optional.empty()
            if (!SpawnDataIndex.hasData()) return Optional.empty()
            val version = SpawnDataIndex.dataVersion
            cachedDisplays?.let { if (cachedVersion == version) return Optional.of(it) }

            val all = RecipeBuilder.buildAllObtainmentRecipes().map { ObtainmentDisplay(it) }
            cachedDisplays = all
            cachedVersion = version
            return if (all.isEmpty()) Optional.empty() else Optional.of(all)
        }
    }

    private inner class DropDisplayGenerator : DynamicDisplayGenerator<DropDisplay> {

        @Volatile private var cachedVersion = -1L
        @Volatile private var cachedDisplays: List<DropDisplay>? = null

        override fun getRecipeFor(entry: EntryStack<*>): Optional<List<DropDisplay>> {
            val value = entry.value ?: return Optional.empty()
            if (value !is PokemonEntry) return Optional.empty()
            val recipes = RecipeBuilder.buildDropsFor(value.species)
            if (recipes.isEmpty()) return Optional.empty()
            return Optional.of(recipes.map { DropDisplay(it) })
        }

        override fun getUsageFor(entry: EntryStack<*>): Optional<List<DropDisplay>> = Optional.empty()

        override fun generate(builder: ViewSearchBuilder): Optional<List<DropDisplay>> {
            if (builder.recipesFor.isNotEmpty() || builder.usagesFor.isNotEmpty()) return Optional.empty()
            if (!SpawnDataIndex.hasData()) return Optional.empty()
            val version = SpawnDataIndex.dataVersion
            cachedDisplays?.let { if (cachedVersion == version) return Optional.of(it) }

            val all = RecipeBuilder.buildAllDropRecipes().map { DropDisplay(it) }
            cachedDisplays = all
            cachedVersion = version
            return if (all.isEmpty()) Optional.empty() else Optional.of(all)
        }
    }

    private inner class StatsDisplayGenerator : DynamicDisplayGenerator<StatsDisplay> {

        @Volatile private var cachedVersion = -1L
        @Volatile private var cachedDisplays: List<StatsDisplay>? = null

        override fun getRecipeFor(entry: EntryStack<*>): Optional<List<StatsDisplay>> {
            val value = entry.value ?: return Optional.empty()
            if (value !is PokemonEntry) return Optional.empty()
            val recipe = RecipeBuilder.buildStatsFor(value.species) ?: return Optional.empty()
            return Optional.of(listOf(StatsDisplay(recipe)))
        }

        override fun getUsageFor(entry: EntryStack<*>): Optional<List<StatsDisplay>> = Optional.empty()

        override fun generate(builder: ViewSearchBuilder): Optional<List<StatsDisplay>> {
            if (builder.recipesFor.isNotEmpty() || builder.usagesFor.isNotEmpty()) return Optional.empty()
            if (!SpawnDataIndex.hasData()) return Optional.empty()
            val version = SpawnDataIndex.dataVersion
            cachedDisplays?.let { if (cachedVersion == version) return Optional.of(it) }

            val all = RecipeBuilder.buildAllStatsRecipes().map { StatsDisplay(it) }
            cachedDisplays = all
            cachedVersion = version
            return if (all.isEmpty()) Optional.empty() else Optional.of(all)
        }
    }

    private inner class PokedexInfoDisplayGenerator : DynamicDisplayGenerator<PokedexInfoDisplay> {

        @Volatile private var cachedVersion = -1L
        @Volatile private var cachedDisplays: List<PokedexInfoDisplay>? = null

        override fun getRecipeFor(entry: EntryStack<*>): Optional<List<PokedexInfoDisplay>> {
            val value = entry.value ?: return Optional.empty()
            if (value !is PokemonEntry) return Optional.empty()
            val recipe = RecipeBuilder.buildPokedexInfoFor(value.species) ?: return Optional.empty()
            return Optional.of(listOf(PokedexInfoDisplay(recipe)))
        }

        override fun getUsageFor(entry: EntryStack<*>): Optional<List<PokedexInfoDisplay>> = Optional.empty()

        override fun generate(builder: ViewSearchBuilder): Optional<List<PokedexInfoDisplay>> {
            if (builder.recipesFor.isNotEmpty() || builder.usagesFor.isNotEmpty()) return Optional.empty()
            if (!SpawnDataIndex.hasData()) return Optional.empty()
            val version = SpawnDataIndex.dataVersion
            cachedDisplays?.let { if (cachedVersion == version) return Optional.of(it) }

            val all = RecipeBuilder.buildAllPokedexInfoRecipes().map { PokedexInfoDisplay(it) }
            cachedDisplays = all
            cachedVersion = version
            return if (all.isEmpty()) Optional.empty() else Optional.of(all)
        }
    }
}
