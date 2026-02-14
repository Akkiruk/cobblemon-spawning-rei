package com.cobbledex.jei.evolution

import com.cobbledex.EvolutionRecipeData

data class JeiEvolutionRecipe(val data: EvolutionRecipeData) {
    val evolution get() = data.evolution
    val branchIndex get() = data.branchIndex
    val branchTotal get() = data.branchTotal
}
