package com.cobblemonrei.jei

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.item.PokemonItem
import com.cobblemonrei.SpawnDataIndex
import com.cobblemonrei.titleCase
import mezz.jei.api.ingredients.IIngredientRenderer
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import java.util.concurrent.ConcurrentHashMap

class PokemonIngredientRenderer : IIngredientRenderer<PokemonIngredient> {

    private val itemCache = ConcurrentHashMap<String, ItemStack>()
    private val speciesCache = ConcurrentHashMap<String, com.cobblemon.mod.common.pokemon.Species>()

    override fun render(graphics: GuiGraphics, ingredient: PokemonIngredient) {
        val itemStack = getOrCreateItem(ingredient.species) ?: return
        if (itemStack.isEmpty) return
        graphics.renderItem(itemStack, 0, 0)
    }

    override fun getTooltip(ingredient: PokemonIngredient, tooltipFlag: TooltipFlag): List<Component> {
        val lines = mutableListOf<Component>()
        lines.add(Component.literal(ingredient.displayName))

        val species = resolveSpecies(ingredient.species)
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

    private fun getOrCreateItem(species: String): ItemStack? {
        itemCache[species]?.let { return it }
        val speciesObj = resolveSpecies(species) ?: return null
        val item = try { PokemonItem.from(speciesObj) } catch (_: Exception) { null }
        if (item != null && !item.isEmpty) itemCache[species] = item
        return item
    }

    private fun resolveSpecies(species: String): com.cobblemon.mod.common.pokemon.Species? {
        speciesCache[species]?.let { return it }
        val resolved = try { PokemonSpecies.getByName(species) } catch (_: Exception) { null }
        if (resolved != null) speciesCache[species] = resolved
        return resolved
    }

    fun canRender(species: String): Boolean {
        val item = getOrCreateItem(species)
        return item != null && !item.isEmpty
    }
}
