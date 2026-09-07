package com.cobbledex

/**
 * One Pokémon Mark (Cobblemon 1.8.0+). Marks are earned on capture/encounter under various conditions
 * and grant a title. The registry carries the flavour data below; the *conditions* for earning each
 * mark live in Cobblemon's code, not the data, so the reference page states rarity and effect and
 * does not invent condition text.
 */
data class MarkInfo(
    val id: String,
    val nameKey: String,
    val descriptionKey: String,
    val titleKey: String?,
    /** Hex string like `"CE3A49"`, or null. */
    val titleColor: String?,
    /** Roll chance (0..1). `0` means the mark is not a plain random-chance mark. */
    val chance: Float,
    val group: String?,
    val sortOrder: Int,
    val indexNumber: Int?,
) {
    val rarityPercent: Double? get() = if (chance > 0f) chance * 100.0 else null
}
