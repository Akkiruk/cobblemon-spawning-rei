package com.cobblemonrei.rei.obtainment

import com.cobblemonrei.CobblemonSpawningMod
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

class ObtainmentCategory : DisplayCategory<ObtainmentDisplay> {

    companion object {
        val ID: CategoryIdentifier<ObtainmentDisplay> = CategoryIdentifier.of(
            CobblemonSpawningMod.MOD_ID, "obtainment"
        )
    }

    override fun getCategoryIdentifier(): CategoryIdentifier<out ObtainmentDisplay> = ID
    override fun getTitle(): Component = Component.literal("Special Obtainment")
    override fun getIcon(): Renderer = EntryStacks.of(Items.NETHER_STAR)
    override fun getDisplayHeight(): Int = 160
    override fun getFixedDisplaysPerPage(): Int = 1

    override fun setupDisplay(display: ObtainmentDisplay, bounds: Rectangle): List<Widget> {
        val widgets = mutableListOf<Widget>()
        widgets.add(Widgets.createRecipeBase(bounds))

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
        widgets.add(Widgets.createDrawableWidget { gfx, _, _, _ ->
            gfx.pose().pushPose()
            gfx.pose().translate(ox.toFloat(), oy.toFloat(), 0f)
            SpawnDisplayHelper.drawObtainmentDetails(
                gfx, display.speciesName, display.obtainment, display.entryIndex, display.entryTotal
            )
            gfx.pose().popPose()
        })

        return widgets
    }
}
