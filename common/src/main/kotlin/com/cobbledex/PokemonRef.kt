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
