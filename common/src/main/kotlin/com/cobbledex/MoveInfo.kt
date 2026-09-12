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
    /** Only learnable via a past-generation TM/tutor Cobblemon still records (e.g. Blissey + Toxic). */
    val legacy: Boolean = false,
) {
    val isLevelUp: Boolean get() = levelUpLevels.isNotEmpty()

    /**
     * Section a move belongs to in the grouped-by-method layout (level-up wins, then egg/tutor/TM,
     * legacy last - it's the least "current" of the five). Every case is explicit on purpose: a
     * catch-all `else` here would silently file a legacy-only move under whatever case came last.
     */
    fun primaryMethod(): String = when {
        isLevelUp -> "levelup"
        egg -> "egg"
        tutor -> "tutor"
        tm -> "tm"
        else -> "legacy"
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

/** One Pokémon that can learn a given move, plus every method by which it learns it. */
data class MoveLearner(
    val speciesName: String,
    val learnMethods: List<LearnMethod>
)

/**
 * A single page of the "which Pokémon can learn this move" grid. Shown when a move's disc is looked
 * up in a recipe viewer or its row is clicked on a Moves page. Learners are a grid of clickable
 * Pokémon icons, covering every learn method (level-up / egg / tutor / TM).
 */
data class MoveLearnersRecipeData(
    val moveName: String,
    val moveDetail: MoveDetail?,
    val learners: List<MoveLearner>,
    val pageIndex: Int,
    val pageTotal: Int
)
