package com.cobblemonrei.rei.obtainment

import com.cobblemonrei.CobblemonSpawningMod
import com.cobblemonrei.SpawnDisplayHelper.clip
import com.cobblemonrei.SpawnDisplayHelper.wrapText
import com.cobblemonrei.titleCase
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

class ObtainmentCategory : DisplayCategory<ObtainmentDisplay> {

    companion object {
        val ID: CategoryIdentifier<ObtainmentDisplay> = CategoryIdentifier.of(
            CobblemonSpawningMod.MOD_ID, "obtainment"
        )

        private const val PADDING = 8
        private const val LINE_HEIGHT = 11
        private const val SECTION_GAP = 4
    }

    override fun getCategoryIdentifier(): CategoryIdentifier<out ObtainmentDisplay> = ID
    override fun getTitle(): Component = Component.literal("Special Obtainment")
    override fun getIcon(): Renderer = EntryStacks.of(Items.NETHER_STAR)
    override fun getDisplayHeight(): Int = 160
    override fun getFixedDisplaysPerPage(): Int = 1

    override fun setupDisplay(display: ObtainmentDisplay, bounds: Rectangle): List<Widget> {
        val widgets = mutableListOf<Widget>()
        widgets.add(Widgets.createRecipeBase(bounds))

        val left = bounds.x + PADDING
        val right = bounds.maxX - PADDING
        val contentWidth = right - left
        val info = display.obtainment

        val pokemonStack = EntryStack.of(PokemonEntryType.POKEMON, PokemonEntry(display.speciesName))
        widgets.add(
            Widgets.createSlot(Rectangle(left, bounds.y + 3, 20, 20))
                .entries(listOf(pokemonStack))
                .markInput()
                .disableBackground()
                .disableHighlight()
        )

        val title = titleCase(display.speciesName)
        widgets.add(
            Widgets.createLabel(Point(left + 24, bounds.y + 9), Component.literal(title))
                .leftAligned().noShadow().color(0xFF333333.toInt(), 0xFFFFFFFF.toInt())
        )

        // Method badge
        widgets.add(
            Widgets.createLabel(Point(right, bounds.y + 9), Component.literal(info.displayMethodName))
                .rightAligned().noShadow().color(0xFFFFAA00.toInt(), 0xFFFFDD66.toInt())
        )

        val sepY = bounds.y + 28
        widgets.add(Widgets.createDrawableWidget { gfx, _, _, _ ->
            gfx.fill(left, sepY, right, sepY + 1, 0x50FFFFFF)
        })

        var y = sepY + SECTION_GAP + 2

        // Description
        val maxChars = ((contentWidth - 4) / 5.5).toInt().coerceIn(20, 80)
        val descLines = wrapText(info.displayDescription, maxChars).take(3)
        for (line in descLines) {
            widgets.add(
                Widgets.createLabel(Point(left + 4, y), Component.literal(line))
                    .leftAligned().noShadow().color(0xFF404040.toInt(), 0xFFEEEEEE.toInt())
            )
            y += LINE_HEIGHT
        }
        y += SECTION_GAP

        // Required items
        if (info.items.isNotEmpty()) {
            widgets.add(
                Widgets.createLabel(Point(left, y), Component.literal("\u2726 Required Items"))
                    .leftAligned().noShadow().color(0xFF333333.toInt(), 0xFFEEEEEE.toInt())
            )
            y += LINE_HEIGHT

            for (item in info.displayItems) {
                if (y + LINE_HEIGHT > bounds.maxY - 18) break
                widgets.add(
                    Widgets.createLabel(Point(left + 8, y), Component.literal("\u2022 $item"))
                        .leftAligned().noShadow().color(0xFF806020.toInt(), 0xFFFFCC66.toInt())
                )
                y += LINE_HEIGHT
            }
            y += SECTION_GAP
        }

        // Block / Structure
        if (info.displayBlock != null || info.displayStructure != null) {
            widgets.add(
                Widgets.createLabel(Point(left, y), Component.literal("\u2605 Location"))
                    .leftAligned().noShadow().color(0xFF333333.toInt(), 0xFFEEEEEE.toInt())
            )
            y += LINE_HEIGHT

            info.displayBlock?.let {
                widgets.add(
                    Widgets.createLabel(Point(left + 8, y), Component.literal("Block: $it"))
                        .leftAligned().noShadow().color(0xFF404040.toInt(), 0xFFDDDDDD.toInt())
                )
                y += LINE_HEIGHT
            }
            info.displayStructure?.let {
                widgets.add(
                    Widgets.createLabel(Point(left + 8, y), Component.literal("Structure: $it"))
                        .leftAligned().noShadow().color(0xFF404040.toInt(), 0xFFDDDDDD.toInt())
                )
                y += LINE_HEIGHT
            }
            info.displayDimension?.let {
                widgets.add(
                    Widgets.createLabel(Point(left + 8, y), Component.literal("Dimension: $it"))
                        .leftAligned().noShadow().color(0xFF404040.toInt(), 0xFFDDDDDD.toInt())
                )
                y += LINE_HEIGHT
            }
            y += SECTION_GAP
        }

        // Notes
        if (info.notes.isNotEmpty()) {
            for (note in info.notes) {
                if (y + LINE_HEIGHT > bounds.maxY - 18) break
                widgets.add(
                    Widgets.createLabel(Point(left + 4, y), Component.literal("\u2139 $note"))
                        .leftAligned().noShadow().color(0xFF666666.toInt(), 0xFFBBBBBB.toInt())
                )
                y += LINE_HEIGHT
            }
        }

        // Footer
        val footerY = bounds.maxY - PADDING - 2
        widgets.add(Widgets.createDrawableWidget { gfx, _, _, _ ->
            gfx.fill(left, footerY - 4, right, footerY - 3, 0x20FFFFFF)
        })

        val footerText = "${display.entryIndex}/${display.entryTotal}"
        widgets.add(
            Widgets.createLabel(Point(left, footerY), Component.literal(footerText))
                .leftAligned().color(0xFFFFAA00.toInt(), 0xFFFFDD66.toInt())
        )

        val sourceText = when (info.source) {
            "bundled" -> "Built-in"
            "datapack" -> "Datapack"
            "mod" -> "Mod"
            else -> ""
        }
        if (sourceText.isNotEmpty()) {
            widgets.add(
                Widgets.createLabel(Point(right, footerY), Component.literal(sourceText))
                    .rightAligned().color(0xFF777777.toInt(), 0xFFBBBBBB.toInt())
            )
        }

        return widgets
    }
}
