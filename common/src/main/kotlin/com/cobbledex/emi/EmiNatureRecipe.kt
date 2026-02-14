package com.cobbledex.emi

import com.cobbledex.CobbleDexMod
import com.cobbledex.DisplayLayout
import com.cobbledex.NatureRecipeData
import com.cobbledex.SpawnDisplayHelper
import dev.emi.emi.api.recipe.EmiRecipe
import dev.emi.emi.api.recipe.EmiRecipeCategory
import dev.emi.emi.api.stack.EmiIngredient
import dev.emi.emi.api.stack.EmiStack
import dev.emi.emi.api.widget.WidgetHolder
import net.minecraft.resources.ResourceLocation

class EmiNatureRecipe(val data: NatureRecipeData) : EmiRecipe {

    override fun getCategory(): EmiRecipeCategory = CobbleDexEMIPlugin.NATURE_CATEGORY

    override fun getId(): ResourceLocation = ResourceLocation.fromNamespaceAndPath(
        CobbleDexMod.MOD_ID,
        "emi_natures/table"
    )

    override fun getInputs(): List<EmiIngredient> = emptyList()
    override fun getOutputs(): List<EmiStack> = emptyList()
    override fun supportsRecipeTree(): Boolean = false
    override fun getDisplayWidth(): Int = DisplayLayout.getMaxNatureSize().width
    override fun getDisplayHeight(): Int = DisplayLayout.getMaxNatureSize().height

    override fun addWidgets(widgets: WidgetHolder) {
        val w = displayWidth
        val h = displayHeight
        widgets.addDrawable(0, 0, w, h) { graphics, _, _, _ ->
            SpawnDisplayHelper.drawNatureDetails(graphics, data, width = w, height = h)
        }
    }
}
