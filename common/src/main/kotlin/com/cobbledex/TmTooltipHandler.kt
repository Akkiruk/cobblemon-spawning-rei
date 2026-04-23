package com.cobbledex

import net.minecraft.ChatFormatting
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.world.item.ItemStack

/**
 * Appends Cobbleworkers job info to TM-style item tooltips.
 * Builds a reverse move→jobs index from the synced job rules
 * stored in SpawnDataIndex.
 */
object TmTooltipHandler {

    private var moveIndex: Map<String, List<MoveJobEntry>> = emptyMap()
    private var lastDataVersion = -1L

    data class MoveJobEntry(
        val displayName: String,
        val isCombo: Boolean,
        val allRequiredMoves: Set<String>,
    )

    private fun rebuildIfNeeded() {
        val currentVersion = SpawnDataIndex.dataVersion
        if (currentVersion == lastDataVersion) return
        lastDataVersion = currentVersion

        val rules = SpawnDataIndex.jobRules
        if (rules.isEmpty()) {
            moveIndex = emptyMap()
            return
        }

        val map = mutableMapOf<String, MutableList<MoveJobEntry>>()
        for (rule in rules) {
            if (!rule.enabled) continue
            val moves = rule.requiredMoves.map { it.lowercase() }.toSet()
            val isCombo = rule.priority.equals("COMBO", ignoreCase = true)
            for (move in moves) {
                map.getOrPut(move) { mutableListOf() }.add(
                    MoveJobEntry(rule.displayName, isCombo, moves)
                )
            }
        }
        moveIndex = map
    }

    /**
     * Called from platform tooltip events (Fabric ItemTooltipCallback, NeoForge ItemTooltipEvent).
     * Appends job lines to the tooltip if the item is a TMCraft move item.
     */
    fun appendTooltip(stack: ItemStack, lines: MutableList<Component>) {
        val itemId = BuiltInRegistries.ITEM.getKey(stack.item).toString()
        val move = TmItemUtils.extractMove(itemId) ?: return

        rebuildIfNeeded()
        val jobs = moveIndex[move] ?: return

        val (singleJobs, comboJobs) = jobs.partition { !it.isCombo }

        lines.add(Component.empty())
        lines.add(noItalic("\u00a78[\u00a7dCobbleworkers\u00a78]", ChatFormatting.DARK_GRAY))

        for (job in singleJobs) {
            lines.add(noItalic(" \u2726 ${job.displayName}", ChatFormatting.GREEN))
        }

        for (combo in comboJobs) {
            val others = combo.allRequiredMoves
                .filter { it != move }
                .joinToString(" + ") { it.replaceFirstChar { c -> c.uppercase() } }
            lines.add(noItalic(" \u2726 ${combo.displayName}", ChatFormatting.GOLD))
            lines.add(noItalic("   also needs: $others", ChatFormatting.GRAY))
        }
    }

    private fun noItalic(text: String, color: ChatFormatting): Component =
        Component.literal(text).withStyle(Style.EMPTY.withItalic(false).withColor(color))
}
