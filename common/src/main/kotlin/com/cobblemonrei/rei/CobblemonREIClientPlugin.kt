package com.cobblemonrei.rei

import com.cobblemonrei.CobblemonSpawningMod
import com.cobblemonrei.SpawnDataIndex
import com.cobblemonrei.rei.entry.PokemonEntry
import com.cobblemonrei.rei.entry.PokemonEntryType
import com.cobblemonrei.rei.evolution.EvolutionCategory
import com.cobblemonrei.rei.evolution.EvolutionDisplay
import com.cobblemonrei.rei.spawn.SpawnCategory
import com.cobblemonrei.rei.spawn.SpawnDisplay
import me.shedaniel.rei.api.client.plugins.REIClientPlugin
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry
import me.shedaniel.rei.api.client.registry.entry.EntryRegistry
import me.shedaniel.rei.api.common.entry.EntryStack

open class CobblemonREIClientPlugin : REIClientPlugin {

    override fun registerCategories(registry: CategoryRegistry) {
        PokemonEntryTypeRegistration.ensureRegistered()
        CobblemonSpawningMod.LOGGER.info("[CobblemonSpawningREI] Registering REI categories")
        registry.add(SpawnCategory())
        registry.add(EvolutionCategory())
    }

    override fun registerDisplays(registry: DisplayRegistry) {
        if (!CobblemonSpawningMod.dataLoaded) {
            CobblemonSpawningMod.LOGGER.warn("[CobblemonSpawningREI] Data not loaded yet, deferring display registration")
            CobblemonSpawningMod.onClientReady()
        }

        var spawnDisplays = 0
        var evoDisplays = 0

        for ((species, spawns) in SpawnDataIndex.spawnsBySpecies) {
            if (spawns.isEmpty()) continue
            try {
                registry.add(SpawnDisplay(species, spawns))
                spawnDisplays++
            } catch (e: Exception) {
                CobblemonSpawningMod.LOGGER.debug("Failed to create spawn display for $species: ${e.message}")
            }
        }

        for ((_, evolutions) in SpawnDataIndex.evolutionsBySpecies) {
            for (evo in evolutions) {
                try {
                    registry.add(EvolutionDisplay(evo))
                    evoDisplays++
                } catch (e: Exception) {
                    CobblemonSpawningMod.LOGGER.debug("Failed to create evolution display for ${evo.id}: ${e.message}")
                }
            }
        }

        CobblemonSpawningMod.LOGGER.info("[CobblemonSpawningREI] Registered $spawnDisplays spawn displays, $evoDisplays evolution displays")
    }

    override fun registerEntries(registry: EntryRegistry) {
        var count = 0
        for (species in SpawnDataIndex.allSpeciesNames) {
            try {
                val stack = EntryStack.of(PokemonEntryType.POKEMON, PokemonEntry(species))
                registry.addEntry(stack)
                count++
            } catch (e: Exception) {
                CobblemonSpawningMod.LOGGER.debug("Failed to register entry for $species: ${e.message}")
            }
        }
        CobblemonSpawningMod.LOGGER.info("[CobblemonSpawningREI] Registered $count Pokémon entries in REI sidebar")
    }
}
