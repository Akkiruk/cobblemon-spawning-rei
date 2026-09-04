package com.cobbledex

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies

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
        }.getOrElse { e ->
            // ClassNotFoundException just means CobbleRegions isn't installed - the overwhelming
            // majority of sessions, and not worth a log line. Anything else means the mod IS
            // installed but its API doesn't look like we expect (a version bump renamed or
            // resignatured something) - that's a real, previously silent integration break, so say
            // so once rather than quietly returning nothing forever.
            if (e !is ClassNotFoundException) {
                DebugLog.warnOnce("cobbleregions-api") { "CobbleRegions integration failed: ${e.message}" }
            }
            return emptyMap()
        }

        val regionsBySpecies = linkedMapOf<String, List<SpawnRegionInfo>>()
        for (speciesId in speciesIds) {
            val dexNumber = PokemonSpecies.getByName(speciesId)?.nationalPokedexNumber ?: -1
            val rawRegions: Iterable<*> = runCatching {
                api.second.invoke(api.first, speciesId, dexNumber) as? Iterable<*>
            }.onFailure { e ->
                DebugLog.warnOnce("cobbleregions-invoke") { "CobbleRegions lookup failed: ${e.message}" }
            }.getOrNull() ?: emptyList<Any>()
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
    }.onFailure { e ->
        DebugLog.warnOnce("cobbleregions-shape") { "CobbleRegions region object has an unexpected shape: ${e.message}" }
    }.getOrNull()
}
