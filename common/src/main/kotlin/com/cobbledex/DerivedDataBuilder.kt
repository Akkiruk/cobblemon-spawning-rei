package com.cobbledex

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies

object DerivedDataBuilder {
    data class Result(
        val snapshot: CobbleDexDataSnapshot,
        val backfilledSpeciesInfoCount: Int,
        val speciesEnumerationError: String?,
    )

    fun rebuild(
        snapshot: CobbleDexDataSnapshot,
        runtimeSpeciesNames: () -> Iterable<String> = ::loadRuntimeSpeciesNames,
    ): Result {
        val reverseMap = mutableMapOf<String, MutableList<EvolutionInfo>>()
        for ((_, evolutions) in snapshot.evolutionsBySpecies) {
            for (evo in evolutions) {
                val normalizedTo = SpeciesNameNormalizer.normalize(evo.toSpecies)
                reverseMap.getOrPut(normalizedTo) { mutableListOf() }.add(evo)
            }
        }

        val enriched = snapshot.speciesInfo.toMutableMap()
        var backfilled = 0
        for (key in snapshot.evolutionsBySpecies.keys) {
            if (key in enriched) continue
            val evos = snapshot.evolutionsBySpecies[key] ?: continue
            val baseName = SpeciesNameNormalizer.normalize(evos.firstOrNull()?.fromSpecies ?: continue)
            val baseInfo = enriched[baseName] ?: continue
            enriched[key] = baseInfo.copy(
                name = key,
                baseSpeciesName = baseName,
                formAspects = evos.firstOrNull()?.fromAspects ?: emptySet()
            )
            backfilled++
        }

        val allNames = mutableSetOf<String>()
        allNames.addAll(snapshot.spawnsBySpecies.keys)
        allNames.addAll(snapshot.evolutionsBySpecies.keys)
        for ((_, evos) in snapshot.evolutionsBySpecies) {
            for (evo in evos) allNames.add(SpeciesNameNormalizer.normalize(evo.toSpecies))
        }
        allNames.addAll(enriched.keys)
        allNames.addAll(snapshot.obtainmentBySpecies.keys)
        allNames.addAll(snapshot.fossilsBySpecies.keys)
        allNames.addAll(snapshot.ridingBySpecies.keys)

        val speciesEnumerationError = addRuntimeSpecies(allNames, runtimeSpeciesNames)

        val dropIndex = mutableMapOf<String, MutableList<String>>()
        for ((species, info) in enriched) {
            val drops = info.drops ?: continue
            for (drop in drops) {
                dropIndex.getOrPut(drop.itemId) { mutableListOf() }.add(species)
            }
        }

        val tmIndex = mutableMapOf<String, MutableList<String>>()
        for ((species, info) in enriched) {
            val tms = info.tmMoves ?: continue
            for (move in tms) {
                tmIndex.getOrPut(move.name.lowercase()) { mutableListOf() }.add(species)
            }
        }

        val sortedSpecies = allNames.sortedWith(
            compareBy<String> {
                val dex = enriched[it]?.nationalDexNumber ?: 0
                if (dex == 0) Int.MAX_VALUE else dex
            }.thenBy { it }
        )

        return Result(
            snapshot = snapshot.copy(
                evolutionsToSpecies = reverseMap,
                speciesInfo = enriched,
                dropsByItem = dropIndex,
                speciesByTmMove = tmIndex,
                allSpeciesNames = sortedSpecies,
            ),
            backfilledSpeciesInfoCount = backfilled,
            speciesEnumerationError = speciesEnumerationError,
        )
    }

    private fun loadRuntimeSpeciesNames(): Iterable<String> {
        val runtimeCount = try { PokemonSpecies.implemented.count() } catch (_: Exception) { 0 }
        if (runtimeCount <= 0) return emptyList()
        return PokemonSpecies.implemented.map { it.name }
    }

    private fun addRuntimeSpecies(
        allNames: MutableSet<String>,
        runtimeSpeciesNames: () -> Iterable<String>,
    ): String? {
        return try {
            for (speciesName in runtimeSpeciesNames()) {
                allNames.add(SpeciesNameNormalizer.normalize(speciesName))
            }
            null
        } catch (e: Exception) {
            e.message
        }
    }
}