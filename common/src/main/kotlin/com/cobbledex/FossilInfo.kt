package com.cobbledex

data class FossilCombo(
    val resultSpecies: String,
    val fossilItems: List<String>,
    val extraTags: String? = null
)

data class FossilRecipeData(
    val speciesName: String,
    val fossilItems: List<String>,
    val extraTags: String? = null
)
