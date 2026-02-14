package com.cobblemonrei.jei.pokedex

import com.cobblemonrei.PokedexInfoRecipeData

data class JeiPokedexInfoRecipe(val data: PokedexInfoRecipeData) {
    val speciesName get() = data.speciesName
}
