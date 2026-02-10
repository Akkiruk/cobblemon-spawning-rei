package com.cobblemonrei.jei.spawn

import com.cobblemonrei.SpawnInfo

data class JeiSpawnRecipe(
    val speciesName: String,
    val spawn: SpawnInfo,
    val mergedFormVariants: List<String> = emptyList(),
    val bucketIndex: Int = 1,
    val bucketTotal: Int = 1
)
