package com.cobbledex.jei.moves

import com.cobbledex.MovesRecipeData

data class JeiMovesRecipe(val data: MovesRecipeData) {
    val speciesName get() = data.speciesName
}
