package com.cobblemonrei.rei.entry

import com.cobblemonrei.PokemonItemCache
import com.cobblemonrei.SpawnDataIndex
import com.cobblemonrei.titleCase
import me.shedaniel.math.Rectangle
import me.shedaniel.rei.api.client.entry.renderer.EntryRenderer
import me.shedaniel.rei.api.client.gui.widgets.Tooltip
import me.shedaniel.rei.api.client.gui.widgets.TooltipContext
import me.shedaniel.rei.api.common.entry.EntryStack
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component

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
        val itemStack = PokemonItemCache.getItem(pokemon.species)

        if (itemStack != null && !itemStack.isEmpty) {
            val poseStack = graphics.pose()
            poseStack.pushPose()

            val slotSize = bounds.width.coerceAtMost(bounds.height).toFloat()
            val scale = slotSize / 16f
            poseStack.translate(bounds.x.toFloat(), bounds.y.toFloat(), 0f)
            poseStack.scale(scale, scale, 1f)

            graphics.renderItem(itemStack, 0, 0)
            poseStack.popPose()
        }
    }

    fun canRender(species: String): Boolean = PokemonItemCache.canRender(species)

    override fun getTooltip(entry: EntryStack<PokemonEntry>, context: TooltipContext): Tooltip? {
        val pokemon = entry.value ?: return null
        val species = PokemonItemCache.resolveSpecies(pokemon.species)
        val tooltip = Tooltip.create(Component.literal(pokemon.displayName))
        if (species != null) {
            tooltip.add(Component.literal("§7#${species.nationalPokedexNumber}"))
        }
        val info = SpawnDataIndex.getSpeciesInfo(pokemon.species)
        if (info != null) {
            val typeStr = buildString {
                append("§e")
                append(titleCase(info.primaryType))
                info.secondaryType?.let { append(" §7/ §e${titleCase(it)}") }
            }
            tooltip.add(Component.literal(typeStr))
            tooltip.add(Component.literal("§7Catch Rate: ${info.catchRate}"))
        }
        val spawns = SpawnDataIndex.getSpawnsFor(pokemon.species)
        if (spawns.isNotEmpty()) {
            tooltip.add(Component.literal("§a${spawns.size} spawn location(s)"))
        }
        return tooltip
    }

}
