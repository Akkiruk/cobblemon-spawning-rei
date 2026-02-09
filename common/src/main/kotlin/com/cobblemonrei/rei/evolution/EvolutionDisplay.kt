package com.cobblemonrei.rei.evolution

import com.cobblemonrei.CobblemonSpawningMod
import com.cobblemonrei.EvolutionInfo
import com.cobblemonrei.rei.entry.PokemonEntry
import com.cobblemonrei.rei.entry.PokemonEntryType
import me.shedaniel.rei.api.common.category.CategoryIdentifier
import me.shedaniel.rei.api.common.display.Display
import me.shedaniel.rei.api.common.entry.EntryIngredient
import me.shedaniel.rei.api.common.entry.EntryStack
import net.minecraft.resources.ResourceLocation
import java.util.Optional

class EvolutionDisplay(
    val evolution: EvolutionInfo,
    val branchIndex: Int = 0,
    val branchTotal: Int = 0
) : Display {

    override fun getInputEntries(): List<EntryIngredient> {
        return listOf(EntryIngredient.of(
            EntryStack.of(PokemonEntryType.POKEMON, PokemonEntry(evolution.fromSpecies))
        ))
    }

    override fun getOutputEntries(): List<EntryIngredient> {
        return listOf(EntryIngredient.of(
            EntryStack.of(PokemonEntryType.POKEMON, PokemonEntry(evolution.toSpecies))
        ))
    }

    override fun getCategoryIdentifier(): CategoryIdentifier<*> = EvolutionCategory.ID

    override fun getDisplayLocation(): Optional<ResourceLocation> {
        return Optional.of(
            ResourceLocation.fromNamespaceAndPath(CobblemonSpawningMod.MOD_ID, "evolution/${evolution.id}")
        )
    }
}
