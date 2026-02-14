package com.cobblemonrei.jei.drops

import com.cobblemonrei.DropEntryInfo
import com.cobblemonrei.DropRecipeData

data class JeiDropRecipe(val data: DropRecipeData) {
    val speciesName get() = data.speciesName
    val drops get() = data.drops
}
