package com.cobbledex.jei

import mezz.jei.api.ingredients.IIngredientType

object PokemonIngredientType : IIngredientType<PokemonIngredient> {
    override fun getIngredientClass(): Class<out PokemonIngredient> = PokemonIngredient::class.java
}
