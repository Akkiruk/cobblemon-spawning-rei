package com.cobblemonrei.rei.evolution

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

class EvolutionCategory : DisplayCategory<EvolutionDisplay> {

    companion object {
        val ID: CategoryIdentifier<EvolutionDisplay> = CategoryIdentifier.of(
            CobblemonSpawningMod.MOD_ID, "evolution"
        )
        private const val SLOT_SIZE = 18
    }

    override fun getCategoryIdentifier(): CategoryIdentifier<out EvolutionDisplay> = ID

    override fun getTitle(): Component = Component.literal("Cobblemon Evolution")

    override fun getIcon(): Renderer = EntryStacks.of(Items.EXPERIENCE_BOTTLE)

    override fun getDisplayHeight(): Int = 100

    override fun getFixedDisplaysPerPage(): Int = 2

    override fun setupDisplay(display: EvolutionDisplay, bounds: Rectangle): List<Widget> {
        val widgets = mutableListOf<Widget>()
        widgets.add(Widgets.createRecipeBase(bounds))

        val fromStack = EntryStack.of(PokemonEntryType.POKEMON, PokemonEntry(display.evolution.fromSpecies))
        widgets.add(
            Widgets.createSlot(Rectangle(bounds.x + 20, bounds.y + 10, SLOT_SIZE, SLOT_SIZE))
                .entries(listOf(fromStack))
                .markInput()
                .disableBackground()
        )

        val arrowY = bounds.y + 10 + (SLOT_SIZE - 17) / 2
        widgets.add(Widgets.createArrow(me.shedaniel.math.Point(bounds.centerX - 12, arrowY)))

        val toStack = EntryStack.of(PokemonEntryType.POKEMON, PokemonEntry(display.evolution.toSpecies))
        widgets.add(
            Widgets.createSlot(Rectangle(bounds.maxX - 8 - SLOT_SIZE - 12, bounds.y + 10, SLOT_SIZE, SLOT_SIZE))
                .entries(listOf(toStack))
                .markOutput()
                .disableBackground()
        )

        val ox = bounds.x
        val oy = bounds.y
        widgets.add(Widgets.createDrawableWidget { gfx, _, _, _ ->
            gfx.pose().pushPose()
            gfx.pose().translate(ox.toFloat(), oy.toFloat(), 0f)
            SpawnDisplayHelper.drawEvolutionText(gfx, display.evolution, display.branchIndex, display.branchTotal)
            gfx.pose().popPose()
        })

        return widgets
    }
}
