package com.cobbledex

import com.cobblemon.mod.common.api.fossil.Fossils
import net.minecraft.advancements.critereon.ItemPredicate
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.ItemStack

/**
 * Reads fossil data directly from Cobblemon's runtime Fossils registry.
 * Presets, datapacks, and any server modifications are already resolved.
 */
object FossilDataLoader {

    fun loadFromRuntime(): Map<String, List<FossilCombo>> {
        val fossils = try {
            Fossils.all()
        } catch (e: Exception) {
            DebugLog.warnOnce("fossils-access") { "Failed to read Fossils registry: ${e.message}" }
            return emptyMap()
        }

        if (fossils.isEmpty()) return emptyMap()

        val result = mutableMapOf<String, MutableList<FossilCombo>>()

        for (fossil in fossils) {
            try {
                val props = fossil.result ?: continue
                val rawSpecies = props.species ?: continue
                val speciesName = rawSpecies.lowercase()

                val items = fossil.fossils.flatMap { predicate ->
                    extractItemIds(predicate)
                }
                if (items.isEmpty()) continue

                val extraParts = mutableListOf<String>()
                props.form?.takeIf { it.isNotBlank() }?.let { extraParts.add("form=$it") }
                props.aspects?.takeIf { it.isNotEmpty() }?.let { extraParts.addAll(it) }
                val extraTags = extraParts.joinToString(" ").takeIf { it.isNotBlank() }

                result.getOrPut(speciesName) { mutableListOf() }.add(FossilCombo(speciesName, items, extraTags))
            } catch (e: Exception) {
                DebugLog.once("fossil-${fossil.identifier}") { "Failed to read fossil: ${e.message}" }
            }
        }

        DebugLog.info("Loaded ${result.values.sumOf { it.size }} fossil combos for ${result.size} species from Cobblemon runtime")
        return result
    }

    private fun extractItemIds(predicate: ItemPredicate): List<String> {
        return try {
            val holderSet = predicate.items().orElse(null) ?: return emptyList()
            val ids = mutableListOf<String>()
            holderSet.forEach { holder ->
                val key = holder.unwrapKey().orElse(null)
                if (key != null) ids.add(key.location().toString())
            }
            ids
        } catch (e: Exception) {
            // Fallback: test against registry
            try {
                val ids = mutableListOf<String>()
                for (item in BuiltInRegistries.ITEM) {
                    if (predicate.test(ItemStack(item))) {
                        ids.add(BuiltInRegistries.ITEM.getKey(item).toString())
                        break
                    }
                }
                ids
            } catch (_: Exception) { emptyList() }
        }
    }
}
