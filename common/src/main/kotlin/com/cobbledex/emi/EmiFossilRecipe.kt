package com.cobbledex.emi

import com.cobbledex.CobbleDexMod
import com.cobbledex.DisplayLayout
import com.cobbledex.FossilRecipeData
import com.cobbledex.PokemonItemCache
import com.cobbledex.SpawnDisplayHelper
import com.cobbledex.sanitizePath
import dev.emi.emi.api.recipe.EmiRecipe
import dev.emi.emi.api.recipe.EmiRecipeCategory
import dev.emi.emi.api.stack.EmiIngredient
import dev.emi.emi.api.stack.EmiStack
import dev.emi.emi.api.widget.WidgetHolder
import net.minecraft.resources.ResourceLocation

class EmiFossilRecipe(val data: FossilRecipeData) : EmiRecipe {

    companion object {
        private const val PADDING = 6
    }

    private val pokemonStack: EmiStack? by lazy(LazyThreadSafetyMode.NONE) {
        val item = PokemonItemCache.getItem(data.speciesName)
        if (item != null && !item.isEmpty) EmiStack.of(item) else null
    }

    override fun getCategory(): EmiRecipeCategory = CobbleDexEMIPlugin.FOSSIL_CATEGORY

    override fun getId(): ResourceLocation = ResourceLocation.fromNamespaceAndPath(
        CobbleDexMod.MOD_ID,
        "emi_fossils/${sanitizePath(data.speciesName)}"
    )

    override fun getInputs(): List<EmiIngredient> {
        return data.fossilItems.mapNotNull { itemId ->
            val stack = SpawnDisplayHelper.resolveItemStack(itemId)
            if (!stack.isEmpty) EmiStack.of(stack) else null
        }
    }

    override fun getOutputs(): List<EmiStack> = listOfNotNull(pokemonStack)
    override fun supportsRecipeTree(): Boolean = true
    override fun getDisplayWidth(): Int = DisplayLayout.getMaxFossilSize().width
    override fun getDisplayHeight(): Int = DisplayLayout.getMaxFossilSize().height

    override fun addWidgets(widgets: WidgetHolder) {
        val w = displayWidth
        val h = displayHeight
        pokemonStack?.let { widgets.addSlot(it, PADDING, 2).recipeContext(this) }

        widgets.addDrawable(0, 0, w, h) { graphics, _, _, _ ->
            SpawnDisplayHelper.drawFossilDetails(graphics, data, width = w, height = h)
        }
    }
}
