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

/**
 * One unique move a species can learn, together with every method that reaches it. Produced by
 * de-duplicating the four separate learn lists ([LevelUpMove], egg, tutor, TM) so a move that is
 * learnable several ways is shown on a single row instead of once per method.
 */
data class MoveEntry(
    val move: MoveDetail,
    /** Sorted, distinct level-up levels; empty when the move is not learned by level-up. */
    val levelUpLevels: List<Int>,
    val egg: Boolean,
    val tutor: Boolean,
    val tm: Boolean,
) {
    val isLevelUp: Boolean get() = levelUpLevels.isNotEmpty()

    /** Section a move belongs to in the grouped-by-method layout (level-up wins, then egg/tutor/TM). */
    fun primaryMethod(): String = when {
        isLevelUp -> "levelup"
        egg -> "egg"
        tutor -> "tutor"
        else -> "tm"
    }
}

data class MovesRecipeData(
    val speciesName: String,
    val moves: List<MoveEntry>,
    val pageIndex: Int,
    val pageTotal: Int,
    /** When true the page is rendered as method sections; otherwise as one unified list. */
    val grouped: Boolean = false,
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
