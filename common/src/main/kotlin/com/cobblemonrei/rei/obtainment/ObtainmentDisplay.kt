package com.cobblemonrei.rei.obtainment

import com.cobblemonrei.CobblemonSpawningMod
import com.cobblemonrei.ObtainmentRecipeData
import com.cobblemonrei.sanitizePath
import com.cobblemonrei.rei.entry.PokemonEntry
import com.cobblemonrei.rei.entry.PokemonEntryType
import me.shedaniel.rei.api.common.category.CategoryIdentifier
import me.shedaniel.rei.api.common.display.Display
import me.shedaniel.rei.api.common.entry.EntryIngredient
import me.shedaniel.rei.api.common.entry.EntryStack
import net.minecraft.resources.ResourceLocation
import java.util.Optional

class ObtainmentDisplay(val data: ObtainmentRecipeData) : Display {

    val speciesName get() = data.speciesName
    val obtainment get() = data.obtainment
    val entryIndex get() = data.entryIndex
    val entryTotal get() = data.entryTotal

    private val cachedOutputEntries: List<EntryIngredient> by lazy {
        listOf(EntryIngredient.of(EntryStack.of(PokemonEntryType.POKEMON, PokemonEntry(speciesName))))
    }

    override fun getInputEntries(): List<EntryIngredient> = emptyList()
    override fun getOutputEntries(): List<EntryIngredient> = cachedOutputEntries
    override fun getCategoryIdentifier(): CategoryIdentifier<*> = ObtainmentCategory.ID

    override fun getDisplayLocation(): Optional<ResourceLocation> {
        return Optional.of(
            ResourceLocation.fromNamespaceAndPath(
                CobblemonSpawningMod.MOD_ID,
                "obtainment/${sanitizePath(speciesName)}/${sanitizePath(obtainment.method)}_$entryIndex"
            )
        )
    }
}
