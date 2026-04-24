package com.cobbledex.rei.entry

import com.cobbledex.PokemonSpriteService
import com.cobbledex.SpawnDisplayHelper
import me.shedaniel.math.Rectangle
import me.shedaniel.rei.api.client.entry.renderer.EntryRenderer
import me.shedaniel.rei.api.client.gui.widgets.Tooltip
import me.shedaniel.rei.api.client.gui.widgets.TooltipContext
import me.shedaniel.rei.api.common.entry.EntryStack
import net.minecraft.client.gui.GuiGraphics

class PokemonEntryRenderer : EntryRenderer<PokemonEntry> {

    override fun render(
        entry: EntryStack<PokemonEntry>,
        graphics: GuiGraphics,
        bounds: Rectangle,
        mouseX: Int,
        mouseY: Int,
        delta: Float
    ) {
        val pokemon = entry.value ?: return
        val slotSize = bounds.width.coerceAtMost(bounds.height)
        PokemonSpriteService.render(graphics, pokemon.species, pokemon.formAspects, bounds.x, bounds.y, slotSize)
    }

    fun canRender(species: String): Boolean = PokemonSpriteService.canRender(species)

    override fun getTooltip(entry: EntryStack<PokemonEntry>, context: TooltipContext): Tooltip? {
        val pokemon = entry.value ?: return null
        val lines = SpawnDisplayHelper.buildPokemonTooltipLines(pokemon.species, pokemon.displayName)
        val tooltip = Tooltip.create(lines.first())
        lines.drop(1).forEach { tooltip.add(it) }
        return tooltip
    }

}
