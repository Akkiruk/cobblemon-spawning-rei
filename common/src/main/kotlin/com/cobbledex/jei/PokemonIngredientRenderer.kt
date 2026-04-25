package com.cobbledex.jei

import com.cobbledex.PokemonItemCache
import com.cobbledex.SpeciesNameNormalizer
import com.cobbledex.PokemonSearchTerms
import com.cobbledex.SpawnDataIndex
import com.cobbledex.SpawnDisplayHelper
import mezz.jei.api.ingredients.IIngredientRenderer
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.world.item.TooltipFlag

class PokemonIngredientRenderer : IIngredientRenderer<PokemonIngredient> {

    override fun render(graphics: GuiGraphics, ingredient: PokemonIngredient) {
        val decomp = SpeciesNameNormalizer.decomposeFormSpecies(ingredient.species)
        val aspects = ingredient.formAspects.ifEmpty { decomp.cobblemonAspects }
        val itemStack = PokemonItemCache.getItem(ingredient.species, aspects) ?: return
        if (itemStack.isEmpty) return
        graphics.renderItem(itemStack, 0, 0)
    }

    override fun getTooltip(ingredient: PokemonIngredient, tooltipFlag: TooltipFlag): List<Component> {
        val lines = SpawnDisplayHelper.buildPokemonTooltipLines(ingredient.species, ingredient.displayName).toMutableList()
        val searchText = PokemonSearchTerms.buildSearchText(
            ingredient.species,
            ingredient.displayName,
            includeDisplayName = false,
        )
        if (searchText.isNotBlank()) {
            lines.add(Component.literal(searchText).withStyle(ChatFormatting.BLACK))
        }
        return lines
    }

    fun canRender(species: String): Boolean = PokemonItemCache.canRender(species)
}
