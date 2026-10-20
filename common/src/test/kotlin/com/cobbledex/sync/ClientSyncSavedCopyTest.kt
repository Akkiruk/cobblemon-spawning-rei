package com.cobbledex.sync

import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What the client does with a copy of the server's data saved by an earlier session. */
class ClientSyncSavedCopyTest {

    private class FakeClientTransport(var supported: Boolean) : SyncClientTransport {
        val sent = ArrayList<CustomPacketPayload>()
        override fun canSend(type: CustomPacketPayload.Type<*>) = supported
        override fun send(payload: CustomPacketPayload) { sent.add(payload) }
    }

    private class FakeCache : SyncCache {
        val saved = HashMap<String, CachedPayload>()
        val stored = ArrayList<Pair<String, Long>>()
        val deleted = ArrayList<String>()
        override fun load(key: String) = saved[key]
        override fun store(key: String, compressed: ByteArray, hash: Long) {
            stored.add(key to hash)
            saved[key] = CachedPayload(compressed, hash)
        }
        override fun delete(key: String) { deleted.add(key); saved.remove(key) }
    }

    private var now = 0L

    @BeforeTest
    fun setUp() {
        now = 1_000L
        ClientSync.cache = null
        ClientSync.listener = null
        ClientSync.clock = { now }
        ClientSync.modsProvider = { mapOf("cobblemon" to "1.8.2") }
        ClientSync.onLeave()
    }

    @AfterTest
    fun tearDown() {
        ClientSync.cache = null
        ClientSync.listener = null
        ClientSync.onLeave()
        ClientSync.clock = System::currentTimeMillis
    }

    private fun payload(name: String = "A") = PayloadWriter.write(
        listOf(SourceFile("species", "x:species/${name.lowercase()}.json", "file/x", """{"name":"$name"}""")), skip = { false },
    )

    private fun header(p: BuiltPayload) = SyncHeaderPayload(SyncProtocol.STATUS_INCOMING, p.hash, 1, p.compressed.size, p.rawBytes)

