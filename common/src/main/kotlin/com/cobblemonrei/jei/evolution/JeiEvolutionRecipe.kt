package com.cobblemonrei.jei.evolution

import com.cobblemonrei.EvolutionRecipeData

data class JeiEvolutionRecipe(val data: EvolutionRecipeData) {
    val evolution get() = data.evolution
    val branchIndex get() = data.branchIndex
    val branchTotal get() = data.branchTotal
}
