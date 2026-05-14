package com.cobbledex.jei

import com.cobbledex.PokemonItemCache
import com.cobbledex.SpeciesNameNormalizer
import com.cobbledex.SpawnDisplayHelper
import mezz.jei.api.ingredients.IIngredientRenderer
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.world.item.TooltipFlag

class PokemonIngredientRenderer : IIngredientRenderer<PokemonIngredient> {

    override fun render(graphics: GuiGraphics, ingredient: PokemonIngredient) {
        val decomp = SpeciesNameNormalizer.decomposeFormSpecies(ingredient.species)
        val aspects = ingredient.formAspects.ifEmpty { decomp.cobblemonAspects }
        val itemStack = PokemonItemCache.getRenderItem(ingredient.species, aspects) ?: return
        if (itemStack.isEmpty) return
        try {
            graphics.renderItem(itemStack, 0, 0)
        } catch (t: Throwable) {
            PokemonItemCache.markRenderFailed(ingredient.species, aspects, t)
        }
    }

    override fun getTooltip(ingredient: PokemonIngredient, tooltipFlag: TooltipFlag): List<Component> {
        return SpawnDisplayHelper.buildPokemonTooltipLines(ingredient.species, ingredient.displayName)
    }

    fun canRender(species: String): Boolean = PokemonItemCache.canRender(species)
}
