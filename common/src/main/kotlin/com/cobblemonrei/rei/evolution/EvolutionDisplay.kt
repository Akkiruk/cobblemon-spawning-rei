package com.cobblemonrei.rei.evolution

import com.cobblemonrei.CobblemonSpawningMod
import com.cobblemonrei.EvolutionInfo
import me.shedaniel.rei.api.common.category.CategoryIdentifier
import me.shedaniel.rei.api.common.display.Display
import me.shedaniel.rei.api.common.entry.EntryIngredient
import me.shedaniel.rei.api.common.util.EntryStacks
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.Items
import java.util.Optional

class EvolutionDisplay(
    val evolution: EvolutionInfo
) : Display {

    override fun getInputEntries(): List<EntryIngredient> {
        // Input = the pre-evolution species (left side, R-click shows this)
        return listOf(EntryIngredient.of(EntryStacks.of(Items.EXPERIENCE_BOTTLE)))
    }

    override fun getOutputEntries(): List<EntryIngredient> {
        // Output = the evolved species (right side, U-click shows this)
        return listOf(EntryIngredient.of(EntryStacks.of(Items.EXPERIENCE_BOTTLE)))
    }

    override fun getCategoryIdentifier(): CategoryIdentifier<*> = EvolutionCategory.ID

    override fun getDisplayLocation(): Optional<ResourceLocation> {
        return Optional.of(
            ResourceLocation.fromNamespaceAndPath(CobblemonSpawningMod.MOD_ID, "evolution/${evolution.id}")
        )
    }
}
