package com.cobbledex.rei.nature

import com.cobbledex.CobbleDexMod
import com.cobbledex.DisplayLayout
import com.cobbledex.SpawnDisplayHelper
import me.shedaniel.math.Rectangle
import me.shedaniel.rei.api.client.gui.Renderer
import me.shedaniel.rei.api.client.gui.widgets.Widget
import me.shedaniel.rei.api.client.gui.widgets.Widgets
import me.shedaniel.rei.api.client.registry.display.DisplayCategory
import me.shedaniel.rei.api.common.category.CategoryIdentifier
import me.shedaniel.rei.api.common.util.EntryStacks
import net.minecraft.network.chat.Component
import net.minecraft.world.item.Items

class NatureCategory : DisplayCategory<NatureDisplay> {

    companion object {
        val ID: CategoryIdentifier<NatureDisplay> = CategoryIdentifier.of(
            CobbleDexMod.MOD_ID, "natures"
        )
    }

    override fun getCategoryIdentifier(): CategoryIdentifier<out NatureDisplay> = ID

    override fun getTitle(): Component = Component.translatable("category.cobbledex-rei-emi-jei.natures")

    override fun getIcon(): Renderer = EntryStacks.of(Items.WRITABLE_BOOK)

    override fun getDisplayHeight(): Int = DisplayLayout.getMaxNatureSize().height

    override fun getFixedDisplaysPerPage(): Int = 1

    override fun getDisplayWidth(display: NatureDisplay): Int =
        DisplayLayout.getMaxNatureSize().width

    override fun setupDisplay(display: NatureDisplay, bounds: Rectangle): List<Widget> {
        val widgets = mutableListOf<Widget>()
        val size = DisplayLayout.getMaxNatureSize()

        val yOffset = (bounds.height - size.height).coerceAtLeast(0) / 2
        val panelX = bounds.x
        val panelY = bounds.y + yOffset

        widgets.add(Widgets.createRecipeBase(Rectangle(panelX, panelY, size.width, size.height)))

        val w = size.width
        val h = size.height
        widgets.add(Widgets.createDrawableWidget { gfx, _, _, _ ->
            gfx.pose().pushPose()
            gfx.pose().translate(panelX.toFloat(), panelY.toFloat(), 0f)
            SpawnDisplayHelper.drawNatureDetails(gfx, display.data, width = w, height = h)
            gfx.pose().popPose()
        })

        return widgets
    }
}
