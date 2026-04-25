package com.cobbledex

data class ResolvedPokemonIcon(
    val requestedSpecies: String,
    val requestedAspects: Set<String>,
    val captureSpecies: String,
    val captureAspects: Set<String>,
) {
    val requestedIdentity: String
        get() = pokemonIdentityKey(requestedSpecies, requestedAspects)

    val captureIdentity: String
        get() = pokemonIdentityKey(captureSpecies, captureAspects)

    val cacheKey: String
        get() = "pokemon:$captureIdentity"
}

object PokemonIconResolver {

    fun resolve(species: String, explicitAspects: Set<String> = emptySet()): ResolvedPokemonIcon {
        return resolve(species, explicitAspects, SpawnDataIndex.getSpeciesInfo(species))
    }

    internal fun resolve(
        species: String,
        explicitAspects: Set<String>,
        info: EvolutionDataLoader.SpeciesBasicInfo?
    ): ResolvedPokemonIcon {
        val requestedSpecies = SpeciesNameNormalizer.normalize(species)
        val decomp = SpeciesNameNormalizer.decomposeFormSpecies(requestedSpecies)
        val explicit = normalizeAspects(explicitAspects)
        val indexedAspects = if (info?.isForm == true) info.formAspects else emptySet()
        val requestedAspects = normalizeAspects(explicit + indexedAspects + decomp.cobblemonAspects)

        val captureSpecies = when {
            info?.baseSpeciesName != null -> SpeciesNameNormalizer.normalize(info.baseSpeciesName)
            decomp.cobblemonAspects.isNotEmpty() -> SpeciesNameNormalizer.normalize(decomp.baseName)
            else -> requestedSpecies
        }

        return ResolvedPokemonIcon(
            requestedSpecies = requestedSpecies,
            requestedAspects = requestedAspects,
            captureSpecies = captureSpecies,
            captureAspects = requestedAspects,
        )
    }

    fun encodeCacheKey(species: String, explicitAspects: Set<String> = emptySet()): String =
        resolve(species, explicitAspects).cacheKey

    fun decodeCacheKey(cacheKey: String): ResolvedPokemonIcon? {
        if (!cacheKey.startsWith("pokemon:")) return null
        val encoded = cacheKey.removePrefix("pokemon:")
        val parts = encoded.split("|", limit = 2)
        val species = parts.firstOrNull().orEmpty()
        if (species.isBlank()) return null
        val aspects = parts.getOrNull(1)
            ?.split(",")
            ?: emptyList()
        val normalizedAspects = normalizeAspects(aspects)
        return ResolvedPokemonIcon(
            requestedSpecies = species,
            requestedAspects = normalizedAspects,
            captureSpecies = species,
            captureAspects = normalizedAspects,
        )
    }

    fun normalizeAspects(aspects: Iterable<String>): Set<String> = aspects
        .map { it.trim().lowercase() }
        .filter { it.isNotBlank() }
        .toSortedSet()
}