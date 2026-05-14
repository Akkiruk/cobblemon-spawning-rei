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
    val form: FormInfoEntry,
    val baseInfo: EvolutionDataLoader.SpeciesBasicInfo? = null,
    val siblingFormKeys: List<String> = listOf(form.formKey),
    val differenceReasons: List<String> = emptyList(),
    val pageIndex: Int = 1,
    val pageTotal: Int = 1,
    val totalForms: Int = pageTotal
)
