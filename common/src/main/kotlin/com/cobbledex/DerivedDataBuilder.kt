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
        // Resolve aspect-qualified results (e.g. Riolu -> "lucario y", the
        // Midnight form) to the specific form's own key, not just the bare
        // species name - otherwise the reverse map only ever pointed at base
        // Lucario, so Lucario (Midnight)'s own page never showed it came
        // from Riolu. Snapshot.speciesInfo already has form entries (with
        // their aspects) synced/loaded before this runs, so lookups work
        // even though `enriched` below hasn't been built yet.
        // "Fusion" packs (e.g. Starlight Fusion) register their fusion results
        // as plain forms with no real Cobblemon Evolution behind them at all -
        // the only place a fusion is documented is the pokedex flavor text,
        // and its phrasing isn't consistent ("A fusion of X and Y,
        // possessing...", "Necrozma appears to have fully absorbed
        // Metagross.", "...after fusing X and Y!"). Rather than match one
        // sentence shape, gate on a loose fusion-indicator keyword and scan
        // the whole resolved description for every known base species name
        // that appears in it, then synthesize a forward evolution edge from
        // *every* component species into the fusion result - including the
        // fusion's own base species, so it shows up directly in that
        // species' own evolution chain too, not just its separate "Otras
        // formas/Megas" panel. Mirrors the equivalent fix in the companion
        // Pokedex website's pipeline (pokedex-site/pipeline/src/index.ts).
        val baseSpeciesNames = try {
            com.cobblemon.mod.common.api.pokemon.PokemonSpecies.implemented.map { it.name.lowercase() }
        } catch (_: Exception) { emptyList() }
        val fusionIndicator = Regex("""\b(fusion|fusing|fused|absorbed?|absorbs)\b""", RegexOption.IGNORE_CASE)
        val mutableEvolutionsBySpecies = snapshot.evolutionsBySpecies.mapValues { it.value.toMutableList() }.toMutableMap()
        var fusionEdgeCount = 0
        for ((formKey, info) in snapshot.speciesInfo) {
            val descKey = info.description ?: continue
            val descText = try { tr(descKey) } catch (_: Exception) { continue }
            if (descText == descKey || descText.isBlank()) continue
            if (!fusionIndicator.containsMatchIn(descText)) continue
            val toSpecies = info.baseSpeciesName?.let { SpeciesNameNormalizer.normalize(it) } ?: formKey
            val toAspects = info.formAspects
            val componentNames = baseSpeciesNames.filter { name ->
                Regex("\\b${Regex.escape(name)}\\b", RegexOption.IGNORE_CASE).containsMatchIn(descText)
            }
            if (componentNames.size < 2) continue
            for (component in componentNames) {
                val others = componentNames.filter { it != component }.joinToString(" + ") { titleCase(it) }
                val evo = EvolutionInfo(
                    id = "fusion_${component}_$formKey",
                    fromSpecies = component,
                    fromAspects = emptySet(),
                    toSpecies = toSpecies,
                    toAspects = toAspects,
                    variant = "fusion",
                    requirements = listOf(EvolutionRequirement("fusion_with", mapOf("partner" to others))),
                    requiredContext = null,
                    consumeHeldItem = false,
                )
                mutableEvolutionsBySpecies.getOrPut(component) { mutableListOf() }.add(evo)
                fusionEdgeCount++
            }
        }
        if (fusionEdgeCount > 0) DebugLog.info("Synthesized $fusionEdgeCount fusion evolution edges from pokedex description text.")

        // Resolve aspect-qualified results (e.g. Riolu -> "lucario y", the
        // Midnight form) to the specific form's own key, not just the bare
        // species name - otherwise the reverse map only ever pointed at base
        // Lucario, so Lucario (Midnight)'s own page never showed it came
        // from Riolu. Snapshot.speciesInfo already has form entries (with
        // their aspects) synced/loaded before this runs, so lookups work
        // even though `enriched` below hasn't been built yet.
        val reverseLookupQueries = CobbleDexDataQueries(snapshot)
        val reverseMap = mutableMapOf<String, MutableList<EvolutionInfo>>()
        for ((_, evolutions) in mutableEvolutionsBySpecies) {
            for (evo in evolutions) {
                val normalizedTo = RecipeBuilder.resolveEvolutionTargetKey(evo.toSpecies, evo.toAspects, snapshot, reverseLookupQueries)
                reverseMap.getOrPut(normalizedTo) { mutableListOf() }.add(evo)
            }
        }

        val enriched = snapshot.speciesInfo.toMutableMap()
        var backfilled = 0
        for (key in mutableEvolutionsBySpecies.keys) {
            if (key in enriched) continue
            val evos = mutableEvolutionsBySpecies[key] ?: continue
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
        allNames.addAll(mutableEvolutionsBySpecies.keys)
        for ((_, evos) in mutableEvolutionsBySpecies) {
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
                evolutionsBySpecies = mutableEvolutionsBySpecies,
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