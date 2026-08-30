package com.cobbledex.rei.entry

import me.shedaniel.math.Rectangle
import me.shedaniel.rei.api.client.entry.renderer.EntryRenderer
import me.shedaniel.rei.api.client.gui.widgets.Tooltip
import me.shedaniel.rei.api.client.gui.widgets.TooltipContext
import me.shedaniel.rei.api.common.entry.EntryStack
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component

/**
 * A move entry is only ever used as an invisible click target on the Moves page, never shown as a
 * slot, so rendering is a no-op. The tooltip is only relevant if REI ever surfaces one in search.
 */
class MoveEntryRenderer : EntryRenderer<MoveEntry> {

    override fun render(
        entry: EntryStack<MoveEntry>,
        graphics: GuiGraphics,
        bounds: Rectangle,
        mouseX: Int,
        mouseY: Int,
        delta: Float,
    ) {
        // no-op
    }

    override fun getTooltip(entry: EntryStack<MoveEntry>, context: TooltipContext): Tooltip? {
        val value = entry.value ?: return null
        val key = "cobblemon.move.${value.moveName}"
        val name = Component.translatable(key).let { c ->
            if (c.string == key) Component.literal(
                value.moveName.replace('_', ' ').replaceFirstChar { it.uppercase() }
            ) else c
        }
        return Tooltip.create(name)
    }
}
