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
) {
    /**
     * Every page of a family answers to the base species and to all of its forms. EMI and JEI index
     * pages by these keys up front and never ask for one species' pages, so a page keyed only by its
     * own form left that form's lookup with a single page and no way to reach its siblings.
     */
    fun lookupSpecies(): List<String> =
        (listOf(baseSpeciesName, form.formKey) + siblingFormKeys).distinct()
}
