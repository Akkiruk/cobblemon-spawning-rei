package com.cobbledex.sync

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiskSyncCacheTest {

    private fun bytes(seed: Int, n: Int = 500) = ByteArray(n) { (it * seed).toByte() }

    private fun cache(max: Int = 8) = DiskSyncCache(Files.createTempDirectory("cdx-cache"), max)

    @Test
    fun aStoredCopyComesBackIntact() {
        val c = cache(); val data = bytes(3)
        c.store("play.example.com:25565", data, SyncHash.of(data))
        val back = assertNotNull(c.load("play.example.com:25565"))
        assertContentEquals(data, back.compressed)
        assertEquals(SyncHash.of(data), back.hash)
    }

    @Test
    fun serversAreKeptApartAndTheKeyIgnoresCaseAndSpaces() {
        val c = cache(); val a = bytes(3); val b = bytes(5)
        c.store("Play.Example.com", a, SyncHash.of(a)); c.store("other.host", b, SyncHash.of(b))
        assertContentEquals(a, c.load(" play.example.com ")!!.compressed)
        assertContentEquals(b, c.load("other.host")!!.compressed)
        assertNull(c.load("never.seen"))
    }

    @Test
    fun aCorruptedFileIsDiscardedNotTrusted() {
        val dir = Files.createTempDirectory("cdx-cache"); val c = DiskSyncCache(dir)
        val data = bytes(7); c.store("x", data, SyncHash.of(data))
        val file = Files.list(dir).use { it.toList().single() }
        val raw = Files.readAllBytes(file); raw[raw.size - 5] = (raw[raw.size - 5] + 1).toByte()
        Files.write(file, raw)
        assertNull(c.load("x"))
        assertTrue(!Files.exists(file), "a bad file is removed so it is not tried again")
    }

    @Test
    fun aTruncatedOrForeignFileIsDiscarded() {
        val dir = Files.createTempDirectory("cdx-cache"); val c = DiskSyncCache(dir)
        val data = bytes(2); c.store("x", data, SyncHash.of(data))
        val file = Files.list(dir).use { it.toList().single() }
        Files.write(file, Files.readAllBytes(file).copyOf(30))
        assertNull(c.load("x"))
        c.store("x", data, SyncHash.of(data))
        Files.write(file, "not a cache file at all".toByteArray())
        assertNull(c.load("x"))
        c.store("x", data, SyncHash.of(data))
        Files.write(file, ByteArray(0))
        assertNull(c.load("x"))
    }

    @Test
    fun onlyTheMostRecentServersAreKept() {
        val dir = Files.createTempDirectory("cdx-cache"); val c = DiskSyncCache(dir, maxEntries = 3)
        for (i in 1..6) {
            val d = bytes(i); c.store("server$i", d, SyncHash.of(d))
            Thread.sleep(15)
        }
        val left = Files.list(dir).use { it.count() }
        assertEquals(3, left)
        assertNotNull(c.load("server6"))
        assertNull(c.load("server1"))
    }

    @Test
    fun deletingAMissingEntryIsHarmlessAndLoadingFromAMissingFolderGivesNull() {
        val c = DiskSyncCache(Files.createTempDirectory("cdx-cache").resolve("does-not-exist"))
        c.delete("x")
        assertNull(c.load("x"))
        val d = bytes(1); c.store("x", d, SyncHash.of(d))
        assertNotNull(c.load("x"))
    }

    @Test
    fun noTemporaryFilesAreLeftBehind() {
        val dir = Files.createTempDirectory("cdx-cache"); val c = DiskSyncCache(dir)
        repeat(5) { val d = bytes(it + 1); c.store("s", d, SyncHash.of(d)) }
        assertTrue(Files.list(dir).use { s -> s.allMatch { it.fileName.toString().endsWith(".bin") } })
    }
}
