package com.cobbledex

data class PokedexInfoRecipeData(
    val speciesName: String,
    val abilities: List<String>,
    val hiddenAbility: String?,
    val eggGroups: List<String>,
    val maleRatio: Float?,
    val eggCycles: Int?,
    val catchRate: Int,
    val baseFriendship: Int?,
    val experienceGroup: String?,
    val baseExperienceYield: Int?,
    val height: Float,
    val weight: Float,
    val description: String?,
    val shoulderMountable: Boolean = false,
    val source: String? = null
)
