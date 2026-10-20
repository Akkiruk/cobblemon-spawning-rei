package com.cobbledex.sync

import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClientSyncTest {

    private class FakeClientTransport(var supported: Boolean) : SyncClientTransport {
        val sent = ArrayList<CustomPacketPayload>()
        override fun canSend(type: CustomPacketPayload.Type<*>) = supported
        override fun send(payload: CustomPacketPayload) { sent.add(payload) }
    }

    private var now = 0L

    @BeforeTest
    fun setUp() {
        now = 1_000L
        ClientSync.cache = null
        ClientSync.listener = null
        ClientSync.clock = { now }
        ClientSync.modsProvider = { mapOf("cobblemon" to "1.8.2", "fabricloader" to "0.19.5") }
        ClientSync.onLeave()
    }

    @AfterTest
    fun tearDown() {
        ClientSync.onLeave()
        ClientSync.clock = System::currentTimeMillis
    }

    private fun payload(): BuiltPayload = PayloadWriter.write(
        listOf(SourceFile("species", "x:species/a.json", "file/x", """{"name":"A"}""")), skip = { false },
    )

    private fun header(p: BuiltPayload) = SyncHeaderPayload(SyncProtocol.STATUS_INCOMING, p.hash, 1, p.compressed.size, p.rawBytes)

    private fun waitFor(what: String, check: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) { if (check()) return; Thread.sleep(10) }
        throw AssertionError("timed out waiting for $what")
    }

    @Test
    fun aLocalWorldNeverAsksAnyone() {
        val t = FakeClientTransport(true).also { ClientSync.install(it) }
        ClientSync.onJoin(remoteServer = false)
        assertEquals(ClientSync.State.IDLE, ClientSync.state)
        assertTrue(t.sent.isEmpty())
    }

    @Test
    fun aServerThatNeverRegisteredTheChannelIsNeverSentARequest() {
        val t = FakeClientTransport(false).also { ClientSync.install(it) }
        ClientSync.onJoin(remoteServer = true)
        assertEquals(ClientSync.State.NO_SERVER_SUPPORT, ClientSync.state)
        assertTrue(t.sent.isEmpty())
    }

    @Test
    fun aSupportingServerGetsOneRequestListingTheInstalledMods() {
        val t = FakeClientTransport(true).also { ClientSync.install(it) }
        ClientSync.onJoin(remoteServer = true)
        assertEquals(ClientSync.State.REQUESTED, ClientSync.state)
        val req = t.sent.single() as SyncRequestPayload
        assertEquals(SyncProtocol.VERSION, req.protocol)
        assertEquals(setOf("cobblemon@1.8.2", "fabricloader@0.19.5"), SyncRequestBody.parse(req.body))
    }

    @Test
    fun aRequestTheServerNeverAnswersEndsInsteadOfWaitingForever() {
        FakeClientTransport(true).also { ClientSync.install(it) }
        ClientSync.onJoin(remoteServer = true)
        now += ClientSync.REQUEST_TIMEOUT_MS - 1
        ClientSync.tick()
        assertEquals(ClientSync.State.REQUESTED, ClientSync.state)
        now += 2
        ClientSync.tick()
        assertEquals(ClientSync.State.FAILED, ClientSync.state)
    }

    @Test
    fun aCompleteTransferEndsWithTheOverlayInPlace() {
        FakeClientTransport(true).also { ClientSync.install(it) }
        ClientSync.onJoin(remoteServer = true)
        val p = payload()
        ClientSync.onHeader(header(p))
        assertEquals(ClientSync.State.RECEIVING, ClientSync.state)
        ClientSync.onChunk(SyncChunkPayload(0, p.compressed))
        waitFor("decode") { ClientSync.state == ClientSync.State.DONE }
        assertNotNull(ClientSync.overlay)
        assertEquals(1, ClientSync.overlay!!.fileCount)
    }

    @Test
    fun aFailedTransferLeavesNoOverlayAndTheNextRequestStillWorks() {
        FakeClientTransport(true).also { ClientSync.install(it) }
        ClientSync.onJoin(remoteServer = true)
        val p = payload()
        ClientSync.onHeader(header(p))
        ClientSync.onChunk(SyncChunkPayload(5, p.compressed))             // wrong index
        assertEquals(ClientSync.State.FAILED, ClientSync.state)
        assertNull(ClientSync.overlay)

        ClientSync.onJoin(remoteServer = true)                             // a later join recovers
        ClientSync.onHeader(header(p))
        ClientSync.onChunk(SyncChunkPayload(0, p.compressed))
        waitFor("decode") { ClientSync.state == ClientSync.State.DONE }
    }

    @Test
    fun refusedAndMalformedAnswersAreHandledWithoutThrowing() {
        FakeClientTransport(true).also { ClientSync.install(it) }
        ClientSync.onJoin(remoteServer = true)
        ClientSync.onHeader(SyncHeaderPayload.refused())
        assertEquals(ClientSync.State.REFUSED, ClientSync.state)
        ClientSync.onHeader(SyncHeaderPayload(SyncHeaderPayload.STATUS_MALFORMED, 0, 0, 0, 0))
        assertEquals(ClientSync.State.FAILED, ClientSync.state)
        ClientSync.onHeader(SyncHeaderPayload(SyncProtocol.STATUS_INCOMING, 1L, Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE))
        assertEquals(ClientSync.State.FAILED, ClientSync.state)
        ClientSync.onChunk(SyncChunkPayload(0, byteArrayOf(1)))           // no transfer in progress: ignored
        assertEquals(ClientSync.State.FAILED, ClientSync.state)
    }

    @Test
    fun leavingTheServerDropsAnInProgressTransferAndTheOverlay() {
        FakeClientTransport(true).also { ClientSync.install(it) }
        ClientSync.onJoin(remoteServer = true)
        val p = payload()
        ClientSync.onHeader(header(p))
        ClientSync.onLeave()
        ClientSync.onChunk(SyncChunkPayload(0, p.compressed))
        assertEquals(ClientSync.State.IDLE, ClientSync.state)
        assertNull(ClientSync.overlay)
    }

    @Test
    fun aFailureToBuildTheRequestIsContainedNotThrown() {
        FakeClientTransport(true).also { ClientSync.install(it) }
        ClientSync.modsProvider = { throw AssertionError("platform stub") }
        ClientSync.onJoin(remoteServer = true)
        assertEquals(ClientSync.State.FAILED, ClientSync.state)
    }
}
