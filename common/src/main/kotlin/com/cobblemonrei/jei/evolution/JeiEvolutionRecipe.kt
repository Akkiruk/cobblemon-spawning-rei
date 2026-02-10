package com.cobblemonrei.jei.evolution

import com.cobblemonrei.EvolutionInfo

data class JeiEvolutionRecipe(
    val evolution: EvolutionInfo,
    val branchIndex: Int = 0,
    val branchTotal: Int = 0
)
