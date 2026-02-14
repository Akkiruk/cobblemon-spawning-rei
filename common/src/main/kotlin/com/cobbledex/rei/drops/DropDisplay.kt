package com.cobbledex.rei.drops

import com.cobbledex.CobbleDexMod
import com.cobbledex.DropEntryInfo
import com.cobbledex.DropRecipeData
import com.cobbledex.SpawnDisplayHelper
import com.cobbledex.sanitizePath
import com.cobbledex.rei.entry.PokemonEntry
import com.cobbledex.rei.entry.PokemonEntryType
import me.shedaniel.rei.api.common.category.CategoryIdentifier
import me.shedaniel.rei.api.common.display.Display
import me.shedaniel.rei.api.common.entry.EntryIngredient
import me.shedaniel.rei.api.common.entry.EntryStack
import me.shedaniel.rei.api.common.util.EntryStacks
import net.minecraft.resources.ResourceLocation
import java.util.Optional

class DropDisplay(val data: DropRecipeData) : Display {

    val speciesName get() = data.speciesName
    val drops get() = data.drops

    private val cachedInputEntries: List<EntryIngredient> by lazy {
        listOf(EntryIngredient.of(EntryStack.of(PokemonEntryType.POKEMON, PokemonEntry(speciesName))))
    }

    private val cachedOutputEntries: List<EntryIngredient> by lazy {
        drops.mapNotNull { drop ->
            val stack = SpawnDisplayHelper.resolveItemStack(drop.itemId)
            if (!stack.isEmpty) EntryIngredient.of(EntryStacks.of(stack)) else null
        }
    }

    override fun getInputEntries(): List<EntryIngredient> = cachedInputEntries

    override fun getOutputEntries(): List<EntryIngredient> = cachedOutputEntries

    override fun getCategoryIdentifier(): CategoryIdentifier<*> = DropCategory.ID

    override fun getDisplayLocation(): Optional<ResourceLocation> {
        return Optional.of(
            ResourceLocation.fromNamespaceAndPath(
                CobbleDexMod.MOD_ID,
                "drops/${sanitizePath(speciesName)}"
            )
        )
    }
}
