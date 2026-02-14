package com.cobbledex.jei.obtainment

import com.cobbledex.ObtainmentRecipeData

data class JeiObtainmentRecipe(val data: ObtainmentRecipeData) {
    val speciesName get() = data.speciesName
    val obtainment get() = data.obtainment
    val entryIndex get() = data.entryIndex
    val entryTotal get() = data.entryTotal
}
