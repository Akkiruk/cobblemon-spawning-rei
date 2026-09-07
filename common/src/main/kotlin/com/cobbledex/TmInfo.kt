package com.cobbledex

/**
 * One native Technical Machine (Cobblemon 1.8.0+). Cobblemon's TM system is a single
 * `cobblemon:technical_machine` item whose move lives in a data component; each move that is a TM has
 * a recipe (Type Gem + up to two extra ingredients) crafted in the TM Machine.
 *
 * Third-party TM mods (`tmcraft`, `simpletms`) are unaffected — they are still handled by
 * [TmItemUtils] via their per-move item ids and have no recipe data here.
 */
data class TmInfo(
    /** Lower-cased Cobblemon move id. */
    val moveName: String,
    /** Elemental type of the TM (drives the required Type Gem), e.g. `"fire"`; null if unknown. */
    val elementalType: String?,
    val ingredients: List<TmIngredient>,
    /**
     * `true` — the recipe is always available. `false` — it unlocks once the player owns a Pokémon
     * that knows the move (or scans a Data Monitor holding the disc).
     */
    val passivelyObtained: Boolean,
    /** `"cobblemon"` = read from the synced registry; `"bundled"` = from the Cobblemon jar. */
    val source: String,
)

data class TmIngredient(
    /** Concrete item ids. When the recipe entry is a tag this is left empty and [tagId] is set. */
    val itemIds: List<String>,
    val count: Int,
    val tagId: String? = null,
) {
    val displayItemId: String? get() = itemIds.firstOrNull()
}
