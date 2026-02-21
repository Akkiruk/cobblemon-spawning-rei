package com.cobbledex

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component

class PanelLayout(val width: Int) {

    data class TooltipZone(val x: Int, val y: Int, val width: Int, val height: Int, val lines: List<Component>)

    private sealed class Element {
        data class Text(val x: Int, val y: Int, val text: String, val color: Int, val shadow: Boolean) : Element()
        data class Fill(val x1: Int, val y1: Int, val x2: Int, val y2: Int, val color: Int) : Element()
    }

    private val elements = mutableListOf<Element>()
    val tooltipZones = mutableListOf<TooltipZone>()
    val font: Font = Minecraft.getInstance().font

    var y: Int = 0
        private set

    val height: Int get() = y
    val right: Int get() = width - PADDING

    // --- Text (places at current Y, does NOT advance cursor) ---

    fun text(x: Int, text: String, color: Int, shadow: Boolean = true): PanelLayout {
        elements.add(Element.Text(x, y, text, color, shadow))
        return this
    }

    fun textAt(x: Int, yPos: Int, text: String, color: Int, shadow: Boolean = true): PanelLayout {
        elements.add(Element.Text(x, yPos, text, color, shadow))
        return this
    }

    fun textRight(text: String, color: Int, shadow: Boolean = true): PanelLayout {
        elements.add(Element.Text(right - font.width(text), y, text, color, shadow))
        return this
    }

    fun textRightAt(yPos: Int, text: String, color: Int, shadow: Boolean = true): PanelLayout {
        elements.add(Element.Text(right - font.width(text), yPos, text, color, shadow))
        return this
    }

    fun textCentered(text: String, color: Int, shadow: Boolean = true): PanelLayout {
        elements.add(Element.Text((width - font.width(text)) / 2, y, text, color, shadow))
        return this
    }

    // --- Clipped text (truncates with ellipsis, does NOT advance cursor) ---

    fun clipped(x: Int, text: String, maxWidth: Int, color: Int, shadow: Boolean = true): PanelLayout {
        elements.add(Element.Text(x, y, SpawnDisplayHelper.clipToWidth(font, text, maxWidth), color, shadow))
        return this
    }

    // --- Wrapped text (adds multiple lines, DOES advance cursor) ---

    fun wrapped(x: Int, text: String, maxWidth: Int, color: Int, lineHeight: Int = LINE_HEIGHT, shadow: Boolean = true): Int {
        val lines = SpawnDisplayHelper.wrapText(font, text, maxWidth)
        for (line in lines) {
            elements.add(Element.Text(x, y, line, color, shadow))
            y += lineHeight
        }
        return lines.size
    }

    fun wrappedCommas(x: Int, text: String, maxWidth: Int, color: Int, lineHeight: Int = LINE_HEIGHT, shadow: Boolean = true): Int {
        val lines = SpawnDisplayHelper.wrapToWidth(font, text, maxWidth)
        for (line in lines) {
            elements.add(Element.Text(x, y, line, color, shadow))
            y += lineHeight
        }
        return lines.size
    }

    data class ItemPlacement(val x: Int, val y: Int, val width: Int, val index: Int)

    fun wrappedItemsWithPositions(
        startX: Int, items: List<String>, separator: String, maxWidth: Int, color: Int,
        lineHeight: Int = LINE_HEIGHT, shadow: Boolean = true
    ): List<ItemPlacement> {
        val placements = mutableListOf<ItemPlacement>()
        var curX = startX
        for ((i, item) in items.withIndex()) {
            val suffix = if (i < items.size - 1) separator else ""
            val itemWidth = font.width(item)
            val fullWidth = font.width(item + suffix)
            if (curX > startX && curX + fullWidth > startX + maxWidth) {
                y += lineHeight
                curX = startX
            }
            placements.add(ItemPlacement(curX, y, itemWidth, i))
            elements.add(Element.Text(curX, y, item + suffix, color, shadow))
            curX += fullWidth
        }
        y += lineHeight
        return placements
    }

    // --- Structural ---

    fun separator(color: Int = 0x50FFFFFF): PanelLayout {
        elements.add(Element.Fill(PADDING, y, right, y + 1, color))
        y += 1
        return this
    }

    fun fill(x1: Int, y1: Int, x2: Int, y2: Int, color: Int): PanelLayout {
        elements.add(Element.Fill(x1, y1, x2, y2, color))
        return this
    }

    fun gap(px: Int): PanelLayout { y += px; return this }
    fun line(): PanelLayout { y += LINE_HEIGHT; return this }
    fun skipTo(newY: Int): PanelLayout { y = newY; return this }

    // --- Tooltip zones ---

    fun addTooltipZone(x: Int, y: Int, width: Int, height: Int, lines: List<Component>) {
        tooltipZones.add(TooltipZone(x, y, width, height, lines))
    }

    fun getTooltipAt(mouseX: Int, mouseY: Int): List<Component>? {
        return tooltipZones.firstOrNull { zone ->
            mouseX >= zone.x && mouseX < zone.x + zone.width &&
            mouseY >= zone.y && mouseY < zone.y + zone.height
        }?.lines
    }

    // --- Rendering ---

    fun render(graphics: GuiGraphics) {
        for (el in elements) when (el) {
            is Element.Text -> graphics.drawString(font, el.text, el.x, el.y, el.color, el.shadow)
            is Element.Fill -> graphics.fill(el.x1, el.y1, el.x2, el.y2, el.color)
        }
    }

    companion object {
        const val PADDING = 6
        const val LINE_HEIGHT = 11
        const val SECTION_GAP = 3
        const val ICON_SIZE = 20
        const val ICON_GAP = 2
        const val TEXT_START_X = PADDING + ICON_SIZE + ICON_GAP
        const val INDENT_X = PADDING + 6
        const val SLOT_SIZE = 18
        const val ITEM_ROW_HEIGHT = 20
        const val MIN_WIDTH = 150
        const val MAX_WIDTH = 300

        fun error(message: String): PanelLayout {
            val layout = PanelLayout(200)
            layout.gap(PADDING)
            layout.text(PADDING, message, 0xFFAA5555.toInt(), shadow = true)
            layout.line()
            layout.gap(PADDING)
            return layout
        }
    }
}
