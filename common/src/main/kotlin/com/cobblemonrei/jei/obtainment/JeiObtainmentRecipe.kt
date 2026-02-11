package com.cobblemonrei.jei.obtainment

import com.cobblemonrei.ObtainmentRecipeData

data class JeiObtainmentRecipe(val data: ObtainmentRecipeData) {
    val speciesName get() = data.speciesName
    val obtainment get() = data.obtainment
    val entryIndex get() = data.entryIndex
    val entryTotal get() = data.entryTotal
}
