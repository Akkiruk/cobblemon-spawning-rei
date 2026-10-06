package com.cobbledex

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class EpochCacheTest {
    @Test
    fun concurrentCallersShareOneComputation() {
        val cache = EpochCache<Long, String>()
        val computations = AtomicInteger()
        val threads = 8
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val results = (1..threads).map {
            pool.submit<String> {
                start.await()
                cache.get(1L, "moves") { computations.incrementAndGet(); Thread.sleep(50); "built" }
            }
        }
        start.countDown()
        results.forEach { assertEquals("built", it.get(5, TimeUnit.SECONDS)) }
        pool.shutdown()

        assertEquals(1, computations.get())
    }

    @Test
    fun changedEpochDropsEverything() {
        val cache = EpochCache<Long, String>()
        cache.get(1L, "a") { "old" }

        assertNull(cache.peek(2L, "a"))
        assertEquals("new", cache.get(2L, "a") { "new" })
        assertNull(cache.peek(1L, "a"))
    }

    @Test
    fun peekNeverComputes() {
        val cache = EpochCache<Long, String>()

        assertNull(cache.peek(1L, "a"))
        cache.get(1L, "a") { "x" }
        assertEquals("x", cache.peek(1L, "a"))
    }

    @Test
    fun failedComputationIsRetried() {
        val cache = EpochCache<Long, String>()
        assertFailsWith<IllegalStateException> { cache.get(1L, "a") { error("boom") } }

        assertNull(cache.peek(1L, "a"))
        assertEquals("ok", cache.get(1L, "a") { "ok" })
    }

    @Test
    fun reportsComputingOnlyWhileInFlight() {
        val cache = EpochCache<Long, String>()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val pool = Executors.newSingleThreadExecutor()
        val result = pool.submit<String> {
            cache.get(1L, "a") { started.countDown(); release.await(5, TimeUnit.SECONDS); "done" }
        }
        started.await(5, TimeUnit.SECONDS)

        assertEquals(true, cache.isComputing(1L, "a"))
        assertEquals(false, cache.isComputing(1L, "b"))
        assertEquals(false, cache.isComputing(2L, "a"))
        release.countDown()
        assertEquals("done", result.get(5, TimeUnit.SECONDS))
        pool.shutdown()

        assertEquals(false, cache.isComputing(1L, "a"))
    }
}
