package com.cobbledex

import kotlin.test.Test
import kotlin.test.assertEquals

class MeasuredPagePlannerTest {
    @Test
    fun paginatesRepeatedContentFromMeasuredItemHeights() {
        val pages = MeasuredPagePlanner.paginateMeasured(
            items = listOf("a", "b", "c"),
            maxHeight = 70,
            fixedHeight = 10,
            spacingHeight = 5,
            measureItemHeight = { item, _ -> mapOf("a" to 20, "b" to 30, "c" to 40).getValue(item) },
        )

        assertEquals(listOf(listOf("a", "b"), listOf("c")), pages.map { it.items })
        assertEquals(listOf(65, 50), pages.map { it.height })
    }

    @Test
    fun keepsOversizedItemsReachableAsSingleItemPages() {
        val pages = MeasuredPagePlanner.paginate(
            items = listOf(1, 2),
            maxHeight = 40,
            fixedHeight = 10,
            spacingHeight = 4,
            measureItemHeight = { item, _ -> if (item == 1) 80 else 5 },
        )

        assertEquals(listOf(listOf(1), listOf(2)), pages)
    }

    @Test
    fun repeatsGroupHeaderCostAtEveryPageStartEvenForSameGroupContinuation() {
        // Mirrors RecipeBuilder.buildMovesPages: a same-group item costs 1 normally, but 2 when it
        // lands first on a page (preceding == null) - even if its predecessor, now stranded on the
        // previous page, was in the same group. That's what keeps each page's repeated section
        // header charged, without the planner needing to know what a "group" is.
        fun groupOf(item: String) = item.first()
        val pages = MeasuredPagePlanner.paginate(
            items = listOf("a1", "a2", "b1"),
            maxHeight = 2,
            measureItemHeight = { item, preceding ->
                val startsGroup = preceding == null || groupOf(preceding) != groupOf(item)
                1 + if (startsGroup) 1 else 0
            },
        )

        assertEquals(listOf(listOf("a1"), listOf("a2"), listOf("b1")), pages)
    }
}