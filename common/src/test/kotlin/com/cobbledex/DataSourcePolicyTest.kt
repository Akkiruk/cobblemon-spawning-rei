package com.cobbledex

import kotlin.test.Test
import kotlin.test.assertEquals

class DataSourcePolicyTest {
    @Test
    fun serverSyncWinsOverEveryFallback() {
        val preferred = DataSourcePolicy.preferredSource(listOf("bundled", "datapack", "runtime", "server_sync"))

        assertEquals(DataSourceTier.SERVER_SYNC, preferred)
    }

    @Test
    fun bundledDefaultsAreBelowDatapacks() {
        val ordered = DataSourcePolicy.sortByPrecedence(listOf("bundled", "datapack"))

        assertEquals(listOf(DataSourceTier.JAR_OR_DATAPACK, DataSourceTier.BUNDLED_DEFAULT), ordered)
    }

    @Test
    fun unknownSourcesSortLast() {
        val ordered = DataSourcePolicy.sortByPrecedence(listOf("mystery", "runtime"))

        assertEquals(listOf(DataSourceTier.RUNTIME, DataSourceTier.UNKNOWN), ordered)
    }
}