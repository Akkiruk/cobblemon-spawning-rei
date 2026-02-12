package com.cobblemonrei

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
        // Check cache with normalized key
        val normalized = SpeciesNameNormalizer.normalize(name)
        speciesCache[normalized]?.let { return it }
        
        // Try multiple name formats to find the species
        val namesToTry = listOf(
            name,
            normalized,
            SpeciesNameNormalizer.toDisplayName(normalized),
            name.lowercase(),
            name.replace(" ", "").replace(".", "").replace("'", "").replace("-", "").replace(":", "").lowercase()
        ).distinct()
        
        for (tryName in namesToTry) {
            val resolved = try { PokemonSpecies.getByName(tryName) } catch (_: Exception) { null }
            if (resolved != null) {
                speciesCache[normalized] = resolved
                return resolved
            }
        }
        return null
    }

    fun getItem(name: String): ItemStack? {
        val normalized = SpeciesNameNormalizer.normalize(name)
        itemCache[normalized]?.let { return it }
        val species = resolveSpecies(name) ?: return null
        val item = try { PokemonItem.from(species) } catch (_: Exception) { null }
        if (item != null && !item.isEmpty) itemCache[normalized] = item
        return item
    }

    fun canRender(name: String): Boolean {
        val item = getItem(name)
        return item != null && !item.isEmpty
    }

    fun reset() {
        speciesCache.clear()
        itemCache.clear()
    }
}
