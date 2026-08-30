package com.cobbledex.jei

import mezz.jei.api.ingredients.IIngredientType

object MoveIngredientType : IIngredientType<MoveIngredient> {
    override fun getIngredientClass(): Class<out MoveIngredient> = MoveIngredient::class.java
}
