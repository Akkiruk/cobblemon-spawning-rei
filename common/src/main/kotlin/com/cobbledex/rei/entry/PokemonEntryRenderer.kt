package com.cobbledex.rei.entry

import com.cobbledex.PokemonItemCache
import com.cobbledex.PokemonSpriteAtlas
import com.cobbledex.SpeciesNameNormalizer
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
        val decomp = SpeciesNameNormalizer.decomposeFormSpecies(pokemon.species)
        val aspects = pokemon.formAspects.ifEmpty { decomp.cobblemonAspects }

        val slotSize = bounds.width.coerceAtMost(bounds.height)
        if (PokemonSpriteAtlas.renderIfAvailable(graphics, pokemon.species, aspects, bounds.x, bounds.y, slotSize)) {
            return
        }

        val itemStack = PokemonItemCache.getRenderItem(pokemon.species, aspects)

        if (itemStack != null && !itemStack.isEmpty) {
            val poseStack = graphics.pose()
            poseStack.pushPose()

            val scale = slotSize.toFloat() / 16f
            poseStack.translate(bounds.x.toFloat(), bounds.y.toFloat(), 0f)
            poseStack.scale(scale, scale, 1f)

            try {
                graphics.renderItem(itemStack, 0, 0)
            } catch (t: Throwable) {
                PokemonItemCache.markRenderFailed(pokemon.species, aspects, t)
                poseStack.popPose()
                return
            }
            poseStack.popPose()
        }
    }

    fun canRender(species: String): Boolean = PokemonItemCache.canRender(species)

    override fun getTooltip(entry: EntryStack<PokemonEntry>, context: TooltipContext): Tooltip? {
        val pokemon = entry.value ?: return null
        val lines = SpawnDisplayHelper.buildPokemonTooltipLines(pokemon.species, pokemon.displayName)
        val tooltip = Tooltip.create(lines.first())
        lines.drop(1).forEach { tooltip.add(it) }
        return tooltip
    }

}
