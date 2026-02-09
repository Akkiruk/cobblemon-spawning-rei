package com.cobblemonrei

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.pokemon.evolution.Evolution

object EvolutionDataLoader {

    fun loadFromRuntime(): Map<String, List<EvolutionInfo>> {
        val result = mutableMapOf<String, MutableList<EvolutionInfo>>()
        var speciesCount = 0
        var evoCount = 0

        for (species in PokemonSpecies.implemented) {
            val evolutions = species.evolutions
            if (evolutions.isEmpty()) continue
            speciesCount++
            val fromName = species.name.lowercase()

            for (evo in evolutions) {
                try {
                    val info = parseEvolution(fromName, evo)
                    if (info != null) {
                        result.getOrPut(fromName) { mutableListOf() }.add(info)
                        evoCount++
                    }
                } catch (e: Exception) {
                    CobblemonSpawningMod.LOGGER.debug("Failed to parse evolution for $fromName: ${e.message}")
                }
            }
        }

        CobblemonSpawningMod.LOGGER.info("Parsed $evoCount evolutions from $speciesCount species (runtime API)")
        return result
    }

    private fun parseEvolution(fromSpecies: String, evo: Evolution): EvolutionInfo? {
        val id = evo.id
        val toSpecies = evo.result.species?.lowercase() ?: return null
        
        val variant = evo.javaClass.simpleName
            .replace("Evolution", "")
            .replace(Regex("([A-Z])"), "_$1")
            .lowercase()
            .trimStart('_')
            .ifEmpty { "level_up" }

        val requirements = mutableListOf<EvolutionRequirement>()
        for (req in evo.requirements) {
            val reqVariant = req.javaClass.simpleName.replace("Requirement", "").lowercase()
            requirements.add(EvolutionRequirement(reqVariant, emptyMap()))
        }

        return EvolutionInfo(
            id = id,
            fromSpecies = fromSpecies,
            toSpecies = toSpecies,
            variant = variant,
            requirements = requirements,
            requiredContext = null,
            consumeHeldItem = evo.consumeHeldItem
        )
    }

    data class SpeciesBasicInfo(
        val name: String,
        val nationalDexNumber: Int,
        val primaryType: String,
        val secondaryType: String?,
        val catchRate: Int,
        val weight: Float,
        val height: Float
    )

    fun loadSpeciesBasicInfoFromRuntime(): Map<String, SpeciesBasicInfo> {
        val result = mutableMapOf<String, SpeciesBasicInfo>()

        for (species in PokemonSpecies.implemented) {
            try {
                val name = species.name.lowercase()
                result[name] = SpeciesBasicInfo(
                    name = name,
                    nationalDexNumber = species.nationalPokedexNumber,
                    primaryType = species.primaryType.name.lowercase(),
                    secondaryType = species.secondaryType?.name?.lowercase(),
                    catchRate = species.catchRate,
                    weight = species.weight,
                    height = species.height
                )
            } catch (e: Exception) {
                CobblemonSpawningMod.LOGGER.debug("Failed to load species info for ${species.name}: ${e.message}")
            }
        }

        CobblemonSpawningMod.LOGGER.info("Loaded ${result.size} species from runtime API (includes datapacks/addons)")
        return result
    }
}
