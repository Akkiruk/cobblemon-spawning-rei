package com.cobbledex

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.item.PokemonItem
import com.cobblemon.mod.common.pokemon.Species
import net.minecraft.world.item.ItemStack
import java.util.concurrent.ConcurrentHashMap

/**
 * Shared cache for PokemonSpecies resolution and PokemonItem creation.
 * All recipe viewer integrations (REI/JEI/EMI) share this single cache
 * instead of maintaining separate per-renderer ConcurrentHashMaps.
 */
object PokemonItemCache {

    private val speciesCache = ConcurrentHashMap<String, Species>()
    private val itemCache = ConcurrentHashMap<String, ItemStack>()
    private val blockedRenderKeys = ConcurrentHashMap.newKeySet<String>()

    private fun resolveAspects(name: String, explicitAspects: Set<String>): Set<String> {
        if (explicitAspects.isNotEmpty()) return explicitAspects
        val normalized = SpeciesNameNormalizer.normalize(name)
        val decomp = SpeciesNameNormalizer.decomposeFormSpecies(normalized)
        return decomp.cobblemonAspects.ifEmpty {
            SpawnDataIndex.getSpeciesInfo(name)?.formAspects ?: emptySet()
        }
    }

    private fun cacheKey(name: String, explicitAspects: Set<String> = emptySet()): String {
        val normalized = SpeciesNameNormalizer.normalize(name)
        val aspects = resolveAspects(name, explicitAspects)
        return if (aspects.isEmpty()) normalized else "$normalized|${aspects.sorted().joinToString(",")}"
    }

    fun resolveSpecies(name: String): Species? {
        val normalized = SpeciesNameNormalizer.normalize(name)
        speciesCache[normalized]?.let { return it }
        
        val namesToTry = mutableListOf(
            name,
            normalized,
            SpeciesNameNormalizer.toDisplayName(normalized),
            name.lowercase(),
            name.replace(" ", "").replace(".", "").replace("'", "").replace("-", "").replace(":", "").lowercase()
        )

        val decomp = SpeciesNameNormalizer.decomposeFormSpecies(normalized)
        if (decomp.regionKey != null) {
            namesToTry.add(decomp.baseName)
            namesToTry.add(SpeciesNameNormalizer.toDisplayName(decomp.baseName))
        }
        
        for (tryName in namesToTry.distinct()) {
            val resolved = try { PokemonSpecies.getByName(tryName) } catch (_: Exception) { null }
            if (resolved != null) {
                speciesCache[normalized] = resolved
                return resolved
            }
        }

        // Fallback for form keys: resolve via baseSpeciesName (O2)
        val formInfo = SpawnDataIndex.getSpeciesInfo(normalized)
        if (formInfo?.baseSpeciesName != null) {
            val baseSpecies = try { PokemonSpecies.getByName(formInfo.baseSpeciesName) } catch (_: Exception) { null }
            if (baseSpecies != null) {
                speciesCache[normalized] = baseSpecies
                return baseSpecies
            }
        }

        return null
    }

    fun getItem(name: String, explicitAspects: Set<String> = emptySet()): ItemStack? {
        val aspects = resolveAspects(name, explicitAspects)
        val cacheKey = cacheKey(name, explicitAspects)
        if (blockedRenderKeys.contains(cacheKey)) return null
        itemCache[cacheKey]?.let { return it.copy() }
        val species = resolveSpecies(name) ?: return null
        val item = try {
            if (aspects.isNotEmpty()) PokemonItem.from(species, aspects)
            else PokemonItem.from(species)
        } catch (_: Exception) {
            try { PokemonItem.from(species) } catch (_: Exception) { null }
        }
        if (item != null && !item.isEmpty) itemCache[cacheKey] = item
        return item?.copy()
    }

    fun canRender(name: String, explicitAspects: Set<String> = emptySet()): Boolean {
        if (blockedRenderKeys.contains(cacheKey(name, explicitAspects))) return false
        val item = getItem(name, explicitAspects)
        return item != null && !item.isEmpty
    }

    fun markRenderFailed(name: String, explicitAspects: Set<String> = emptySet(), error: Throwable? = null) {
        val cacheKey = cacheKey(name, explicitAspects)
        if (!blockedRenderKeys.add(cacheKey)) return
        DebugLog.trackMissingModel(name)
        DebugLog.warnOnce("pokemon-render-fail-$cacheKey") {
            val detail = error?.message?.takeIf { it.isNotBlank() }
                ?: error?.javaClass?.simpleName
                ?: "unknown render error"
            "Hiding $name from recipe viewers after render failure: $detail"
        }
    }

    fun reset() {
        speciesCache.clear()
        itemCache.clear()
        blockedRenderKeys.clear()
    }
}
