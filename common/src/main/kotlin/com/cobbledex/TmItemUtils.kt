package com.cobbledex

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack

/**
 * Maps TM-style items to the move they teach.
 *
 * Two systems are supported side by side:
 *  - **Native** (Cobblemon 1.8.0+): one item `cobblemon:technical_machine` whose move lives in the
 *    `cobblemon:tm_move` data component. The move can only be read from an [ItemStack], not the id.
 *  - **Third-party** (`tmcraft`, `simpletms`): one item per move, move encoded in the id. Unchanged.
 */
object TmItemUtils {

    const val NATIVE_TM_ID = "cobblemon:technical_machine"
    private val TM_MOVE_COMPONENT = ResourceLocation.parse("cobblemon:tm_move")

    private val movePrefixes = listOf(
        "tmcraft:tm_",
        "tmcraft:tutor_",
        "tmcraft:egg_",
        "tmcraft:star_",
        "simpletms:tm_",
    )

    /** Third-party TMs only — native discs carry no move in the id, use [extractMoveFromStack]. */
    fun extractMove(itemId: String): String? {
        for (prefix in movePrefixes) {
            if (itemId.startsWith(prefix)) return itemId.removePrefix(prefix)
        }
        return null
    }

    fun isNativeTm(itemId: String): Boolean = itemId == NATIVE_TM_ID

    fun isTmItem(itemId: String): Boolean =
        itemId == NATIVE_TM_ID || movePrefixes.any { itemId.startsWith(it) }

    /** Resolves the move for either system from a stack (native reads the data component). */
    fun extractMoveFromStack(stack: ItemStack): String? {
        return try {
            val itemId = BuiltInRegistries.ITEM.getKey(stack.item)?.toString() ?: return null
            extractMove(itemId)?.let { return it.lowercase() }
            if (itemId != NATIVE_TM_ID) return null
            val compType = BuiltInRegistries.DATA_COMPONENT_TYPE.get(TM_MOVE_COMPONENT) ?: return null
            val comp = stack.get(compType) ?: return null
            (comp.javaClass.getMethod("getMoveName").invoke(comp) as? String)?.lowercase()
        } catch (_: Throwable) {
            null
        }
    }

    /** Candidate third-party item ids for a move (used to key disc → move-learner grid lookups). */
    fun tmItemIds(moveName: String): List<String> =
        movePrefixes.distinct().map { prefix -> "$prefix${moveName.lowercase()}" }
}
