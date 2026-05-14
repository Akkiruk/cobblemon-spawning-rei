package com.cobbledex

import kotlin.test.Test
import kotlin.test.assertSame

class SpawnDataIndexCacheTest {
    @Test
    fun currentQueriesReusesTheSameSnapshotQueryObject() {
        val first = SpawnDataIndex.currentQueries()
        val second = SpawnDataIndex.currentQueries()

        assertSame(first, second)
    }
}
