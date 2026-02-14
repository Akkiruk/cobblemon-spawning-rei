package com.cobbledex

data class MoveDetail(
    val name: String,
    val type: String,
    val category: String,
    val power: Int,
    val accuracy: Int,
    val pp: Int
)

data class LevelUpMove(
    val level: Int,
    val moves: List<MoveDetail>
)

data class MovesRecipeData(
    val speciesName: String,
    val levelUpMoves: List<LevelUpMove>,
    val eggMoves: List<MoveDetail>,
    val tutorMoves: List<MoveDetail>,
    val tmMoves: List<MoveDetail>,
    val pageIndex: Int,
    val pageTotal: Int
)
