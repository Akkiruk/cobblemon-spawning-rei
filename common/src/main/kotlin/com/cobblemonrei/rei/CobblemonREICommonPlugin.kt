package com.cobblemonrei.rei

import com.cobblemonrei.rei.entry.PokemonEntryDefinition
import com.cobblemonrei.rei.entry.PokemonEntryType
import me.shedaniel.rei.api.common.entry.type.EntryTypeRegistry

object PokemonEntryTypeRegistration {
    private var registered = false

    fun ensureRegistered() {
        if (registered) return
        registered = true
        EntryTypeRegistry.getInstance().register(PokemonEntryType.POKEMON, PokemonEntryDefinition())
    }
}