    private fun waitFor(what: String, check: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) { if (check()) return; Thread.sleep(10) }
        throw AssertionError("timed out waiting for $what")
    }

    private fun withSavedCopy(key: String = "x", supported: Boolean = true): Triple<FakeCache, FakeClientTransport, BuiltPayload> {
        val cache = FakeCache().also { ClientSync.cache = it }
        val p = payload(); cache.saved[key] = CachedPayload(p.compressed, p.hash)
        val t = FakeClientTransport(supported).also { ClientSync.install(it) }
        return Triple(cache, t, p)
    }

    @Test
    fun aSavedCopyIsOfferedByHashAndUsedStraightAway() {
        val (cache, t, p) = withSavedCopy("play.example.com")
        val seen = ArrayList<ServerOverlay?>()
        ClientSync.listener = { synchronized(seen) { seen.add(it) } }

        ClientSync.onJoin(remoteServer = true, serverAddress = "play.example.com")
        assertEquals(p.hash, (t.sent.single() as SyncRequestPayload).cachedHash)
        waitFor("saved copy") { ClientSync.overlay != null }
        assertTrue(ClientSync.fromSavedCopy)
        assertEquals(1, synchronized(seen) { seen.filterNotNull().size })

        ClientSync.onHeader(SyncHeaderPayload.unchanged(p.hash))
        assertEquals(ClientSync.State.UNCHANGED, ClientSync.state)
        assertNotNull(ClientSync.overlay)
        assertTrue(cache.stored.isEmpty(), "nothing new to save")
    }

    @Test
    fun anUnchangedAnswerForDataWeNeverOfferedIsRejected() {
        FakeClientTransport(true).also { ClientSync.install(it) }
        ClientSync.onJoin(remoteServer = true, serverAddress = "x")
        ClientSync.onHeader(SyncHeaderPayload.unchanged(5L))
        assertEquals(ClientSync.State.FAILED, ClientSync.state)
    }

    @Test
    fun anUnchangedAnswerForADifferentHashIsRejected() {
        val (_, _, p) = withSavedCopy()
        ClientSync.onJoin(remoteServer = true, serverAddress = "x")
        ClientSync.onHeader(SyncHeaderPayload.unchanged(p.hash + 1))
        assertEquals(ClientSync.State.FAILED, ClientSync.state)
    }

    @Test
    fun aFreshTransferReplacesTheSavedCopyAndIsSavedInTurn() {
        val (cache, _, _) = withSavedCopy()
        ClientSync.onJoin(remoteServer = true, serverAddress = "x")
        waitFor("saved copy") { ClientSync.overlay != null }

        val fresh = PayloadWriter.write(
            listOf(
                SourceFile("species", "x:species/b.json", "file/x", """{"name":"B"}"""),
                SourceFile("species", "x:species/c.json", "file/x", """{"name":"C"}"""),
            ),
            skip = { false },
        )
        ClientSync.onHeader(header(fresh))
        ClientSync.onChunk(SyncChunkPayload(0, fresh.compressed))
        waitFor("fresh data") { ClientSync.overlay?.fileCount == 2 }
        assertTrue(!ClientSync.fromSavedCopy)
        waitFor("the new copy to be saved") { cache.stored.isNotEmpty() }
        assertEquals(listOf("x" to fresh.hash), cache.stored)
    }

    @Test
    fun aFailedTransferKeepsTheSavedCopyInUse() {
        val (cache, _, p) = withSavedCopy()
        ClientSync.onJoin(remoteServer = true, serverAddress = "x")
        waitFor("saved copy") { ClientSync.overlay != null }

        ClientSync.onHeader(header(p))
        ClientSync.onChunk(SyncChunkPayload(3, p.compressed))        // wrong index: the transfer fails
        assertEquals(ClientSync.State.FAILED, ClientSync.state)
        assertNotNull(ClientSync.overlay, "the saved copy is still better than nothing")
        assertTrue(cache.deleted.isEmpty())
    }

    @Test
    fun aRefusalKeepsTheSavedCopyToo() {
        withSavedCopy()
        ClientSync.onJoin(remoteServer = true, serverAddress = "x")
        waitFor("saved copy") { ClientSync.overlay != null }
        ClientSync.onHeader(SyncHeaderPayload.refused())
        assertEquals(ClientSync.State.REFUSED, ClientSync.state)
        assertNotNull(ClientSync.overlay)
    }

    @Test
    fun anUnreadableSavedCopyIsDiscardedAndTheServerIsAskedAgainWithoutIt() {
        val cache = FakeCache().also { ClientSync.cache = it }
        cache.saved["x"] = CachedPayload(ByteArray(50) { it.toByte() }, 77L)            // garbage that claims to be valid
        val t = FakeClientTransport(true).also { ClientSync.install(it) }
        ClientSync.onJoin(remoteServer = true, serverAddress = "x")
        assertEquals(77L, (t.sent[0] as SyncRequestPayload).cachedHash)

        waitFor("the bad copy to be discarded") { cache.deleted.isNotEmpty() }
        ClientSync.tick()                                                                // asks again from the game thread
        assertEquals(2, t.sent.size)
        assertEquals(0L, (t.sent[1] as SyncRequestPayload).cachedHash)
        assertNull(ClientSync.overlay)
    }

    @Test
    fun aServerWithoutTheChannelNeverTouchesTheSavedCopy() {
        val (cache, _, _) = withSavedCopy(supported = false)
        ClientSync.onJoin(remoteServer = true, serverAddress = "x")
        assertEquals(ClientSync.State.NO_SERVER_SUPPORT, ClientSync.state)
        assertNull(ClientSync.overlay)
        assertTrue(cache.deleted.isEmpty(), "kept for if the mod comes back")
    }

    @Test
    fun theListenerIsToldWhenTheDataGoesAway() {
        FakeClientTransport(true).also { ClientSync.install(it) }
        val seen = ArrayList<Boolean>()
        ClientSync.listener = { synchronized(seen) { seen.add(it != null) } }
        ClientSync.onJoin(remoteServer = true)
        val p = payload()
        ClientSync.onHeader(header(p)); ClientSync.onChunk(SyncChunkPayload(0, p.compressed))
        waitFor("data") { ClientSync.overlay != null }
        ClientSync.onLeave()
        assertEquals(listOf(true, false), synchronized(seen) { seen.toList() })
    }
}
