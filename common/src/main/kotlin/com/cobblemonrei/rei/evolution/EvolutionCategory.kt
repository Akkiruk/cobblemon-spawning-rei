package com.cobblemonrei.rei.evolution

import com.cobblemonrei.CobblemonSpawningMod
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
    }

    override fun getCategoryIdentifier(): CategoryIdentifier<out EvolutionDisplay> = ID

    override fun getTitle(): Component = Component.literal("Cobblemon Evolution")

    override fun getIcon(): Renderer = EntryStacks.of(Items.EXPERIENCE_BOTTLE)

    override fun getDisplayHeight(): Int = 80

    override fun getFixedDisplaysPerPage(): Int = 3

    override fun setupDisplay(display: EvolutionDisplay, bounds: Rectangle): List<Widget> {
        val widgets = mutableListOf<Widget>()
        widgets.add(Widgets.createRecipeBase(bounds))

        val centerY = bounds.centerY
        val startX = bounds.x + 10
        val slotSize = 28

        // From species slot (left side)
        val fromStack = EntryStack.of(PokemonEntryType.POKEMON, PokemonEntry(display.evolution.fromSpecies))
        widgets.add(
            Widgets.createSlot(Rectangle(startX, centerY - slotSize / 2 - 4, slotSize, slotSize))
                .entries(listOf(fromStack))
                .markInput()
                .disableBackground()
                .disableHighlight()
        )

        // From species name
        val fromName = display.evolution.fromSpecies.replaceFirstChar { it.uppercase() }
        widgets.add(
            Widgets.createLabel(Point(startX + slotSize + 2, centerY - 12), Component.literal(fromName))
                .leftAligned().noShadow().color(0xFF333333.toInt(), 0xFFDDDDDD.toInt())
        )

        // Arrow
        widgets.add(Widgets.createArrow(Point(bounds.centerX - 12, centerY - 9)))

        // To species slot (right side)
        val toStack = EntryStack.of(PokemonEntryType.POKEMON, PokemonEntry(display.evolution.toSpecies))
        val toSlotX = bounds.centerX + 16
        widgets.add(
            Widgets.createSlot(Rectangle(toSlotX, centerY - slotSize / 2 - 4, slotSize, slotSize))
                .entries(listOf(toStack))
                .markOutput()
                .disableBackground()
                .disableHighlight()
        )

        // To species name
        val toName = display.evolution.toSpecies.replaceFirstChar { it.uppercase() }
        widgets.add(
            Widgets.createLabel(Point(toSlotX + slotSize + 2, centerY - 12), Component.literal(toName))
                .leftAligned().noShadow().color(0xFF333333.toInt(), 0xFFDDDDDD.toInt())
        )

        // Requirements text
        val reqText = display.evolution.displayRequirements
        widgets.add(
            Widgets.createLabel(Point(bounds.centerX, centerY + 10), Component.literal(reqText))
                .centered().noShadow().color(0xFF777777.toInt(), 0xFF999999.toInt())
        )

        // Variant indicator
        val variantText = when (display.evolution.variant) {
            "level_up", "passive" -> ""
            "trade" -> "[Trade]"
            "item_interact" -> "[Item]"
            "block_click" -> "[Block]"
            else -> "[${display.evolution.variant}]"
        }
        if (variantText.isNotEmpty()) {
            widgets.add(
                Widgets.createLabel(Point(bounds.maxX - 8, bounds.y + 6), Component.literal(variantText))
                    .rightAligned().noShadow().color(0xFF999999.toInt(), 0xFF777777.toInt())
            )
        }

        return widgets
    }
}
