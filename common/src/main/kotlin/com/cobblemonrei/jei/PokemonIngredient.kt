package com.cobblemonrei.jei

import net.minecraft.resources.ResourceLocation

data class PokemonIngredient(
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
