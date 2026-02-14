package com.cobblemonrei.emi

import com.cobblemonrei.CobblemonSpawningMod
import com.cobblemonrei.DisplayLayout
import com.cobblemonrei.DropRecipeData
import com.cobblemonrei.PokemonItemCache
import com.cobblemonrei.SpawnDisplayHelper
import com.cobblemonrei.sanitizePath
import dev.emi.emi.api.recipe.EmiRecipe
import dev.emi.emi.api.recipe.EmiRecipeCategory
import dev.emi.emi.api.stack.EmiIngredient
import dev.emi.emi.api.stack.EmiStack
import dev.emi.emi.api.widget.WidgetHolder
import net.minecraft.resources.ResourceLocation

class EmiDropRecipe(val data: DropRecipeData) : EmiRecipe {

    companion object {
        private const val PADDING = 6
    }

    private val measuredSize by lazy(LazyThreadSafetyMode.NONE) {
        DisplayLayout.measureDropPanel(data.speciesName, data.drops)
    }

    private val pokemonStack: EmiStack? by lazy(LazyThreadSafetyMode.NONE) {
        val item = PokemonItemCache.getItem(data.speciesName)
        if (item != null && !item.isEmpty) EmiStack.of(item) else null
    }

    override fun getCategory(): EmiRecipeCategory = CobblemonEMIPlugin.DROP_CATEGORY

    override fun getId(): ResourceLocation = ResourceLocation.fromNamespaceAndPath(
        CobblemonSpawningMod.MOD_ID,
        "emi_drops/${sanitizePath(data.speciesName)}"
    )

    override fun getInputs(): List<EmiIngredient> = listOfNotNull(pokemonStack)
    override fun getOutputs(): List<EmiStack> {
        return data.drops.mapNotNull { drop ->
            val stack = SpawnDisplayHelper.resolveItemStack(drop.itemId)
            if (!stack.isEmpty) EmiStack.of(stack) else null
        }
    }

    override fun supportsRecipeTree(): Boolean = true
    override fun getDisplayWidth(): Int = measuredSize.width
    override fun getDisplayHeight(): Int = measuredSize.height

    override fun addWidgets(widgets: WidgetHolder) {
        val w = measuredSize.width
        val h = measuredSize.height
        pokemonStack?.let { widgets.addSlot(it, PADDING, 2).recipeContext(this) }

        var slotY = 34
        for (drop in data.drops) {
            val stack = SpawnDisplayHelper.resolveItemStack(drop.itemId)
            if (!stack.isEmpty) {
                widgets.addSlot(EmiStack.of(stack), 8, slotY).recipeContext(this)
            }
            slotY += 20
        }

        widgets.addDrawable(0, 0, w, h) { graphics, _, _, _ ->
            SpawnDisplayHelper.drawDropDetails(
                graphics, data.speciesName, data.drops,
                width = w, height = h
            )
        }
    }
}
