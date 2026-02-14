package com.cobbledex.emi

import com.cobbledex.CobbleDexMod
import com.cobbledex.DisplayLayout
import com.cobbledex.MovesRecipeData
import com.cobbledex.PokemonItemCache
import com.cobbledex.SpawnDisplayHelper
import com.cobbledex.sanitizePath
import dev.emi.emi.api.recipe.EmiRecipe
import dev.emi.emi.api.recipe.EmiRecipeCategory
import dev.emi.emi.api.stack.EmiIngredient
import dev.emi.emi.api.stack.EmiStack
import dev.emi.emi.api.widget.WidgetHolder
import net.minecraft.resources.ResourceLocation

class EmiMovesRecipe(val data: MovesRecipeData) : EmiRecipe {

    companion object {
        private const val PADDING = 6
    }

    private val pokemonStack: EmiStack? by lazy(LazyThreadSafetyMode.NONE) {
        val item = PokemonItemCache.getItem(data.speciesName)
        if (item != null && !item.isEmpty) EmiStack.of(item) else null
    }

    override fun getCategory(): EmiRecipeCategory = CobbleDexEMIPlugin.MOVES_CATEGORY

    override fun getId(): ResourceLocation = ResourceLocation.fromNamespaceAndPath(
        CobbleDexMod.MOD_ID,
        "emi_moves/${sanitizePath(data.speciesName)}_${data.pageIndex}"
    )

    override fun getInputs(): List<EmiIngredient> = listOfNotNull(pokemonStack)
    override fun getOutputs(): List<EmiStack> = emptyList()
    override fun supportsRecipeTree(): Boolean = true
    override fun getDisplayWidth(): Int = DisplayLayout.getMaxMovesSize().width
    override fun getDisplayHeight(): Int = DisplayLayout.getMaxMovesSize().height

    override fun addWidgets(widgets: WidgetHolder) {
        val w = displayWidth
        val h = displayHeight
        pokemonStack?.let { widgets.addSlot(it, PADDING, 2).recipeContext(this) }

        widgets.addDrawable(0, 0, w, h) { graphics, _, _, _ ->
            SpawnDisplayHelper.drawMovesDetails(
                graphics, data, width = w, height = h
            )
        }
    }
}
