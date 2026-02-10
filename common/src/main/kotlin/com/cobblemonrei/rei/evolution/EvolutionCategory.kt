package com.cobblemonrei.rei.evolution

import com.cobblemonrei.CobblemonSpawningMod
import com.cobblemonrei.SpawnDataIndex
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

        private const val SLOT_SIZE = 30
        private const val MAX_NAME_CHARS = 18
    }

    override fun getCategoryIdentifier(): CategoryIdentifier<out EvolutionDisplay> = ID

    override fun getTitle(): Component = Component.literal("Cobblemon Evolution")

    override fun getIcon(): Renderer = EntryStacks.of(Items.EXPERIENCE_BOTTLE)

    override fun getDisplayHeight(): Int = 100

    override fun getFixedDisplaysPerPage(): Int = 2

    override fun setupDisplay(display: EvolutionDisplay, bounds: Rectangle): List<Widget> {
        val widgets = mutableListOf<Widget>()
        widgets.add(Widgets.createRecipeBase(bounds))

        val centerX = bounds.centerX
        val padding = 8
        val availableWidth = bounds.width - padding * 2

        // Data source indicator (top-right, small)
        if (SpawnDataIndex.dataSource == SpawnDataIndex.DataSource.SERVER) {
            widgets.add(
                Widgets.createLabel(Point(bounds.maxX - padding, bounds.y + 3), Component.literal("Server"))
                    .rightAligned().noShadow().color(0xFF44AA44.toInt(), 0xFF66DD66.toInt())
            )
        }

        val slotY = bounds.y + 10
        val nameY = slotY + SLOT_SIZE + 3

        // From species
        val fromSlotX = bounds.x + padding + 12
        val fromStack = EntryStack.of(PokemonEntryType.POKEMON, PokemonEntry(display.evolution.fromSpecies))
        widgets.add(
            Widgets.createSlot(Rectangle(fromSlotX, slotY, SLOT_SIZE, SLOT_SIZE))
                .entries(listOf(fromStack))
                .markInput()
                .disableBackground()
        )
        widgets.add(
            Widgets.createLabel(Point(fromSlotX + SLOT_SIZE / 2, nameY), Component.literal(clip(display.evolution.displayFromName, MAX_NAME_CHARS)))
                .centered().noShadow().color(0xFF333333.toInt(), 0xFFFFFFFF.toInt())
        )

        if (display.branchTotal > 1) {
            widgets.add(
                Widgets.createLabel(
                    Point(fromSlotX + SLOT_SIZE / 2, nameY + 10),
                    Component.literal("${display.branchIndex}/${display.branchTotal}")
                ).centered().color(0xFF888888.toInt(), 0xFFBBBBBB.toInt())
            )
        }

        // Arrow centered on slot row
        val arrowY = slotY + (SLOT_SIZE - 17) / 2
        widgets.add(Widgets.createArrow(Point(centerX - 12, arrowY)))

        // To species
        val toSlotX = bounds.maxX - padding - SLOT_SIZE - 12
        val toStack = EntryStack.of(PokemonEntryType.POKEMON, PokemonEntry(display.evolution.toSpecies))
        widgets.add(
            Widgets.createSlot(Rectangle(toSlotX, slotY, SLOT_SIZE, SLOT_SIZE))
                .entries(listOf(toStack))
                .markOutput()
                .disableBackground()
        )
        widgets.add(
            Widgets.createLabel(Point(toSlotX + SLOT_SIZE / 2, nameY), Component.literal(clip(display.evolution.displayToName, MAX_NAME_CHARS)))
                .centered().noShadow().color(0xFF333333.toInt(), 0xFFFFFFFF.toInt())
        )

        val reqText = display.evolution.displayRequirements
        val maxCharsPerLine = (availableWidth / 5.5).toInt().coerceIn(24, 50)
        val reqLines = wrapReqText(reqText, maxCharsPerLine, 2).map { clip(it, maxCharsPerLine) }
        val totalReqHeight = reqLines.size * 11
        val reqAreaTop = if (display.branchTotal > 1) nameY + 20 else nameY + 12
        val reqAreaBottom = bounds.maxY - 4
        val reqStartY = reqAreaTop + ((reqAreaBottom - reqAreaTop - totalReqHeight) / 2).coerceAtLeast(0)

        for ((i, line) in reqLines.withIndex()) {
            widgets.add(
                Widgets.createLabel(Point(centerX, reqStartY + i * 11), Component.literal(line))
                    .centered().noShadow().color(0xFF444444.toInt(), 0xFFFFDD88.toInt())
            )
        }

        return widgets
    }

    private fun clip(text: String, maxLen: Int): String =
        if (text.length > maxLen) text.take(maxLen - 1) + "\u2026" else text

    private fun wrapReqText(text: String, maxChars: Int, maxLines: Int): List<String> {
        if (text.length <= maxChars) return listOf(text)
        val items = text.split(", ")
        val lines = mutableListOf<String>()
        var current = ""
        for (item in items) {
            val next = if (current.isEmpty()) item else "$current, $item"
            if (next.length > maxChars && current.isNotEmpty()) {
                lines.add(current)
                if (lines.size >= maxLines) {
                    val remaining = items.drop(items.indexOf(item))
                    lines[lines.lastIndex] = clip(lines.last() + ", " + remaining.joinToString(", "), maxChars)
                    return lines
                }
                current = item
            } else {
                current = next
            }
        }
        if (current.isNotEmpty()) {
            if (lines.size >= maxLines) {
                lines[lines.lastIndex] = clip(lines.last() + ", $current", maxChars)
            } else {
                lines.add(current)
            }
        }
        return lines
    }
}
