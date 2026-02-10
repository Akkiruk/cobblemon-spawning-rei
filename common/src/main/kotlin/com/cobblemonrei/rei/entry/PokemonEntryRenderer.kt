package com.cobblemonrei.rei.entry

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.item.PokemonItem
import com.cobblemonrei.DebugLog
import com.cobblemonrei.SpawnDataIndex
import com.cobblemonrei.titleCase
import me.shedaniel.math.Rectangle
import me.shedaniel.rei.api.client.entry.renderer.EntryRenderer
import me.shedaniel.rei.api.client.gui.widgets.Tooltip
import me.shedaniel.rei.api.client.gui.widgets.TooltipContext
import me.shedaniel.rei.api.common.entry.EntryStack
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack

class PokemonEntryRenderer : EntryRenderer<PokemonEntry> {

    private val itemCache = java.util.concurrent.ConcurrentHashMap<String, ItemStack?>()
    private val speciesCache = java.util.concurrent.ConcurrentHashMap<String, com.cobblemon.mod.common.pokemon.Species?>()

    override fun render(
        entry: EntryStack<PokemonEntry>,
        graphics: GuiGraphics,
        bounds: Rectangle,
        mouseX: Int,
        mouseY: Int,
        delta: Float
    ) {
        val pokemon = entry.value ?: return
        val itemStack = getOrCreateItem(pokemon.species)

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

    private fun getOrCreateItem(species: String): ItemStack? {
        return itemCache.getOrPut(species) {
            val speciesObj = resolveSpecies(species)
            if (speciesObj != null) {
                try { PokemonItem.from(speciesObj) } catch (_: Exception) { null }
            } else null
        }
    }

    fun resolveSpecies(species: String): com.cobblemon.mod.common.pokemon.Species? {
        return speciesCache.getOrPut(species) {
            try { PokemonSpecies.getByName(species) } catch (_: Exception) { null }
        }
    }

    fun canRender(species: String): Boolean {
        val item = getOrCreateItem(species)
        return item != null && !item.isEmpty
    }

    override fun getTooltip(entry: EntryStack<PokemonEntry>, context: TooltipContext): Tooltip? {
        val pokemon = entry.value ?: return null
        val species = resolveSpecies(pokemon.species)
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

    fun invalidateCaches() {
        itemCache.clear()
        speciesCache.clear()
    }
}
