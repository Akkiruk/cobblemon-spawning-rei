package com.cobbledex.emi

import com.cobbledex.CobbleDexMod
import com.cobbledex.DisplayLayout
import com.cobbledex.PokemonItemCache
import com.cobbledex.SpawnDisplayHelper
import com.cobbledex.SpawnRecipeData
import com.cobbledex.sanitizePath
import dev.emi.emi.api.recipe.EmiRecipe
import dev.emi.emi.api.recipe.EmiRecipeCategory
import dev.emi.emi.api.stack.EmiIngredient
import dev.emi.emi.api.stack.EmiStack
import dev.emi.emi.api.widget.WidgetHolder
import net.minecraft.resources.ResourceLocation

class EmiSpawnRecipe(val data: SpawnRecipeData) : EmiRecipe {

    companion object {
        private const val PADDING = 6
    }

    private val measuredSize by lazy(LazyThreadSafetyMode.NONE) {
        DisplayLayout.measureSpawnPanel(data.speciesName, data.spawn, data.mergedFormVariants, data.bucketIndex, data.bucketTotal)
    }

    private val pokemonStack: EmiStack? by lazy(LazyThreadSafetyMode.NONE) {
        val item = PokemonItemCache.getItem(data.speciesName)
        if (item != null && !item.isEmpty) EmiStack.of(item) else null
    }

    override fun getCategory(): EmiRecipeCategory = CobbleDexEMIPlugin.SPAWN_CATEGORY

    override fun getId(): ResourceLocation = ResourceLocation.fromNamespaceAndPath(
        CobbleDexMod.MOD_ID,
        "emi_spawn/${sanitizePath(data.speciesName)}/${sanitizePath(data.spawn.bucket)}/${data.bucketIndex}"
    )

    override fun getInputs(): List<EmiIngredient> = emptyList()
    override fun getOutputs(): List<EmiStack> = listOfNotNull(pokemonStack)
    override fun supportsRecipeTree(): Boolean = false
    override fun getDisplayWidth(): Int = measuredSize.width
    override fun getDisplayHeight(): Int = measuredSize.height

    override fun addWidgets(widgets: WidgetHolder) {
        val w = measuredSize.width
        val h = measuredSize.height
        pokemonStack?.let { widgets.addSlot(it, PADDING, 2).recipeContext(this) }
        widgets.addDrawable(0, 0, w, h) { graphics, _, _, _ ->
            SpawnDisplayHelper.drawSpawnDetails(
                graphics, data.speciesName, data.spawn, data.mergedFormVariants, data.bucketIndex, data.bucketTotal,
                width = w, height = h
            )
        }
    }
}
