package com.cobbledex.jei.drops

import com.cobbledex.DropEntryInfo
import com.cobbledex.DropRecipeData

data class JeiDropRecipe(val data: DropRecipeData) {
    val speciesName get() = data.speciesName
    val drops get() = data.drops
}
