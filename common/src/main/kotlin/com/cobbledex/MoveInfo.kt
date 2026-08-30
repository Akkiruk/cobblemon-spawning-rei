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

data class LearnMethod(val label: String, val detail: String?)

/** One Pokémon that can learn a given TM move, plus every method by which it learns it. */
data class TmMoveLearner(
    val speciesName: String,
    val learnMethods: List<LearnMethod>
)

/**
 * A single page of the "which Pokémon can learn this TM move" grid. Shown when a TM item is
 * looked up in a recipe viewer. Learners are rendered as a grid of clickable Pokémon icons.
 */
data class TmMoveLearnersRecipeData(
    val moveName: String,
    val moveDetail: MoveDetail?,
    val learners: List<TmMoveLearner>,
    val pageIndex: Int,
    val pageTotal: Int
)
