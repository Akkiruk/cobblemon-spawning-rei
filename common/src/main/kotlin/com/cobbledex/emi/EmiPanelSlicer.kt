package com.cobbledex.emi

import com.cobbledex.PanelLayout
import com.cobbledex.RecipeHandle
import dev.emi.emi.config.EmiConfig
import net.minecraft.client.Minecraft

// EMI never scrolls inside a recipe, so panels taller than its recipe area are split into slices.
object EmiPanelSlicer {

    data class Slice(val top: Int, val bottom: Int, val shift: Int, val height: Int) {
        operator fun contains(y: Int) = y >= top && y < bottom
    }

    fun budget(): Int {
        val screen = Minecraft.getInstance().window.guiScaledHeight - 52 - EmiConfig.verticalMargin
        return (minOf(EmiConfig.maximumRecipeScreenHeight, screen) - 46).coerceAtLeast(80)
    }

    fun slice(handle: RecipeHandle, budget: Int = budget()): List<Slice> {
        val height = handle.height
        if (height <= budget) return listOf(Slice(0, height, 0, height))

        val blocked = BooleanArray(height + 1)
        val slotRows = (handle.slots.pokemon.map { it.y } + handle.slots.items.map { it.y })
            .map { it until it + PanelLayout.SLOT_SIZE }
        for (span in handle.layout.occupiedSpans() + slotRows) {
            for (y in maxOf(span.first + 1, 0)..minOf(span.last, height)) blocked[y] = true
        }

        val slices = mutableListOf<Slice>()
        var top = 0
        var lead = 0
        while (height - top + lead > budget) {
            val limit = top + budget - lead
            val cut = (limit downTo top + 1).firstOrNull { !blocked[it] } ?: limit
            // Consecutive slices never share a page, so fill it instead of showing EMI's background.
            slices += Slice(top, cut, top - lead, budget)
            top = cut
            lead = PanelLayout.PADDING
        }
        slices += Slice(top, height, top - lead, height - top + lead)
        return slices
    }
}
