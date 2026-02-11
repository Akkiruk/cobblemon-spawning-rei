package com.cobblemonrei.rei.evolution

import com.cobblemonrei.CobblemonSpawningMod
import com.cobblemonrei.SpawnDisplayHelper
import com.cobblemonrei.rei.entry.PokemonEntry
import com.cobblemonrei.rei.entry.PokemonEntryType
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
            CobblemonSpawningMod.MOD_ID, "evolution"
        )
        private const val SLOT_SIZE = 18
        private const val ITEM_START_Y = 48
        private const val ITEM_ROW_HEIGHT = 20
    }

    override fun getCategoryIdentifier(): CategoryIdentifier<out EvolutionDisplay> = ID

    override fun getTitle(): Component = Component.literal("Cobblemon Evolution")

    override fun getIcon(): Renderer = EntryStacks.of(Items.EXPERIENCE_BOTTLE)

    override fun getDisplayHeight(): Int = 120

    override fun getFixedDisplaysPerPage(): Int = 2

    override fun setupDisplay(display: EvolutionDisplay, bounds: Rectangle): List<Widget> {
        val widgets = mutableListOf<Widget>()
        widgets.add(Widgets.createRecipeBase(bounds))

        val evo = display.evolution

        val fromStack = EntryStack.of(PokemonEntryType.POKEMON, PokemonEntry(evo.fromSpecies))
        widgets.add(
            Widgets.createSlot(Rectangle(bounds.x + 20, bounds.y + 8, SLOT_SIZE, SLOT_SIZE))
                .entries(listOf(fromStack))
                .markInput()
                .disableBackground()
        )

        val arrowY = bounds.y + 8 + (SLOT_SIZE - 17) / 2
        widgets.add(Widgets.createArrow(Point(bounds.centerX - 12, arrowY)))

        val toStack = EntryStack.of(PokemonEntryType.POKEMON, PokemonEntry(evo.toSpecies))
        widgets.add(
            Widgets.createSlot(Rectangle(bounds.maxX - 8 - SLOT_SIZE - 12, bounds.y + 8, SLOT_SIZE, SLOT_SIZE))
                .entries(listOf(toStack))
                .markOutput()
                .disableBackground()
        )

        val items = evo.itemRequirements
        for ((i, item) in items.withIndex()) {
            val stack = SpawnDisplayHelper.resolveItemStack(item.itemId)
            if (!stack.isEmpty) {
                widgets.add(
                    Widgets.createSlot(Rectangle(bounds.x + 8, bounds.y + ITEM_START_Y + i * ITEM_ROW_HEIGHT, SLOT_SIZE, SLOT_SIZE))
                        .entries(listOf(EntryStacks.of(stack)))
                        .markInput()
                )
            }
        }

        val ox = bounds.x
        val oy = bounds.y
        val bw = bounds.width
        val bh = bounds.height
        val hasItems = items.isNotEmpty()
        widgets.add(Widgets.createDrawableWidget { gfx, _, _, _ ->
            gfx.enableScissor(ox, oy, ox + bw, oy + bh)
            gfx.pose().pushPose()
            gfx.pose().translate(ox.toFloat(), oy.toFloat(), 0f)
            SpawnDisplayHelper.drawEvolutionText(gfx, evo, display.branchIndex, display.branchTotal, height = bh, hasItemSlots = hasItems)
            gfx.pose().popPose()
            gfx.disableScissor()
        })

        return widgets
    }
}
