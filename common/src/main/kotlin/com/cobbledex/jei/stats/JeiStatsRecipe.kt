package com.cobbledex.jei.stats

import com.cobbledex.StatsRecipeData

data class JeiStatsRecipe(val data: StatsRecipeData) {
    val speciesName get() = data.speciesName
}
