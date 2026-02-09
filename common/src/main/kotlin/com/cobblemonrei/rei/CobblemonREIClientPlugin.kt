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
import me.shedaniel.rei.api.client.registry.entry.EntryRegistry
import me.shedaniel.rei.api.common.entry.EntryStack
import me.shedaniel.rei.api.common.entry.type.EntryTypeRegistry

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

        var spawnDisplays = 0
        var evoDisplays = 0
        val registeredEvoIds = mutableSetOf<String>()

        // Spawn displays — one per rule, merge variant-only pools, deterministic order
        for ((species, spawns) in SpawnDataIndex.spawnsBySpecies) {
            if (spawns.isEmpty()) continue
            val merged = mergeVariantSpawns(spawns)
            val sorted = merged.sortedWith(
                compareBy<MergedSpawn> { SpawnCategory.bucketSortOrder(it.spawn.bucket) }
                    .thenBy { it.spawn.context }
                    .thenByDescending { it.spawn.weight }
            )
            val bucketCounts = sorted.groupBy { it.spawn.bucket.lowercase() }.mapValues { it.value.size }
            val bucketIdx = mutableMapOf<String, Int>()
            for (ms in sorted) {
                val b = ms.spawn.bucket.lowercase()
                val idx = (bucketIdx[b] ?: 0) + 1
                bucketIdx[b] = idx
                try {
                    registry.add(SpawnDisplay(species, ms.spawn, ms.formVariants, idx, bucketCounts[b]!!))
                    spawnDisplays++
                } catch (e: Exception) {
                    DebugLog.once("spawn-display-$species-${ms.spawn.id}") { "Failed: ${e.message}" }
                }
            }
        }

        // Evolution displays — deduplicate, compute branch counts
        val uniqueEvos = mutableListOf<EvolutionInfo>()
        for ((_, evolutions) in SpawnDataIndex.evolutionsBySpecies) {
            for (evo in evolutions) {
                if (evo.id in registeredEvoIds) continue
                registeredEvoIds.add(evo.id)
                uniqueEvos.add(evo)
            }
        }
        val branchCounts = uniqueEvos.groupBy { it.fromSpecies }
        for (evo in uniqueEvos) {
            val siblings = branchCounts[evo.fromSpecies] ?: listOf(evo)
            val idx = siblings.indexOf(evo) + 1
            try {
                registry.add(EvolutionDisplay(evo, idx, siblings.size))
                evoDisplays++
            } catch (e: Exception) {
                DebugLog.once("evo-display-${evo.id}") { "Failed evolution display for ${evo.id}: ${e.message}" }
            }
        }

        DebugLog.info("Registered $spawnDisplays spawn, $evoDisplays evolution displays")
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
                        .replaceFirstChar { c -> c.uppercase() }
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
