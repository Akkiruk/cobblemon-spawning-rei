package com.cobblemonrei.emi

import com.cobblemonrei.CobblemonSpawningMod
import com.cobblemonrei.EvolutionRecipeData
import com.cobblemonrei.PokemonItemCache
import com.cobblemonrei.SpawnDisplayHelper
import com.cobblemonrei.sanitizePath
import dev.emi.emi.api.recipe.EmiRecipe
import dev.emi.emi.api.recipe.EmiRecipeCategory
import dev.emi.emi.api.render.EmiTexture
import dev.emi.emi.api.stack.EmiIngredient
import dev.emi.emi.api.stack.EmiStack
import dev.emi.emi.api.widget.WidgetHolder
import net.minecraft.resources.ResourceLocation

class EmiEvolutionRecipe(val data: EvolutionRecipeData) : EmiRecipe {

    companion object {
        private const val WIDTH = 180
        private const val HEIGHT = 120
        private const val SLOT_SIZE = 18
        private const val ITEM_START_Y = 48
        private const val ITEM_ROW_HEIGHT = 20
    }

    private val fromStack: EmiStack? by lazy(LazyThreadSafetyMode.NONE) {
        val item = PokemonItemCache.getItem(data.evolution.fromSpecies)
        if (item != null && !item.isEmpty) EmiStack.of(item) else null
    }

    private val toStack: EmiStack? by lazy(LazyThreadSafetyMode.NONE) {
        val item = PokemonItemCache.getItem(data.evolution.toSpecies)
        if (item != null && !item.isEmpty) EmiStack.of(item) else null
    }

    override fun getCategory(): EmiRecipeCategory = CobblemonEMIPlugin.EVOLUTION_CATEGORY

    override fun getId(): ResourceLocation {
        val suffix = if (data.evolution.fromAspects.isNotEmpty() || data.evolution.toAspects.isNotEmpty()) {
            "_${(data.evolution.fromAspects + data.evolution.toAspects).hashCode().toUInt()}"
        } else ""
        return ResourceLocation.fromNamespaceAndPath(
            CobblemonSpawningMod.MOD_ID,
            "emi_evolution/${sanitizePath(data.evolution.fromSpecies)}_to_${sanitizePath(data.evolution.toSpecies)}$suffix"
        )
    }

    override fun getInputs(): List<EmiIngredient> = listOfNotNull(fromStack)
    override fun getOutputs(): List<EmiStack> = listOfNotNull(toStack)
    override fun supportsRecipeTree(): Boolean = false
    override fun getDisplayWidth(): Int = WIDTH
    override fun getDisplayHeight(): Int = HEIGHT

    override fun addWidgets(widgets: WidgetHolder) {
        fromStack?.let { widgets.addSlot(it, 20, 8) }
        toStack?.let { widgets.addSlot(it, WIDTH - 20 - SLOT_SIZE, 8).recipeContext(this) }
        widgets.addTexture(EmiTexture.EMPTY_ARROW, WIDTH / 2 - 12, 8)

        val items = data.evolution.itemRequirements
        for ((i, item) in items.withIndex()) {
            val stack = SpawnDisplayHelper.resolveItemStack(item.itemId)
            if (!stack.isEmpty) {
                widgets.addSlot(EmiStack.of(stack), 8, ITEM_START_Y + i * ITEM_ROW_HEIGHT)
            }
        }

        val hasItems = items.isNotEmpty()
        widgets.addDrawable(0, 0, WIDTH, HEIGHT) { graphics, _, _, _ ->
            SpawnDisplayHelper.drawEvolutionText(graphics, data.evolution, data.branchIndex, data.branchTotal, hasItemSlots = hasItems)
        }
    }
}
