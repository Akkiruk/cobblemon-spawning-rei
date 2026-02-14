package com.cobbledex.jei.fossil

import com.cobbledex.FossilRecipeData

data class JeiFossilRecipe(val data: FossilRecipeData) {
    val speciesName get() = data.speciesName
}
