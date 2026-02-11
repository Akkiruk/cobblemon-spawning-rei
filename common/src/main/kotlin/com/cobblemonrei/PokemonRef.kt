package com.cobblemonrei

import net.minecraft.resources.ResourceLocation

interface PokemonRef {
    val species: String
    val formAspects: Set<String>

    val identifier: ResourceLocation
        get() = ResourceLocation.fromNamespaceAndPath("cobblemon", sanitizePath(species))

    val displayName: String
        get() = titleCase(species)
}
