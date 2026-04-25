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
            measureItemHeight = { item -> mapOf("a" to 20, "b" to 30, "c" to 40).getValue(item) },
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
            measureItemHeight = { item -> if (item == 1) 80 else 5 },
        )

        assertEquals(listOf(listOf(1), listOf(2)), pages)
    }
}