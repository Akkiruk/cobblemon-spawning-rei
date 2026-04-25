package com.cobbledex.jei

import com.cobbledex.DebugLog
import mezz.jei.api.runtime.IIngredientManager

object JeiRuntimeAliasBridge {

    fun replacePokemonAliases(
        ingredientManager: IIngredientManager,
        ingredients: Collection<PokemonIngredient>,
        aliasProvider: (PokemonIngredient) -> Collection<String>
    ) {
        try {
            val registeredIngredientsField = ingredientManager.javaClass.getDeclaredField("registeredIngredients")
            registeredIngredientsField.isAccessible = true
            val registeredIngredients = registeredIngredientsField.get(ingredientManager)

            val getIngredientInfo = registeredIngredients.javaClass.getMethod(
                "getIngredientInfo",
                mezz.jei.api.ingredients.IIngredientType::class.java
            )
            val ingredientInfo = getIngredientInfo.invoke(registeredIngredients, PokemonIngredientType)

            val aliasesField = ingredientInfo.javaClass.getDeclaredField("aliases")
            aliasesField.isAccessible = true
            val aliases = aliasesField.get(ingredientInfo)
            aliases.javaClass.getMethod("clear").invoke(aliases)

            val addAliases = ingredientInfo.javaClass.getMethod("addIngredientAliases", Any::class.java, Collection::class.java)
            for (ingredient in ingredients) {
                val aliasTerms = aliasProvider(ingredient)
                    .map(String::trim)
                    .filter(String::isNotBlank)
                    .distinct()
                if (aliasTerms.isNotEmpty()) {
                    addAliases.invoke(ingredientInfo, ingredient, aliasTerms)
                }
            }
        } catch (e: Exception) {
            DebugLog.once("jei-runtime-alias-bridge") {
                "JEI runtime alias bridge unavailable: ${e.message}"
            }
        }
    }
}