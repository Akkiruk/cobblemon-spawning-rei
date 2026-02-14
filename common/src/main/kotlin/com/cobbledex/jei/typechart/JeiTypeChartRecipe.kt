package com.cobbledex.jei.typechart

import com.cobbledex.TypeChartRecipeData

data class JeiTypeChartRecipe(val data: TypeChartRecipeData) {
    val speciesName get() = data.speciesName
}
