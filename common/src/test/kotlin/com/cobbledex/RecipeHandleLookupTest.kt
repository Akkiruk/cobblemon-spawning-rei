package com.cobbledex

import kotlin.test.Test
import kotlin.test.assertEquals

class RecipeHandleLookupTest {
    @Test
    fun inputOnlyPokemonPagesAreDiscoverableAsRecipesAndUsages() {
        val handle = RecipeHandle(
            recipeIdPath = "overview/bulbasaur",
            inputSpecies = listOf("bulbasaur"),
            outputSpecies = emptyList(),
            layoutFactory = { error("layout should not be built for lookup metadata") },
        )

        assertEquals(listOf("bulbasaur"), handle.lookupInputSpecies())
        assertEquals(listOf("bulbasaur"), handle.lookupOutputSpecies())
    }

    @Test
    fun outputOnlyPokemonPagesAreDiscoverableAsRecipesAndUsages() {
        val handle = RecipeHandle(
            recipeIdPath = "spawn/bulbasaur/common/1",
            inputSpecies = emptyList(),
            outputSpecies = listOf("bulbasaur"),
            layoutFactory = { error("layout should not be built for lookup metadata") },
        )

        assertEquals(listOf("bulbasaur"), handle.lookupInputSpecies())
        assertEquals(listOf("bulbasaur"), handle.lookupOutputSpecies())
    }
}