package com.cobblemonrei.rei.entry

import com.cobblemonrei.PokemonRef

data class PokemonEntry(
    override val species: String,
    override val formAspects: Set<String> = emptySet()
) : PokemonRef
