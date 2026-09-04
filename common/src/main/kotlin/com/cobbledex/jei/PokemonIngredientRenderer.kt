package com.cobbledex.jei

import com.cobbledex.PokemonIconRenderer
import com.cobbledex.PokemonItemCache
import com.cobbledex.SpawnDisplayHelper
import mezz.jei.api.ingredients.IIngredientRenderer
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.world.item.TooltipFlag

class PokemonIngredientRenderer : IIngredientRenderer<PokemonIngredient> {

    override fun render(graphics: GuiGraphics, ingredient: PokemonIngredient) {
        // JEI renders ingredients pre-translated to the slot origin.
        PokemonIconRenderer.render(graphics, ingredient.species, ingredient.formAspects, 0, 0)
    }

    override fun getTooltip(ingredient: PokemonIngredient, tooltipFlag: TooltipFlag): List<Component> {
        return SpawnDisplayHelper.buildPokemonTooltipLines(ingredient.species, ingredient.displayName)
    }

    fun canRender(species: String): Boolean = PokemonItemCache.canRender(species)
}
