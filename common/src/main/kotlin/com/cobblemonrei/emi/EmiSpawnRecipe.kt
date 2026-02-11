package com.cobblemonrei.emi

import com.cobblemonrei.CobblemonSpawningMod
import com.cobblemonrei.PokemonItemCache
import com.cobblemonrei.SpawnDisplayHelper
import com.cobblemonrei.SpawnInfo
import com.cobblemonrei.sanitizePath
import dev.emi.emi.api.recipe.EmiRecipe
import dev.emi.emi.api.recipe.EmiRecipeCategory
import dev.emi.emi.api.stack.EmiIngredient
import dev.emi.emi.api.stack.EmiStack
import dev.emi.emi.api.widget.WidgetHolder
import net.minecraft.resources.ResourceLocation

class EmiSpawnRecipe(
    val speciesName: String,
    val spawn: SpawnInfo,
    val mergedFormVariants: List<String> = emptyList(),
    val bucketIndex: Int = 1,
    val bucketTotal: Int = 1
) : EmiRecipe {

    companion object {
        private const val WIDTH = 180
        private const val HEIGHT = 200
        private const val PADDING = 6
    }

    private val pokemonStack: EmiStack? by lazy(LazyThreadSafetyMode.NONE) {
        val item = PokemonItemCache.getItem(speciesName)
        if (item != null && !item.isEmpty) EmiStack.of(item) else null
    }

    override fun getCategory(): EmiRecipeCategory = CobblemonEMIPlugin.SPAWN_CATEGORY

    override fun getId(): ResourceLocation = ResourceLocation.fromNamespaceAndPath(
        CobblemonSpawningMod.MOD_ID,
        "emi_spawn/${sanitizePath(speciesName)}/${sanitizePath(spawn.bucket)}/$bucketIndex"
    )

    override fun getInputs(): List<EmiIngredient> = emptyList()
    override fun getOutputs(): List<EmiStack> = listOfNotNull(pokemonStack)
    override fun supportsRecipeTree(): Boolean = false
    override fun getDisplayWidth(): Int = WIDTH
    override fun getDisplayHeight(): Int = HEIGHT

    override fun addWidgets(widgets: WidgetHolder) {
        pokemonStack?.let { widgets.addSlot(it, PADDING, 2).recipeContext(this) }
        widgets.addDrawable(0, 0, WIDTH, HEIGHT) { graphics, _, _, _ ->
            SpawnDisplayHelper.drawSpawnDetails(
                graphics, speciesName, spawn, mergedFormVariants, bucketIndex, bucketTotal
            )
        }
    }

}
