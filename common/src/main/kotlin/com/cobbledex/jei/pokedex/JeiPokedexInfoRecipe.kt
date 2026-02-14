package com.cobbledex.jei.pokedex

import com.cobbledex.PokedexInfoRecipeData

data class JeiPokedexInfoRecipe(val data: PokedexInfoRecipeData) {
    val speciesName get() = data.speciesName
}
