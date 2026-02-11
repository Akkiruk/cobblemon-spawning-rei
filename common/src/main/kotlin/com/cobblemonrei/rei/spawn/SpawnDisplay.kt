package com.cobblemonrei.rei.spawn

import com.cobblemonrei.CobblemonSpawningMod
import com.cobblemonrei.SpawnInfo
import com.cobblemonrei.sanitizePath
import com.cobblemonrei.rei.entry.PokemonEntry
import com.cobblemonrei.rei.entry.PokemonEntryType
import me.shedaniel.rei.api.common.category.CategoryIdentifier
import me.shedaniel.rei.api.common.display.Display
import me.shedaniel.rei.api.common.entry.EntryIngredient
import me.shedaniel.rei.api.common.entry.EntryStack
import net.minecraft.resources.ResourceLocation
import java.util.Optional

class SpawnDisplay(
    val speciesName: String,
    val spawn: SpawnInfo,
    val mergedFormVariants: List<String> = emptyList(),
    val bucketIndex: Int = 1,
    val bucketTotal: Int = 1
) : Display {

    private val cachedOutputEntries: List<EntryIngredient> by lazy {
        listOf(EntryIngredient.of(EntryStack.of(PokemonEntryType.POKEMON, PokemonEntry(speciesName))))
    }

    override fun getInputEntries(): List<EntryIngredient> = emptyList()

    override fun getOutputEntries(): List<EntryIngredient> = cachedOutputEntries

    override fun getCategoryIdentifier(): CategoryIdentifier<*> = SpawnCategory.ID

    override fun getDisplayLocation(): Optional<ResourceLocation> {
        return Optional.of(
            ResourceLocation.fromNamespaceAndPath(
                CobblemonSpawningMod.MOD_ID,
                "spawn/${sanitizePath(speciesName)}/${spawn.id}_$bucketIndex"
            )
        )
    }
}
