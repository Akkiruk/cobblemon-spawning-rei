package com.cobbledex

data class RidingMount(
    val mountType: String,
    val ridingStyle: String,
    val speedMin: Int,
    val speedMax: Int,
    val accelMin: Int,
    val accelMax: Int,
    val skillMin: Int,
    val skillMax: Int,
    val jumpMin: Int,
    val jumpMax: Int,
    val staminaMin: Int,
    val staminaMax: Int,
)

data class RidingInfo(
    val pokemon: String,
    val allMountTypes: List<String>,
    val ridingStyles: List<String>,
    val seats: Int,
    val mounts: List<RidingMount>,
)

data class RidingRecipeData(
    val speciesName: String,
    val mount: RidingMount,
    val mountIndex: Int,
    val mountTotal: Int,
    val seats: Int,
    val allMountTypes: List<String>,
)
