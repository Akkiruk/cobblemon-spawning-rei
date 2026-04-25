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
        val resolved = PokemonIconResolver.resolve(name, explicitAspects)
        val cacheKey = resolved.captureIdentity
        itemCache[cacheKey]?.let { return it.copy() }
        val species = resolveSpecies(resolved.captureSpecies) ?: return null
        val item = try {
            if (resolved.captureAspects.isNotEmpty()) PokemonItem.from(species, resolved.captureAspects)
            else PokemonItem.from(species)
        } catch (_: Exception) {
            try { PokemonItem.from(species) } catch (_: Exception) { null }
        }
        if (item != null && !item.isEmpty) itemCache[cacheKey] = item
        return item?.copy()
    }

    fun canRender(name: String, explicitAspects: Set<String> = emptySet()): Boolean {
        val item = getItem(name, explicitAspects)
        return item != null && !item.isEmpty
    }

    fun reset() {
        speciesCache.clear()
        itemCache.clear()
    }
}
