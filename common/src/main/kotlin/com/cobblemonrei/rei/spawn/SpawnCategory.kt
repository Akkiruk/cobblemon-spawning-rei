package com.cobblemonrei.rei.spawn

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

class SpawnCategory : DisplayCategory<SpawnDisplay> {

    companion object {
        val ID: CategoryIdentifier<SpawnDisplay> = CategoryIdentifier.of(
            CobblemonSpawningMod.MOD_ID, "spawns"
        )
    }

    override fun getCategoryIdentifier(): CategoryIdentifier<out SpawnDisplay> = ID

    override fun getTitle(): Component = Component.literal("Spawn Locations")

    override fun getIcon(): Renderer = EntryStacks.of(Items.GRASS_BLOCK)

    override fun getDisplayHeight(): Int = DisplayLayout.getMaxSpawnSize().height

    override fun getFixedDisplaysPerPage(): Int = 1

    override fun getDisplayWidth(display: SpawnDisplay): Int =
        DisplayLayout.measureSpawnPanel(display.speciesName, display.spawn, display.mergedFormVariants, display.bucketIndex, display.bucketTotal).width

    override fun setupDisplay(display: SpawnDisplay, bounds: Rectangle): List<Widget> {
        val widgets = mutableListOf<Widget>()
        val size = DisplayLayout.measureSpawnPanel(display.speciesName, display.spawn, display.mergedFormVariants, display.bucketIndex, display.bucketTotal)
        widgets.add(Widgets.createRecipeBase(Rectangle(bounds.x, bounds.y, size.width, size.height)))

        val pokemonStack = EntryStack.of(PokemonEntryType.POKEMON, PokemonEntry(display.speciesName))
        widgets.add(
            Widgets.createSlot(Rectangle(bounds.x + 8, bounds.y + 3, 20, 20))
                .entries(listOf(pokemonStack))
                .markInput()
                .disableBackground()
                .disableHighlight()
        )

        val ox = bounds.x
        val oy = bounds.y
        val w = size.width
        val h = size.height
        widgets.add(Widgets.createDrawableWidget { gfx, _, _, _ ->
            gfx.pose().pushPose()
            gfx.pose().translate(ox.toFloat(), oy.toFloat(), 0f)
            SpawnDisplayHelper.drawSpawnDetails(
                gfx, display.speciesName, display.spawn, display.mergedFormVariants,
                display.bucketIndex, display.bucketTotal,
                width = w, height = h
            )
            gfx.pose().popPose()
        })

        return widgets
    }
}
