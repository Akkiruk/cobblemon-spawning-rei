package com.cobbledex.jei

import mezz.jei.api.ingredients.IIngredientHelper
import mezz.jei.api.ingredients.IIngredientType
import mezz.jei.api.ingredients.subtypes.UidContext
import net.minecraft.resources.ResourceLocation
import java.util.Optional

@Suppress("DEPRECATION")
class PokemonIngredientHelper : IIngredientHelper<PokemonIngredient> {

    override fun getIngredientType(): IIngredientType<PokemonIngredient> = PokemonIngredientType

    override fun getDisplayName(ingredient: PokemonIngredient): String = ingredient.displayName

    override fun getUniqueId(ingredient: PokemonIngredient, context: UidContext): String = ingredient.species

    override fun getResourceLocation(ingredient: PokemonIngredient): ResourceLocation = ingredient.identifier

    override fun copyIngredient(ingredient: PokemonIngredient): PokemonIngredient = ingredient.copy()

    override fun getErrorInfo(ingredient: PokemonIngredient?): String {
        return ingredient?.species ?: "null"
    }

    override fun getWildcardId(ingredient: PokemonIngredient): String = ingredient.species

    override fun getDisplayModId(ingredient: PokemonIngredient): String = "cobblemon"

    override fun getTagEquivalent(ingredients: MutableCollection<PokemonIngredient>): Optional<ResourceLocation> = Optional.empty()

    override fun getColors(ingredient: PokemonIngredient): Iterable<Int> = emptyList()

    override fun isValidIngredient(ingredient: PokemonIngredient): Boolean = ingredient.species.isNotBlank()
}
