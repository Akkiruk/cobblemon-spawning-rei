package com.cobbledex

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.crafting.Ingredient

/**
 * Reads Cobblemon's native TM registry (`TechnicalMachines.tmMap`, Cobblemon 1.8.0+) by reflection so
 * the same jar still loads on 1.7.x, where the class does not exist. The registry is client-synced
 * with full recipes, so this is player-truth on servers too; [JarDataCache] provides the fallback for
 * the rare case the sync hasn't landed.
 *
 * [Ingredient] and the item registry are Minecraft classes (always present), so recipe items are read
 * directly once the `TechnicalMachineRecipe` object is in hand.
 */
object NativeTmDataLoader {

    private const val TMS_CLASS = "com.cobblemon.mod.common.api.tms.TechnicalMachines"

    fun loadFromRuntime(): Map<String, TmInfo> {
        return try {
            val cls = Class.forName(TMS_CLASS)
            val instance = cls.getField("INSTANCE").get(null)
            val tmMap = cls.getMethod("getTmMap").invoke(instance) as? Map<*, *> ?: return emptyMap()
            val out = LinkedHashMap<String, TmInfo>()
            for (tm in tmMap.values) {
                val info = tm?.let(::readTm) ?: continue
                out[info.moveName] = info
            }
            if (out.isNotEmpty()) DebugLog.info("Loaded ${out.size} native TMs from Cobblemon runtime")
            out
        } catch (_: Throwable) {
            emptyMap()
        }
    }

    private fun readTm(tm: Any): TmInfo? {
        return try {
            val cls = tm.javaClass
            val moveTemplate = cls.getMethod("getMoveName").invoke(tm) ?: return null
            val moveName = (moveTemplate.javaClass.getMethod("getName").invoke(moveTemplate) as? String)
                ?.lowercase() ?: return null
            val type = (cls.getMethod("getType").invoke(tm) as? String)?.lowercase()?.ifBlank { null }
            val passive = runCatching { cls.getMethod("isPassivelyObtained").invoke(tm) as? Boolean }
                .getOrNull() ?: false
            val recipe = (cls.getMethod("getRecipe").invoke(tm) as? List<*>)
                ?.mapNotNull(::readIngredient) ?: emptyList()
            TmInfo(moveName, type, recipe, passive, "cobblemon")
        } catch (_: Throwable) {
            null
        }
    }

    private fun readIngredient(recipe: Any?): TmIngredient? {
        recipe ?: return null
        return try {
            val cls = recipe.javaClass
            val count = (cls.getMethod("getCount").invoke(recipe) as? Number)?.toInt() ?: 1
            val ingredient = cls.getMethod("getIngredient").invoke(recipe) as? Ingredient ?: return null
            val itemIds = ingredient.items.mapNotNull { stack ->
                BuiltInRegistries.ITEM.getKey(stack.item)?.toString()?.takeUnless { it == "minecraft:air" }
            }.distinct()
            if (itemIds.isEmpty()) null else TmIngredient(itemIds, count)
        } catch (_: Throwable) {
            null
        }
    }
}
