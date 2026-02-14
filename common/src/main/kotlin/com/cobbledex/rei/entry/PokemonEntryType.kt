package com.cobbledex.rei.entry

import me.shedaniel.rei.api.common.entry.type.EntryType
import net.minecraft.resources.ResourceLocation

object PokemonEntryType {
    val POKEMON: EntryType<PokemonEntry> = EntryType.deferred(
        ResourceLocation.fromNamespaceAndPath("cobbledex-rei-emi-jei", "pokemon")
    )
}
