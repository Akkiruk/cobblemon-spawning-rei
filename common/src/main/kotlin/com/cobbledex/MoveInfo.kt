package com.cobbledex

data class LevelUpMove(
    val level: Int,
    val moves: List<String>
)

data class MovesRecipeData(
    val speciesName: String,
    val levelUpMoves: List<LevelUpMove>,
    val eggMoves: List<String>,
    val tutorMoves: List<String>,
    val pageIndex: Int,
    val pageTotal: Int
)
