package com.cobbledex.jei.spawn

import com.cobbledex.SpawnRecipeData

data class JeiSpawnRecipe(val data: SpawnRecipeData) {
    val speciesName get() = data.speciesName
    val spawn get() = data.spawn
    val mergedFormVariants get() = data.mergedFormVariants
    val bucketIndex get() = data.bucketIndex
    val bucketTotal get() = data.bucketTotal
}
