package com.cobblemonrei.rei

import com.cobblemonrei.CobblemonSpawningMod
import com.cobblemonrei.SpawnDataIndex
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
import me.shedaniel.rei.api.client.registry.entry.EntryRegistry
import me.shedaniel.rei.api.common.entry.EntryStack
import me.shedaniel.rei.api.common.entry.type.EntryTypeRegistry

open class CobblemonREIClientPlugin : REIClientPlugin {

    override fun registerEntryTypes(registry: EntryTypeRegistry) {
        CobblemonSpawningMod.LOGGER.info("[CobblemonSpawningREI] registerEntryTypes called")
        try {
            registry.register(PokemonEntryType.POKEMON.id, PokemonEntryDefinition())
            CobblemonSpawningMod.LOGGER.info("[CobblemonSpawningREI] Pokémon entry type registered")
        } catch (e: Exception) {
            CobblemonSpawningMod.LOGGER.warn("[CobblemonSpawningREI] registerEntryTypes failed: ${e.message}")
        }
    }

    private fun ensureEntryTypeAvailable() {
        try {
            PokemonEntryType.POKEMON.definition
        } catch (_: Exception) {
            try {
                EntryTypeRegistry.getInstance().register(PokemonEntryType.POKEMON.id, PokemonEntryDefinition())
                CobblemonSpawningMod.LOGGER.info("[CobblemonSpawningREI] Entry type registered (late)")
            } catch (_: Exception) { }
        }
    }

    override fun registerCategories(registry: CategoryRegistry) {
        ensureEntryTypeAvailable()
        CobblemonSpawningMod.LOGGER.info("[CobblemonSpawningREI] Registering REI categories")
        registry.add(SpawnCategory())
        registry.add(EvolutionCategory())
    }

    override fun registerDisplays(registry: DisplayRegistry) {
        ensureEntryTypeAvailable()
        if (!CobblemonSpawningMod.dataLoaded) {
            CobblemonSpawningMod.LOGGER.warn("[CobblemonSpawningREI] Data not loaded yet, loading now")
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
        ensureEntryTypeAvailable()

        if (!CobblemonSpawningMod.dataLoaded) {
            CobblemonSpawningMod.onClientReady()
        }

        CobblemonSpawningMod.LOGGER.info("[CobblemonSpawningREI] registerEntries called, ${SpawnDataIndex.allSpeciesNames.size} species available")
        
        val hasPikachu = SpawnDataIndex.allSpeciesNames.any { it.lowercase() == "pikachu" }
        CobblemonSpawningMod.LOGGER.info("[CobblemonSpawningREI] Species list contains pikachu: $hasPikachu")
        
        var count = 0
        var errors = 0
        for (species in SpawnDataIndex.allSpeciesNames) {
            try {
                val entry = PokemonEntry(species)
                val stack = EntryStack.of(PokemonEntryType.POKEMON, entry)
                registry.addEntry(stack)
                count++
                if (species.lowercase() == "pikachu") {
                    CobblemonSpawningMod.LOGGER.info("[CobblemonSpawningREI] Registered pikachu entry: species='${entry.species}', displayName='${entry.displayName}'")
                }
            } catch (e: Exception) {
                errors++
                if (errors <= 3) {
                    CobblemonSpawningMod.LOGGER.warn("[CobblemonSpawningREI] Failed to register entry for $species: ${e.javaClass.simpleName}: ${e.message}")
                }
            }
        }
        if (errors > 3) {
            CobblemonSpawningMod.LOGGER.warn("[CobblemonSpawningREI] ... and ${errors - 3} more entry registration failures")
        }
        CobblemonSpawningMod.LOGGER.info("[CobblemonSpawningREI] Registered $count Pokémon entries in REI sidebar ($errors failures)")
    }
}
