package com.cobbledex.rei.evolution

import com.cobbledex.CobbleDexMod
import com.cobbledex.DisplayLayout
import com.cobbledex.SpawnDisplayHelper
import com.cobbledex.rei.entry.PokemonEntry
import com.cobbledex.rei.entry.PokemonEntryType
import me.shedaniel.math.Point
import me.shedaniel.math.Rectangle
import me.shedaniel.rei.api.client.gui.Renderer
import me.shedaniel.rei.api.client.gui.widgets.Widget
import me.shedaniel.rei.api.client.gui.widgets.Widgets
import me.shedaniel.rei.api.client.registry.display.DisplayCategory
import me.shedaniel.rei.api.common.category.CategoryIdentifier
import me.shedaniel.rei.api.common.entry.EntryStack
import me.shedaniel.rei.api.common.util.EntryStacks
import net.minecraft.network.chat.Component
import net.minecraft.world.item.Items

class EvolutionCategory : DisplayCategory<EvolutionDisplay> {

    companion object {
        val ID: CategoryIdentifier<EvolutionDisplay> = CategoryIdentifier.of(
            CobbleDexMod.MOD_ID, "evolution"
        )
        private const val SLOT_SIZE = 18
        private const val ITEM_START_Y = 48
        private const val ITEM_ROW_HEIGHT = 20
    }

    override fun getCategoryIdentifier(): CategoryIdentifier<out EvolutionDisplay> = ID

    override fun getTitle(): Component = Component.translatable("category.cobbledex-rei-emi-jei.evolution")

    override fun getIcon(): Renderer = EntryStacks.of(Items.EXPERIENCE_BOTTLE)

    override fun getDisplayHeight(): Int = DisplayLayout.getMaxEvolutionSize().height

    override fun getFixedDisplaysPerPage(): Int = 1

    override fun getDisplayWidth(display: EvolutionDisplay): Int =
        DisplayLayout.measureEvolutionPanel(display.evolution, display.branchIndex, display.branchTotal).width

    override fun setupDisplay(display: EvolutionDisplay, bounds: Rectangle): List<Widget> {
        val widgets = mutableListOf<Widget>()
        val evo = display.evolution
        val size = DisplayLayout.measureEvolutionPanel(evo, display.branchIndex, display.branchTotal)
        
        // Center panel vertically within allocated bounds
        val yOffset = (bounds.height - size.height).coerceAtLeast(0) / 2
        val panelX = bounds.x
        val panelY = bounds.y + yOffset
        
        widgets.add(Widgets.createRecipeBase(Rectangle(panelX, panelY, size.width, size.height)))

        val fromStack = EntryStack.of(PokemonEntryType.POKEMON, PokemonEntry(evo.fromSpecies))
        widgets.add(
            Widgets.createSlot(Rectangle(panelX + 20, panelY + 8, SLOT_SIZE, SLOT_SIZE))
                .entries(listOf(fromStack))
                .markInput()
                .disableBackground()
        )

        val arrowY = panelY + 8 + (SLOT_SIZE - 17) / 2
        widgets.add(Widgets.createArrow(Point(panelX + size.width / 2 - 12, arrowY)))

        val toStack = EntryStack.of(PokemonEntryType.POKEMON, PokemonEntry(evo.toSpecies))
        widgets.add(
            Widgets.createSlot(Rectangle(panelX + size.width - 20 - SLOT_SIZE - 12, panelY + 8, SLOT_SIZE, SLOT_SIZE))
                .entries(listOf(toStack))
                .markOutput()
                .disableBackground()
        )

        val items = evo.itemRequirements
        for ((i, item) in items.withIndex()) {
            val stack = SpawnDisplayHelper.resolveItemStack(item.itemId)
            if (!stack.isEmpty) {
                widgets.add(
                    Widgets.createSlot(Rectangle(panelX + 8, panelY + ITEM_START_Y + i * ITEM_ROW_HEIGHT, SLOT_SIZE, SLOT_SIZE))
                        .entries(listOf(EntryStacks.of(stack)))
                        .markInput()
                )
            }
        }

        val w = size.width
        val h = size.height
        val hasItems = items.isNotEmpty()
        widgets.add(Widgets.createDrawableWidget { gfx, _, _, _ ->
            gfx.pose().pushPose()
            gfx.pose().translate(panelX.toFloat(), panelY.toFloat(), 0f)
            SpawnDisplayHelper.drawEvolutionText(gfx, evo, display.branchIndex, display.branchTotal, width = w, height = h, hasItemSlots = hasItems)
            gfx.pose().popPose()
        })

        return widgets
    }
}
