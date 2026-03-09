package com.cobbledex.jei

import com.cobbledex.PokemonItemCache
import com.cobbledex.SpeciesNameNormalizer
import com.cobbledex.SpawnDataIndex
import com.cobbledex.SpawnDisplayHelper
import com.cobbledex.formatSpeciesName
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
        // Append job names as invisible text for JEI search indexing
        val jobs = SpawnDataIndex.getJobsFor(ingredient.species)
        val searchParts = mutableListOf<String>()
        if (jobs.isNotEmpty()) {
            searchParts.add(jobs.joinToString(" ") { "job:${it.rule.id} ${it.rule.displayName}" })
        }
        // Include base species name so forms are findable by base name
        val info = SpawnDataIndex.getSpeciesInfo(ingredient.species)
        if (info != null && info.isForm && info.baseSpeciesName != null) {
            searchParts.add(formatSpeciesName(info.baseSpeciesName))
        }
        if (searchParts.isNotEmpty()) {
            lines.add(Component.literal(searchParts.joinToString(" ")).withStyle(ChatFormatting.BLACK))
        }
        return lines
    }

    fun canRender(species: String): Boolean = PokemonItemCache.canRender(species)
}
