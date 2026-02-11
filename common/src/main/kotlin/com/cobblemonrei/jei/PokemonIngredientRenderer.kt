package com.cobblemonrei.jei

import com.cobblemonrei.PokemonItemCache
import com.cobblemonrei.SpawnDataIndex
import com.cobblemonrei.titleCase
import mezz.jei.api.ingredients.IIngredientRenderer
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.world.item.TooltipFlag

class PokemonIngredientRenderer : IIngredientRenderer<PokemonIngredient> {

    override fun render(graphics: GuiGraphics, ingredient: PokemonIngredient) {
        val itemStack = PokemonItemCache.getItem(ingredient.species) ?: return
        if (itemStack.isEmpty) return
        graphics.renderItem(itemStack, 0, 0)
    }

    override fun getTooltip(ingredient: PokemonIngredient, tooltipFlag: TooltipFlag): List<Component> {
        val lines = mutableListOf<Component>()
        lines.add(Component.literal(ingredient.displayName))

        val species = PokemonItemCache.resolveSpecies(ingredient.species)
        if (species != null) {
            lines.add(Component.literal("§7#${species.nationalPokedexNumber}"))
        }

        val info = SpawnDataIndex.getSpeciesInfo(ingredient.species)
        if (info != null) {
            val typeStr = buildString {
                append("§e")
                append(titleCase(info.primaryType))
                info.secondaryType?.let { append(" §7/ §e${titleCase(it)}") }
            }
            lines.add(Component.literal(typeStr))
            lines.add(Component.literal("§7Catch Rate: ${info.catchRate}"))
        }

        val spawns = SpawnDataIndex.getSpawnsFor(ingredient.species)
        if (spawns.isNotEmpty()) {
            lines.add(Component.literal("§a${spawns.size} spawn location(s)"))
        }

        return lines
    }

    fun canRender(species: String): Boolean = PokemonItemCache.canRender(species)
}
