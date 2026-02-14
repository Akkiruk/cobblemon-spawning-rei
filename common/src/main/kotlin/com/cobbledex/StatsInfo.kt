package com.cobbledex

data class StatsRecipeData(
    val speciesName: String,
    val baseStats: Map<String, Int>,
    val baseStatTotal: Int,
    val primaryType: String,
    val secondaryType: String?
)
