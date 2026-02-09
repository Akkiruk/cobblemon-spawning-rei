package com.cobblemonrei.rei.entry

import net.minecraft.resources.ResourceLocation

data class PokemonEntry(
    val species: String,
    val formAspects: Set<String> = emptySet()
) {
    val identifier: ResourceLocation
        get() = ResourceLocation.fromNamespaceAndPath("cobblemon", species.lowercase())

    val displayName: String
        get() = species.replace("_", " ")
            .split(" ")
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
}
