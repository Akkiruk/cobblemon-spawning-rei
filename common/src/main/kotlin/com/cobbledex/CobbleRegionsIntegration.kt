package com.cobbledex

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobbledex.network.SpawnRegionInfo

/** Optional CobbleRegions integration with no compile-time dependency. */
object CobbleRegionsIntegration {

    fun regionsBySpecies(speciesIds: Collection<String>): Map<String, List<SpawnRegionInfo>> {
        val api = runCatching {
            val type = Class.forName("dev.cobbleregions.api.CobbleRegionsApi")
            val instance = type.getField("INSTANCE").get(null)
            val method = type.getMethod(
                "spawnRegionsForSpecies",
                String::class.java,
                Int::class.javaPrimitiveType,
            )
            instance to method
        }.getOrNull() ?: return emptyMap()

        val regionsBySpecies = linkedMapOf<String, List<SpawnRegionInfo>>()
        for (speciesId in speciesIds) {
            val dexNumber = PokemonSpecies.getByName(speciesId)?.nationalPokedexNumber ?: -1
            val rawRegions: Iterable<*> = runCatching {
                api.second.invoke(api.first, speciesId, dexNumber) as? Iterable<*>
            }
                .getOrNull()
                ?: emptyList<Any>()
            val regions: List<SpawnRegionInfo> = rawRegions.mapNotNull { region: Any? ->
                if (region == null) null else toSpawnRegionInfo(region)
            }
            if (regions.isNotEmpty()) {
                regionsBySpecies[SpeciesNameNormalizer.normalize(speciesId)] = regions
            }
        }
        return regionsBySpecies
    }

    private fun toSpawnRegionInfo(region: Any): SpawnRegionInfo? = runCatching {
        val type = region.javaClass
        SpawnRegionInfo(
            id = type.getMethod("getId").invoke(region) as String,
            displayName = type.getMethod("getDisplayName").invoke(region) as String,
        )
    }.getOrNull()
}