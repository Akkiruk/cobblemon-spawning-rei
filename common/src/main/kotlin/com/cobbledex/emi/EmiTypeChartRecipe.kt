package com.cobbledex.emi

import com.cobbledex.CobbleDexMod
import com.cobbledex.DisplayLayout
import com.cobbledex.PokemonItemCache
import com.cobbledex.SpawnDisplayHelper
import com.cobbledex.TypeChartRecipeData
import com.cobbledex.sanitizePath
import dev.emi.emi.api.recipe.EmiRecipe
import dev.emi.emi.api.recipe.EmiRecipeCategory
import dev.emi.emi.api.stack.EmiIngredient
import dev.emi.emi.api.stack.EmiStack
import dev.emi.emi.api.widget.WidgetHolder
import net.minecraft.resources.ResourceLocation

class EmiTypeChartRecipe(val data: TypeChartRecipeData) : EmiRecipe {

    companion object {
        private const val PADDING = 6
    }

    private val pokemonStack: EmiStack? by lazy(LazyThreadSafetyMode.NONE) {
        val item = PokemonItemCache.getItem(data.speciesName)
        if (item != null && !item.isEmpty) EmiStack.of(item) else null
    }

    override fun getCategory(): EmiRecipeCategory = CobbleDexEMIPlugin.TYPE_CHART_CATEGORY

    override fun getId(): ResourceLocation = ResourceLocation.fromNamespaceAndPath(
        CobbleDexMod.MOD_ID,
        "emi_type_chart/${sanitizePath(data.speciesName)}"
    )

    override fun getInputs(): List<EmiIngredient> = listOfNotNull(pokemonStack)
    override fun getOutputs(): List<EmiStack> = emptyList()
    override fun supportsRecipeTree(): Boolean = true
    override fun getDisplayWidth(): Int = DisplayLayout.getMaxTypeChartSize().width
    override fun getDisplayHeight(): Int = DisplayLayout.getMaxTypeChartSize().height

    override fun addWidgets(widgets: WidgetHolder) {
        val w = displayWidth
        val h = displayHeight
        pokemonStack?.let { widgets.addSlot(it, PADDING, 2).recipeContext(this) }

        widgets.addDrawable(0, 0, w, h) { graphics, _, _, _ ->
            SpawnDisplayHelper.drawTypeChartDetails(graphics, data, width = w, height = h)
        }
    }
}
