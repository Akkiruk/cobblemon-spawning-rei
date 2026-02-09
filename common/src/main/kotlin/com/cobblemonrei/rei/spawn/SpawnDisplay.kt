package com.cobblemonrei.rei.spawn

import com.cobblemonrei.CobblemonSpawningMod
import com.cobblemonrei.SpawnInfo
import me.shedaniel.rei.api.common.category.CategoryIdentifier
import me.shedaniel.rei.api.common.display.Display
import me.shedaniel.rei.api.common.entry.EntryIngredient
import me.shedaniel.rei.api.common.util.EntryStacks
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.Items
import java.util.Optional

class SpawnDisplay(
    val speciesName: String,
    val spawns: List<SpawnInfo>
) : Display {

    private val cachedInputEntries: List<EntryIngredient> by lazy {
        listOf(EntryIngredient.of(EntryStacks.of(Items.GRASS_BLOCK)))
    }

    override fun getInputEntries(): List<EntryIngredient> = cachedInputEntries

    override fun getOutputEntries(): List<EntryIngredient> = emptyList()

    override fun getCategoryIdentifier(): CategoryIdentifier<*> = SpawnCategory.ID

    override fun getDisplayLocation(): Optional<ResourceLocation> {
        return Optional.of(
            ResourceLocation.fromNamespaceAndPath(CobblemonSpawningMod.MOD_ID, "spawn/$speciesName")
        )
    }
}
