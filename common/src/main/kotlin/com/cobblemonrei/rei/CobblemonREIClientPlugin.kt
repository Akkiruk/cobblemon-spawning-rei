package com.cobblemonrei.rei

import com.cobblemonrei.CobblemonSpawningMod
import com.cobblemonrei.SpawnDataIndex
import com.cobblemonrei.rei.evolution.EvolutionCategory
import com.cobblemonrei.rei.evolution.EvolutionDisplay
import com.cobblemonrei.rei.spawn.SpawnCategory
import com.cobblemonrei.rei.spawn.SpawnDisplay
import me.shedaniel.rei.api.client.plugins.REIClientPlugin
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry
import me.shedaniel.rei.api.client.registry.entry.EntryRegistry
import me.shedaniel.rei.api.common.util.EntryStacks
import net.minecraft.world.item.Items

open class CobblemonREIClientPlugin : REIClientPlugin {

    override fun registerCategories(registry: CategoryRegistry) {
        CobblemonSpawningMod.LOGGER.info("[CobblemonSpawningREI] Registering REI categories")
        registry.add(SpawnCategory())
        registry.add(EvolutionCategory())

        registry.addWorkstations(SpawnCategory.ID, EntryStacks.of(Items.GRASS_BLOCK))
        registry.addWorkstations(EvolutionCategory.ID, EntryStacks.of(Items.EXPERIENCE_BOTTLE))
    }

    override fun registerDisplays(registry: DisplayRegistry) {
        if (!CobblemonSpawningMod.dataLoaded) {
            CobblemonSpawningMod.LOGGER.warn("[CobblemonSpawningREI] Data not loaded yet, deferring display registration")
            CobblemonSpawningMod.onClientReady()
        }

        var spawnDisplays = 0
        var evoDisplays = 0

        // Register spawn displays
        for ((species, spawns) in SpawnDataIndex.spawnsBySpecies) {
            if (spawns.isEmpty()) continue
            try {
                registry.add(SpawnDisplay(species, spawns))
                spawnDisplays++
            } catch (e: Exception) {
                CobblemonSpawningMod.LOGGER.debug("Failed to create spawn display for $species: ${e.message}")
            }
        }

        // Register evolution displays (forward: R-click to see what this evolves into)
        for ((species, evolutions) in SpawnDataIndex.evolutionsBySpecies) {
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
        // Pokémon are already in the sidebar via Cobblemon's spawn eggs or species items.
        // We don't need to add custom entries for v1.0 — REI will link our displays
        // to Cobblemon items via the species name matching.
    }
}
