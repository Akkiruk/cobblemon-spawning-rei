package com.cobblemonrei

import net.minecraft.resources.ResourceLocation

interface PokemonRef {
    val species: String
    val formAspects: Set<String>

    val identifier: ResourceLocation
        get() = ResourceLocation.fromNamespaceAndPath("cobblemon", species.lowercase())

    val displayName: String
        get() = species.replace("_", " ")
            .split(" ")
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
}
