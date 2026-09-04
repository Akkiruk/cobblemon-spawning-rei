package com.cobbledex

import kotlin.test.Test
import kotlin.test.assertEquals

class DataSourcePolicyTest {
    @Test
    fun cobblemonWinsOverEveryFallback() {
        val preferred = DataSourcePolicy.preferredSource(listOf("bundled", "datapack", "runtime"))

        assertEquals(DataSourceTier.COBBLEMON, preferred)
    }

    @Test
    fun builtInDefaultsAreBelowLocalFiles() {
        val ordered = DataSourcePolicy.sortByPrecedence(listOf("bundled", "datapack"))

        assertEquals(listOf(DataSourceTier.LOCAL_FILES, DataSourceTier.BUILT_IN), ordered)
    }

    @Test
    fun unknownSourcesSortLast() {
        val ordered = DataSourcePolicy.sortByPrecedence(listOf("mystery", "runtime"))

        assertEquals(listOf(DataSourceTier.COBBLEMON, DataSourceTier.UNAVAILABLE), ordered)
    }
}
