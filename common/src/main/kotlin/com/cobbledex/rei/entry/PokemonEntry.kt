package com.cobbledex.rei.entry

import com.cobbledex.PokemonRef

data class PokemonEntry(
    override val species: String,
    override val formAspects: Set<String> = emptySet()
) : PokemonRef
