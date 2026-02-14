package com.cobbledex.jei

import com.cobbledex.PokemonRef

data class PokemonIngredient(
    override val species: String,
    override val formAspects: Set<String> = emptySet()
) : PokemonRef
