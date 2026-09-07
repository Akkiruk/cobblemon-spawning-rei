package com.cobbledex

import com.cobbledex.config.CobbleDexConfig
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack

/**
 * Builds one real `cobblemon:technical_machine` ItemStack per known TM, with the move written into the
 * `cobblemon:tm_move` data component - the same stack Cobblemon's TM Machine produces.
 *
 * Cobblemon's native TMs are a single item with no per-move id and no creative-tab entry, so recipe
 * viewers have nothing to browse. These stacks are what CobbleDex registers on REI/JEI/EMI so every
 * TM is a searchable entry that opens its own recipe, and REI can collapse them into one group.
 *
 * Fully reflective: the component value class only exists on Cobblemon 1.8.0+, so on 1.7.x [all]
 * returns an empty list and the feature is simply absent.
 */
object TmDiscStacks {

    private val TM_ITEM = ResourceLocation.parse(TmItemUtils.NATIVE_TM_ID)
    private val TM_MOVE_COMPONENT = ResourceLocation.parse("cobblemon:tm_move")
    private const val COMPONENT_CLASS = "com.cobblemon.mod.common.item.components.TMMoveComponent"

    @Volatile private var cacheVersion = -1L
    @Volatile private var cache: List<Entry> = emptyList()

    data class Entry(val moveName: String, val stack: ItemStack)

    /** (moveName, disc stack) for every TM in the index. Empty on pre-1.8.0 Cobblemon or when off. */
    fun all(): List<Entry> {
        if (!CobbleDexConfig.get().showTmEntries) return emptyList()
        val version = SpawnDataIndex.dataVersion
        cache.let { if (cacheVersion == version && it.isNotEmpty()) return it }

        val built = try {
            val item = BuiltInRegistries.ITEM.getOptional(TM_ITEM).orElse(null) ?: return emptyList()
            @Suppress("UNCHECKED_CAST")
            val compType = BuiltInRegistries.DATA_COMPONENT_TYPE.get(TM_MOVE_COMPONENT)
                as? DataComponentType<Any> ?: return emptyList()
            val ctor = Class.forName(COMPONENT_CLASS).getConstructor(String::class.java)
            SpawnDataIndex.allTms().mapNotNull { tm ->
                try {
                    val stack = ItemStack(item)
                    stack.set(compType, ctor.newInstance(tm.moveName))
                    Entry(tm.moveName, stack)
                } catch (_: Throwable) { null }
            }
        } catch (_: Throwable) {
            emptyList()
        }

        cache = built
        cacheVersion = version
        if (built.isNotEmpty()) DebugLog.info("Built ${built.size} native TM disc entries")
        return built
    }

    fun forMove(moveName: String): ItemStack? =
        all().firstOrNull { it.moveName.equals(moveName, ignoreCase = true) }?.stack

    /** True for a `technical_machine` stack that carries a move (a real TM, not the blank base disc). */
    fun moveOf(stack: ItemStack): String? {
        if (stack.isEmpty) return null
        if (BuiltInRegistries.ITEM.getKey(stack.item)?.toString() != TmItemUtils.NATIVE_TM_ID) return null
        return TmItemUtils.extractMoveFromStack(stack)
    }

    fun isTmDiscItem(item: net.minecraft.world.item.Item): Boolean =
        BuiltInRegistries.ITEM.getKey(item)?.toString() == TmItemUtils.NATIVE_TM_ID
}
