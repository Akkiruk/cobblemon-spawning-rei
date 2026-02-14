package com.cobblemonrei.jei.stats

import com.cobblemonrei.StatsRecipeData

data class JeiStatsRecipe(val data: StatsRecipeData) {
    val speciesName get() = data.speciesName
}
