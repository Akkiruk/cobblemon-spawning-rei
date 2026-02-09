package com.cobblemonrei.rei

import com.cobblemonrei.CobblemonSpawningMod
import com.cobblemonrei.rei.entry.PokemonEntryDefinition
import com.cobblemonrei.rei.entry.PokemonEntryType
import me.shedaniel.rei.api.common.entry.type.EntryTypeRegistry

object PokemonEntryTypeRegistration {
    @Volatile
    private var registered = false

    fun ensureRegistered() {
        if (registered) return
        synchronized(this) {
            if (registered) return
            try {
                val registry = EntryTypeRegistry.getInstance()
                registry.register(PokemonEntryType.POKEMON.id, PokemonEntryDefinition())
                registered = true
                CobblemonSpawningMod.LOGGER.info("[CobblemonSpawningREI] Registered Pokémon entry type")
            } catch (e: Exception) {
                CobblemonSpawningMod.LOGGER.error("[CobblemonSpawningREI] Failed to register entry type: ${e.javaClass.simpleName}: ${e.message}")
            }
        }
    }

    fun resetForReload() {
        registered = false
    }
}
