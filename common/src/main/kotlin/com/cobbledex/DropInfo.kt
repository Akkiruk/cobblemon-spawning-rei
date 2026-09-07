package com.cobbledex

enum class DropTrigger { DEFEAT, EVOLUTION }

data class DropEntryInfo(
    val itemId: String,
    val percentage: Float,
    val quantity: Int,
    val quantityRange: String? = null,
    /** When this item is dropped. [DropTrigger.EVOLUTION] = dropped when the Pokémon evolves (1.8.0+). */
    val trigger: DropTrigger = DropTrigger.DEFEAT,
) {
    val isEvolutionDrop: Boolean get() = trigger == DropTrigger.EVOLUTION
    val displayQuantity: String
        get() = quantityRange ?: quantity.toString()

    val displayPercentage: String
        get() {
            if (percentage >= 100f) return "100%"
            if (percentage == percentage.toLong().toFloat()) return "${percentage.toLong()}%"
            return "%.1f%%".format(percentage)
        }
}

data class DropRecipeData(
    val speciesName: String,
    val drops: List<DropEntryInfo>,
    val pageIndex: Int = 1,
    val pageTotal: Int = 1,
    val totalDrops: Int = drops.size
)

/** One Pokémon that drops a given item, plus every drop-table entry that yields it. */
data class ItemDropper(
    val speciesName: String,
    val entries: List<DropEntryInfo>
) {
    /** Best (highest) drop chance among this species' entries for the item, for sorting/summary. */
    val bestPercentage: Float get() = entries.maxOfOrNull { it.percentage } ?: 0f
}

/**
 * A single page of the "which Pokémon drop this item" grid. Shown when an item is looked up in a
 * recipe viewer. Droppers are rendered as a grid of clickable, dex-ordered Pokémon icons; hovering a
 * cell names the Pokémon and its drop chance / quantity, clicking drills into that Pokémon's drops.
 */
data class ItemDroppersRecipeData(
    val itemId: String,
    val droppers: List<ItemDropper>,
    val pageIndex: Int,
    val pageTotal: Int,
    val totalDroppers: Int = droppers.size
)
