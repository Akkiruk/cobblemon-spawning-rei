package com.cobbledex.rei.typechart

import com.cobbledex.CobbleDexMod
import com.cobbledex.DisplayLayout
import com.cobbledex.SpawnDisplayHelper
import com.cobbledex.rei.entry.PokemonEntry
import com.cobbledex.rei.entry.PokemonEntryType
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

class TypeChartCategory : DisplayCategory<TypeChartDisplay> {

    companion object {
        val ID: CategoryIdentifier<TypeChartDisplay> = CategoryIdentifier.of(
            CobbleDexMod.MOD_ID, "type_chart"
        )
    }

    override fun getCategoryIdentifier(): CategoryIdentifier<out TypeChartDisplay> = ID

    override fun getTitle(): Component = Component.translatable("category.cobbledex-rei-emi-jei.type_chart")

    override fun getIcon(): Renderer = EntryStacks.of(Items.SHIELD)

    override fun getDisplayHeight(): Int = DisplayLayout.getMaxTypeChartSize().height

    override fun getFixedDisplaysPerPage(): Int = 1

    override fun getDisplayWidth(display: TypeChartDisplay): Int =
        DisplayLayout.getMaxTypeChartSize().width

    override fun setupDisplay(display: TypeChartDisplay, bounds: Rectangle): List<Widget> {
        val widgets = mutableListOf<Widget>()
        val size = DisplayLayout.getMaxTypeChartSize()

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

        val w = size.width
        val h = size.height
        widgets.add(Widgets.createDrawableWidget { gfx, _, _, _ ->
            gfx.pose().pushPose()
            gfx.pose().translate(panelX.toFloat(), panelY.toFloat(), 0f)
            SpawnDisplayHelper.drawTypeChartDetails(gfx, display.data, width = w, height = h)
            gfx.pose().popPose()
        })

        return widgets
    }
}
