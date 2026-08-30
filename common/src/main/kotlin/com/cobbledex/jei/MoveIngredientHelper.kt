package com.cobbledex.jei

import mezz.jei.api.ingredients.IIngredientHelper
import mezz.jei.api.ingredients.IIngredientType
import mezz.jei.api.ingredients.subtypes.UidContext
import net.minecraft.resources.ResourceLocation
import java.util.Optional

@Suppress("DEPRECATION")
class MoveIngredientHelper : IIngredientHelper<MoveIngredient> {

    override fun getIngredientType(): IIngredientType<MoveIngredient> = MoveIngredientType

    override fun getDisplayName(ingredient: MoveIngredient): String = ingredient.displayName

    override fun getUniqueId(ingredient: MoveIngredient, context: UidContext): String = "move:" + ingredient.normalized

    override fun getResourceLocation(ingredient: MoveIngredient): ResourceLocation =
        ResourceLocation.fromNamespaceAndPath(
            "cobbledex-rei-emi-jei",
            "move/" + ingredient.normalized.replace(Regex("[^a-z0-9._-]"), "")
        )

    override fun copyIngredient(ingredient: MoveIngredient): MoveIngredient = ingredient.copy()

    override fun getErrorInfo(ingredient: MoveIngredient?): String = ingredient?.move ?: "null"

    override fun getWildcardId(ingredient: MoveIngredient): String = "move:" + ingredient.normalized

    override fun getDisplayModId(ingredient: MoveIngredient): String = "cobblemon"

    override fun getTagEquivalent(ingredients: MutableCollection<MoveIngredient>): Optional<ResourceLocation> = Optional.empty()

    override fun getColors(ingredient: MoveIngredient): Iterable<Int> = emptyList()

    override fun isValidIngredient(ingredient: MoveIngredient): Boolean = ingredient.normalized.isNotBlank()
}
