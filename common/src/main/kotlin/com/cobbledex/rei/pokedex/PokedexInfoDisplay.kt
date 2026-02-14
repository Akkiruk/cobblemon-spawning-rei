package com.cobbledex.rei.pokedex

import com.cobbledex.CobbleDexMod
import com.cobbledex.PokedexInfoRecipeData
import com.cobbledex.sanitizePath
import com.cobbledex.rei.entry.PokemonEntry
import com.cobbledex.rei.entry.PokemonEntryType
import me.shedaniel.rei.api.common.category.CategoryIdentifier
import me.shedaniel.rei.api.common.display.Display
import me.shedaniel.rei.api.common.entry.EntryIngredient
import me.shedaniel.rei.api.common.entry.EntryStack
import net.minecraft.resources.ResourceLocation
import java.util.Optional

class PokedexInfoDisplay(val data: PokedexInfoRecipeData) : Display {

    val speciesName get() = data.speciesName

    private val cachedInputEntries: List<EntryIngredient> by lazy {
        listOf(EntryIngredient.of(EntryStack.of(PokemonEntryType.POKEMON, PokemonEntry(speciesName))))
    }

    override fun getInputEntries(): List<EntryIngredient> = cachedInputEntries

    override fun getOutputEntries(): List<EntryIngredient> = emptyList()

    override fun getCategoryIdentifier(): CategoryIdentifier<*> = PokedexInfoCategory.ID

    override fun getDisplayLocation(): Optional<ResourceLocation> {
        return Optional.of(
            ResourceLocation.fromNamespaceAndPath(
                CobbleDexMod.MOD_ID,
                "pokedex_info/${sanitizePath(speciesName)}"
            )
        )
    }
}
