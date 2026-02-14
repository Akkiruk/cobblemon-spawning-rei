package com.cobbledex.rei.evolution

import com.cobbledex.CobbleDexMod
import com.cobbledex.EvolutionRecipeData
import com.cobbledex.sanitizePath
import com.cobbledex.rei.entry.PokemonEntry
import com.cobbledex.rei.entry.PokemonEntryType
import me.shedaniel.rei.api.common.category.CategoryIdentifier
import me.shedaniel.rei.api.common.display.Display
import me.shedaniel.rei.api.common.entry.EntryIngredient
import me.shedaniel.rei.api.common.entry.EntryStack
import net.minecraft.resources.ResourceLocation
import java.util.Optional

class EvolutionDisplay(val data: EvolutionRecipeData) : Display {

    val evolution get() = data.evolution
    val branchIndex get() = data.branchIndex
    val branchTotal get() = data.branchTotal

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
            ResourceLocation.fromNamespaceAndPath(CobbleDexMod.MOD_ID, "evolution/${sanitizePath(evolution.id)}")
        )
    }
}
