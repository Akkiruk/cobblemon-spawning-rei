package com.cobblemonrei.jei.obtainment

import com.cobblemonrei.ObtainmentInfo

data class JeiObtainmentRecipe(
    val speciesName: String,
    val obtainment: ObtainmentInfo,
    val entryIndex: Int = 1,
    val entryTotal: Int = 1
)
