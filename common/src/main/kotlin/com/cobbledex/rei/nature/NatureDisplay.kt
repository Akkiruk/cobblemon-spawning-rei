package com.cobbledex.rei.nature

import com.cobbledex.CobbleDexMod
import com.cobbledex.NatureRecipeData
import me.shedaniel.rei.api.common.category.CategoryIdentifier
import me.shedaniel.rei.api.common.display.Display
import me.shedaniel.rei.api.common.entry.EntryIngredient
import net.minecraft.resources.ResourceLocation
import java.util.Optional

class NatureDisplay(val data: NatureRecipeData) : Display {

    override fun getInputEntries(): List<EntryIngredient> = emptyList()

    override fun getOutputEntries(): List<EntryIngredient> = emptyList()

    override fun getCategoryIdentifier(): CategoryIdentifier<*> = NatureCategory.ID

    override fun getDisplayLocation(): Optional<ResourceLocation> {
        return Optional.of(
            ResourceLocation.fromNamespaceAndPath(CobbleDexMod.MOD_ID, "natures/table")
        )
    }
}
