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

    @Test
    fun explicitMeasurementsAreCachedPerHandle() {
        var widthCalls = 0
        var heightCalls = 0
        val handle = RecipeHandle(
            recipeIdPath = "forms/eevee_1",
            inputSpecies = listOf("eevee"),
            outputSpecies = emptyList(),
            layoutFactory = { error("layout should not be built for explicit measurement") },
            _width = { widthCalls++; 220 },
            _height = { heightCalls++; 120 },
        )

        assertEquals(220, handle.width)
        assertEquals(220, handle.width)
        assertEquals(120, handle.height)
        assertEquals(120, handle.height)
        assertEquals(1, widthCalls)
        assertEquals(1, heightCalls)
    }

    @Test
    fun slotLookupDoesNotForceLayoutConstruction() {
        val handle = RecipeHandle(
            recipeIdPath = "drops/bulbasaur_1",
            inputSpecies = listOf("bulbasaur"),
            outputSpecies = emptyList(),
            layoutFactory = { error("layout should not be built for slot metadata") },
            _slots = {
                RecipeHandle.Slots(
                    items = listOf(ItemSlotDef("minecraft:apple", 8, 34, SlotRole.OUTPUT)),
                    catalogInputIds = listOf("minecraft:bone_meal"),
                )
            },
        )

        assertEquals(listOf("minecraft:bone_meal"), handle.lookupInputItemIds())
        assertEquals(listOf("minecraft:apple"), handle.lookupOutputItemIds())
    }
}