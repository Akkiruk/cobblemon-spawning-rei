package com.cobblemonrei.rei.drops

import com.cobblemonrei.CobblemonSpawningMod
import com.cobblemonrei.DisplayLayout
import com.cobblemonrei.SpawnDisplayHelper
import com.cobblemonrei.rei.entry.PokemonEntry
import com.cobblemonrei.rei.entry.PokemonEntryType
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

class DropCategory : DisplayCategory<DropDisplay> {

    companion object {
        val ID: CategoryIdentifier<DropDisplay> = CategoryIdentifier.of(
            CobblemonSpawningMod.MOD_ID, "drops"
        )
    }

    override fun getCategoryIdentifier(): CategoryIdentifier<out DropDisplay> = ID

    override fun getTitle(): Component = Component.translatable("category.cobblemon-spawning-rei.drops")

    override fun getIcon(): Renderer = EntryStacks.of(Items.DIAMOND)

    override fun getDisplayHeight(): Int = DisplayLayout.getMaxDropSize().height

    override fun getFixedDisplaysPerPage(): Int = 1

    override fun getDisplayWidth(display: DropDisplay): Int =
        DisplayLayout.measureDropPanel(display.speciesName, display.drops).width

    override fun setupDisplay(display: DropDisplay, bounds: Rectangle): List<Widget> {
        val widgets = mutableListOf<Widget>()
        val size = DisplayLayout.measureDropPanel(display.speciesName, display.drops)

        val yOffset = (bounds.height - size.height).coerceAtLeast(0) / 2
        val panelX = bounds.x
        val panelY = bounds.y + yOffset

        widgets.add(Widgets.createRecipeBase(Rectangle(panelX, panelY, size.width, size.height)))

        val pokemonStack = EntryStack.of(PokemonEntryType.POKEMON, PokemonEntry(display.speciesName))
        widgets.add(
            Widgets.createSlot(Rectangle(panelX + 8, panelY + 3, 20, 20))
                .entries(listOf(pokemonStack))
                .markInput()
                .disableBackground()
                .disableHighlight()
        )

        // Item output slots
        val itemSlotX = panelX + 8
        var itemSlotY = panelY + 34
        for (drop in display.drops) {
            val itemStack = SpawnDisplayHelper.resolveItemStack(drop.itemId)
            if (!itemStack.isEmpty) {
                widgets.add(
                    Widgets.createSlot(Rectangle(itemSlotX, itemSlotY, 18, 18))
                        .entries(listOf(EntryStacks.of(itemStack)))
                        .markOutput()
                        .disableBackground()
                )
            }
            itemSlotY += 20
        }

        val w = size.width
        val h = size.height
        widgets.add(Widgets.createDrawableWidget { gfx, _, _, _ ->
            gfx.pose().pushPose()
            gfx.pose().translate(panelX.toFloat(), panelY.toFloat(), 0f)
            SpawnDisplayHelper.drawDropDetails(gfx, display.speciesName, display.drops, width = w, height = h)
            gfx.pose().popPose()
        })

        return widgets
    }
}
