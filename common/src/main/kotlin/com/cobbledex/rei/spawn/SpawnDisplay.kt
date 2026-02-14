package com.cobbledex.rei.spawn

import com.cobbledex.CobbleDexMod
import com.cobbledex.SpawnRecipeData
import com.cobbledex.sanitizePath
import com.cobbledex.rei.entry.PokemonEntry
import com.cobbledex.rei.entry.PokemonEntryType
import me.shedaniel.rei.api.common.category.CategoryIdentifier
import me.shedaniel.rei.api.common.display.Display
import me.shedaniel.rei.api.common.entry.EntryIngredient
import me.shedaniel.rei.api.common.entry.EntryStack
import net.minecraft.resources.ResourceLocation
import java.util.Optional

class SpawnDisplay(val data: SpawnRecipeData) : Display {

    val speciesName get() = data.speciesName
    val spawn get() = data.spawn
    val mergedFormVariants get() = data.mergedFormVariants
    val bucketIndex get() = data.bucketIndex
    val bucketTotal get() = data.bucketTotal

    private val cachedOutputEntries: List<EntryIngredient> by lazy {
        listOf(EntryIngredient.of(EntryStack.of(PokemonEntryType.POKEMON, PokemonEntry(speciesName))))
    }

    override fun getInputEntries(): List<EntryIngredient> = emptyList()

    override fun getOutputEntries(): List<EntryIngredient> = cachedOutputEntries

    override fun getCategoryIdentifier(): CategoryIdentifier<*> = SpawnCategory.ID

    override fun getDisplayLocation(): Optional<ResourceLocation> {
        return Optional.of(
            ResourceLocation.fromNamespaceAndPath(
                CobbleDexMod.MOD_ID,
                "spawn/${sanitizePath(speciesName)}/${spawn.id}_$bucketIndex"
            )
        )
    }
}
