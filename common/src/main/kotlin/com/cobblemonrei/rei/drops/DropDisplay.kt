package com.cobblemonrei.rei.drops

import com.cobblemonrei.CobblemonSpawningMod
import com.cobblemonrei.DropEntryInfo
import com.cobblemonrei.DropRecipeData
import com.cobblemonrei.SpawnDisplayHelper
import com.cobblemonrei.sanitizePath
import com.cobblemonrei.rei.entry.PokemonEntry
import com.cobblemonrei.rei.entry.PokemonEntryType
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
                CobblemonSpawningMod.MOD_ID,
                "drops/${sanitizePath(speciesName)}"
            )
        )
    }
}
