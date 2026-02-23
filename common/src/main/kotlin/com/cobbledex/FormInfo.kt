package com.cobbledex

data class FormInfoEntry(
    val formKey: String,
    val formDisplayName: String,
    val primaryType: String,
    val secondaryType: String?,
    val abilities: List<String>,
    val hiddenAbility: String?,
    val baseStats: Map<String, Int>?,
    val baseStatTotal: Int?,
    val formAspects: Set<String>
)

data class FormRecipeData(
    val baseSpeciesName: String,
    val forms: List<FormInfoEntry>
)
