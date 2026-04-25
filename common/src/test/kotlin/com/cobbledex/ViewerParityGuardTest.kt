package com.cobbledex

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ViewerParityGuardTest {
    @Test
    fun acceptsMeasuredSpeciesBackedHandles() {
        val issues = ViewerParityGuard.validateHandles(
            categoryId = "overview",
            handles = listOf(handle("overview/pikachu", inputSpecies = listOf("pikachu"))),
        )

        assertTrue(issues.isEmpty())
    }

    @Test
    fun reportsRecipeIdsLookupKeysAndMeasuredBounds() {
        val issues = ViewerParityGuard.validateHandles(
            categoryId = "test",
            handles = listOf(
                handle("duplicate", inputSpecies = listOf("bulbasaur")),
                handle("duplicate", inputSpecies = listOf("ivysaur")),
                handle("missing_lookup", width = 120, height = PanelLayout.MAX_HEIGHT + 1),
            ),
        )

        assertEquals(
            listOf("duplicate recipe id", "no species lookup keys", "width 120 outside viewer bounds", "height 551 outside viewer bounds"),
            issues.map { it.message },
        )
    }

    @Test
    fun allowsDeclaredGlobalCategories() {
        val issues = ViewerParityGuard.validateHandles(
            categoryId = "natures",
            handles = listOf(handle("natures/table")),
            allowGlobalHandles = true,
        )

        assertTrue(issues.isEmpty())
    }

    private fun handle(
        id: String,
        inputSpecies: List<String> = emptyList(),
        outputSpecies: List<String> = emptyList(),
        width: Int = 200,
        height: Int = 120,
    ) = RecipeHandle(
        recipeIdPath = id,
        inputSpecies = inputSpecies,
        outputSpecies = outputSpecies,
        layoutFactory = { error("test should not build layouts") },
        _width = { width },
        _height = { height },
    )
}