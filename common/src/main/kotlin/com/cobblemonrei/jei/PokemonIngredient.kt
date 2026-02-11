package com.cobblemonrei.jei

import com.cobblemonrei.PokemonRef

data class PokemonIngredient(
    override val species: String,
    override val formAspects: Set<String> = emptySet()
) : PokemonRef
