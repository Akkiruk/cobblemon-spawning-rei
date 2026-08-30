package com.cobbledex.jei

/** A move as a JEI ingredient. Only used to key navigation; never rendered in a slot. */
data class MoveIngredient(val move: String) {
    val normalized: String = move.lowercase()
    val displayName: String = move.replace('_', ' ').replaceFirstChar { it.uppercase() }
}
