package com.cobbledex

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.riding.stats.RidingStat

object RidingDataLoader {

    fun loadFromRuntime(): Map<String, RidingInfo> {
        val implemented = try {
            PokemonSpecies.implemented.toList()
        } catch (e: Exception) {
            DebugLog.warnOnce("riding-species-load") { "Failed to access PokemonSpecies.implemented: ${e.message}" }
            return emptyMap()
        }
        if (implemented.isEmpty()) return emptyMap()

        val result = mutableMapOf<String, RidingInfo>()

        for (species in implemented) {
            val baseName = SpeciesNameNormalizer.normalize(species.name)

            val riding = try { species.standardForm.riding } catch (_: Exception) { continue }
            val behaviours = riding.behaviours ?: continue
            if (behaviours.isEmpty()) continue

            val seats = riding.seats.size.coerceAtLeast(1)
            val allMountTypes = behaviours.keys.map { it.name }
            val ridingStyles = behaviours.values.mapNotNull { extractStyleName(it.key.path) }.distinct()

            val mounts = behaviours.map { (style, behaviour) ->
                val stats = behaviour.stats
                RidingMount(
                    mountType = style.name,
                    ridingStyle = extractStyleName(behaviour.key.path) ?: style.name.lowercase(),
                    speedMin = stats[RidingStat.SPEED]?.first ?: 0,
                    speedMax = stats[RidingStat.SPEED]?.last ?: 0,
                    accelMin = stats[RidingStat.ACCELERATION]?.first ?: 0,
                    accelMax = stats[RidingStat.ACCELERATION]?.last ?: 0,
                    skillMin = stats[RidingStat.SKILL]?.first ?: 0,
                    skillMax = stats[RidingStat.SKILL]?.last ?: 0,
                    jumpMin = stats[RidingStat.JUMP]?.first ?: 0,
                    jumpMax = stats[RidingStat.JUMP]?.last ?: 0,
                    staminaMin = stats[RidingStat.STAMINA]?.first ?: 0,
                    staminaMax = stats[RidingStat.STAMINA]?.last ?: 0,
                )
            }

            if (mounts.isNotEmpty()) {
                result[baseName] = RidingInfo(
                    pokemon = baseName,
                    allMountTypes = allMountTypes,
                    ridingStyles = ridingStyles,
                    seats = seats,
                    mounts = mounts,
                )
            }
        }

        DebugLog.info("Loaded riding data for ${result.size} species (${result.values.sumOf { it.mounts.size }} mount entries)")
        return result
    }

    /** Extracts riding style name from behaviour key path like "air/bird" -> "bird" */
    private fun extractStyleName(path: String): String? {
        val afterSlash = path.substringAfterLast('/', "")
        return afterSlash.ifBlank { null }
    }
}
