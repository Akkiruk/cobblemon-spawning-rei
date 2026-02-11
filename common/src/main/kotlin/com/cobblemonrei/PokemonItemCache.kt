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
        speciesCache[name]?.let { return it }
        val resolved = try { PokemonSpecies.getByName(name) } catch (_: Exception) { null }
        if (resolved != null) speciesCache[name] = resolved
        return resolved
    }

    fun getItem(name: String): ItemStack? {
        itemCache[name]?.let { return it }
        val species = resolveSpecies(name) ?: return null
        val item = try { PokemonItem.from(species) } catch (_: Exception) { null }
        if (item != null && !item.isEmpty) itemCache[name] = item
        return item
    }

    fun canRender(name: String): Boolean {
        val item = getItem(name)
        return item != null && !item.isEmpty
    }
}
