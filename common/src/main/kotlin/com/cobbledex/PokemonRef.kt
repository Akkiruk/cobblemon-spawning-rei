package com.cobbledex

import net.minecraft.resources.ResourceLocation

interface PokemonRef {
    val species: String
    val formAspects: Set<String>

    val identifier: ResourceLocation
        get() {
            val parts = species.split(":", limit = 2)
            val namespace = if (parts.size > 1) parts[0] else "cobblemon"
            val path = if (parts.size > 1) parts[1] else species
            return ResourceLocation.fromNamespaceAndPath(namespace, sanitizePath(path))
        }

    val displayName: String
        get() = formatSpeciesName(if (species.contains(":")) species.substringAfter(":") else species)
}

fun parseAspectString(aspects: String): Set<String> =
    aspects.split(" ")
        .map { it.trim().lowercase() }
        .filter { it.isNotBlank() }
        .toSortedSet()

fun pokemonIdentityKey(species: String, formAspects: Set<String> = emptySet()): String {
    val normalizedSpecies = SpeciesNameNormalizer.normalize(species)
    val normalizedAspects = formAspects
        .map { it.trim().lowercase() }
        .filter { it.isNotBlank() }
        .toSortedSet()
    return if (normalizedAspects.isEmpty()) {
        normalizedSpecies
    } else {
        "$normalizedSpecies|${normalizedAspects.joinToString(",")}"
    }
}
