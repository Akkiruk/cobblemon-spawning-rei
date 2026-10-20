package com.cobbledex.server

import com.cobbledex.sync.OverlayDecoder
import com.cobbledex.sync.SourceFile
import com.cobbledex.sync.SyncChunkPayload
import com.cobbledex.sync.SyncHeaderPayload
import com.cobbledex.sync.SyncProtocol
import com.cobbledex.sync.SyncRequestBody
import com.cobbledex.sync.SyncRequestPayload
import com.cobbledex.sync.SyncServerTransport
import com.cobbledex.sync.TransferAssembler
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import java.util.UUID
import java.util.concurrent.Executor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ServerSyncServiceTest {

    private class FakeTransport : SyncServerTransport {
        val aware = HashSet<UUID>()      // players whose client registered the channels
        val ops = HashSet<UUID>()
        val gone = HashSet<UUID>()
        val sent = ArrayList<Pair<UUID, CustomPacketPayload>>()

        override fun canSend(player: UUID, type: CustomPacketPayload.Type<*>) = player in aware && player !in gone
        override fun send(player: UUID, payload: CustomPacketPayload): Boolean {
            if (player in gone) return false
            sent.add(player to payload)
            return true
        }
        override fun isOperator(player: UUID) = player in ops

        fun to(p: UUID) = sent.filter { it.first == p }.map { it.second }
        fun headers(p: UUID) = to(p).filterIsInstance<SyncHeaderPayload>()
        fun chunks(p: UUID) = to(p).filterIsInstance<SyncChunkPayload>()
    }

    private val direct = Executor { it.run() }
    private var now = 1_000_000L
    private val alice = UUID.randomUUID()
    private val bob = UUID.randomUUID()

    private fun files(count: Int = 6) = (0 until count).map {
        SourceFile("species", "pack:species/s$it.json", "file/pack", """{"name":"S$it","moves":["1:tackle","tutor:bite"],"pad":"${"x".repeat(2000)}"}""")
    } + SourceFile("spawn_pool_world", "pack:spawn_pool_world/p.json", "file/pack", """{"spawns":[]}""")

    /** Pseudo-random text does not compress, so these really do span many chunks. */
    private fun noisy(count: Int, charsEach: Int = 4000): List<SourceFile> {
        val rnd = java.util.Random(7)
        return (0 until count).map { i ->
            val pad = buildString { repeat(charsEach) { append('a' + rnd.nextInt(26)) } }
            SourceFile("species", "pack:species/n$i.json", "file/pack", """{"name":"N$i","pad":"$pad"}""")
        }
    }

    private fun service(
        t: FakeTransport,
        config: ServerSyncConfig = ServerSyncConfig(chunkKB = 16),
        mods: Map<String, String> = emptyMap(),
        data: List<SourceFile> = files(),
    ) = ServerSyncService(t, config, mods, direct, { now }).also { it.setSnapshot(data) }

    private fun request(hash: Long = 0L, mods: List<String> = emptyList(), protocol: Int = SyncProtocol.VERSION) =
        SyncRequestPayload(protocol, hash, SyncRequestBody.encode(mods))

    private fun FakeTransport.awareOf(vararg p: UUID) = also { aware.addAll(p) }

    private fun drain(s: ServerSyncService, ticks: Int = 200) = repeat(ticks) { s.tick() }

    /** Plays the client's side of a finished transfer and returns the decoded overlay. */
    private fun receive(t: FakeTransport, p: UUID): com.cobbledex.sync.ServerOverlay? {
        val header = t.headers(p).single()
        val a = TransferAssembler { now }
        assertEquals(null, a.begin(header))
        var done: TransferAssembler.Outcome? = null
        for (c in t.chunks(p)) done = a.accept(c)
        assertIs<TransferAssembler.Outcome.Complete>(done)
        return OverlayDecoder.decode(done.compressed, done.header.hash)
    }

    @Test
    fun aSnapshotSubmittedFromAnotherThreadIsAppliedOnTheNextTick() {
        val t = FakeTransport().awareOf(alice)
        val s = ServerSyncService(t, ServerSyncConfig(chunkKB = 16), emptyMap(), direct, { now })
        s.submitSnapshot(files())
        s.handleRequest(alice, "alice", request())          // not applied yet: refused...
        assertEquals(SyncProtocol.STATUS_REFUSED, t.headers(alice).single().status)
        s.tick()                                            // ...applied here
        t.sent.clear()
        s.handleRequest(alice, "alice", request())          // ...and the refusal above did not use up the cooldown
        drain(s)
        assertEquals(SyncProtocol.STATUS_INCOMING, t.headers(alice).single().status)
    }

    @Test
    fun aClientThatDidNotRegisterTheChannelIsNeverSentAnything() {
        val t = FakeTransport()                       // nobody is aware
        val s = service(t)
        s.handleRequest(alice, "alice", request())
        drain(s)
        assertTrue(t.sent.isEmpty())
        assertEquals(1, s.requestsIgnored)
    }

    @Test
    fun anAwareClientGetsTheWholePayloadAsOrderedChunks() {
        val t = FakeTransport().awareOf(alice)
        val s = service(t)
        s.handleRequest(alice, "alice", request())
        drain(s)

        val header = t.headers(alice).single()
        assertEquals(SyncProtocol.STATUS_INCOMING, header.status)
        assertEquals(header.chunkCount, t.chunks(alice).size)
        assertEquals((0 until header.chunkCount).toList(), t.chunks(alice).map { it.index })
        val overlay = assertNotNull(receive(t, alice))
        assertEquals(7, overlay.fileCount)
        assertEquals(0, s.activeTransfers())
    }

    @Test
    fun noChunkIsEverLargerThanTheConfiguredSizeOrTheWireLimit() {
        val t = FakeTransport().awareOf(alice)
        val s = service(t, ServerSyncConfig(chunkKB = 16), data = noisy(120))
        s.handleRequest(alice, "alice", request())
        drain(s, 2000)
        assertTrue(t.chunks(alice).size > 5, "expected many chunks, got ${t.chunks(alice).size}")
        assertTrue(t.chunks(alice).all { it.data.size <= 16 * 1024 && it.data.size <= SyncProtocol.MAX_CHUNK_BYTES })
    }

    @Test
    fun aConfiguredChunkSizeAboveTheWireLimitIsClamped() {
        assertEquals(SyncProtocol.MAX_CHUNK_BYTES, ServerSyncConfig(chunkKB = 100_000).chunkBytes)
        assertEquals(16 * 1024, ServerSyncConfig(chunkKB = 0).chunkBytes)
    }

    @Test
    fun aMatchingCachedHashGetsAnUnchangedAnswerAndNoChunks() {
        val t = FakeTransport().awareOf(alice)
        val s = service(t)
        s.handleRequest(alice, "alice", request())
        drain(s)
        val hash = t.headers(alice).single().hash

        now += 61_000
        t.sent.clear()
        s.handleRequest(alice, "alice", request(hash = hash))
        drain(s)
        assertEquals(SyncProtocol.STATUS_UNCHANGED, t.headers(alice).single().status)
        assertTrue(t.chunks(alice).isEmpty())
    }

    @Test
    fun aSecondTransferInsideTheCooldownIsRefusedAndOneAfterItIsServed() {
        val t = FakeTransport().awareOf(alice)
        val s = service(t)
        s.handleRequest(alice, "alice", request()); drain(s)
        assertEquals(SyncProtocol.STATUS_INCOMING, t.headers(alice).single().status)

        now += 5_000                                     // past the request spacing, inside the 60 s cooldown
        t.sent.clear()
        s.handleRequest(alice, "alice", request()); drain(s)
        assertEquals(SyncProtocol.STATUS_REFUSED, t.headers(alice).single().status)
        assertTrue(t.chunks(alice).isEmpty())

        now += 61_000
        t.sent.clear()
        s.handleRequest(alice, "alice", request()); drain(s)
        assertEquals(SyncProtocol.STATUS_INCOMING, t.headers(alice).single().status)
    }

    @Test
    fun reconnectingInALoopDoesNotDodgeTheTransferCooldown() {
        val t = FakeTransport().awareOf(alice)
        val s = service(t)
        s.handleRequest(alice, "alice", request()); drain(s)
        repeat(5) {
            s.onDisconnect(alice)
            now += 5_000
            t.sent.clear()
            s.handleRequest(alice, "alice", request()); drain(s)
            assertEquals(SyncProtocol.STATUS_REFUSED, t.headers(alice).single().status)
            assertTrue(t.chunks(alice).isEmpty())
        }
    }

    @Test
    fun aQuickRejoinWithCurrentDataIsAnsweredUnchangedEvenInsideTheCooldown() {
        val t = FakeTransport().awareOf(alice)
        val s = service(t)
        s.handleRequest(alice, "alice", request()); drain(s)
        val hash = t.headers(alice).single().hash

        now += 5_000
        t.sent.clear()
        s.handleRequest(alice, "alice", request(hash = hash)); drain(s)
        assertEquals(SyncProtocol.STATUS_UNCHANGED, t.headers(alice).single().status)
    }

    @Test
    fun requestsCloserTogetherThanTheSpacingAreIgnored() {
        val t = FakeTransport().awareOf(alice)
        val s = service(t)
        s.handleRequest(alice, "alice", request()); drain(s)
        t.sent.clear()
        now += 1_000
        s.handleRequest(alice, "alice", request()); drain(s)
        assertTrue(t.sent.isEmpty())
    }

    @Test
    fun aFloodOfRequestsCostsOneTransfer() {
        val t = FakeTransport().awareOf(alice)
        val s = service(t)
        repeat(500) { s.handleRequest(alice, "alice", request()) }
        drain(s)
        assertEquals(1, t.headers(alice).size)
        assertEquals(499, s.requestsIgnored)
    }

    @Test
    fun aDifferentProtocolVersionIsRefused() {
        val t = FakeTransport().awareOf(alice)
        val s = service(t)
        s.handleRequest(alice, "alice", request(protocol = 99))
        drain(s)
        assertEquals(SyncProtocol.STATUS_REFUSED, t.headers(alice).single().status)
        assertTrue(t.chunks(alice).isEmpty())
    }

    @Test
    fun aDisabledServerRefuses() {
        val t = FakeTransport().awareOf(alice)
        val s = service(t, ServerSyncConfig(enabled = false))
        s.handleRequest(alice, "alice", request()); drain(s)
        assertEquals(SyncProtocol.STATUS_REFUSED, t.headers(alice).single().status)
    }

    @Test
    fun opsOnlyServesOperatorsAndRefusesEveryoneElse() {
        val t = FakeTransport().awareOf(alice, bob); t.ops.add(alice)
        val s = service(t, ServerSyncConfig(opsOnly = true))
        s.handleRequest(alice, "alice", request()); s.handleRequest(bob, "bob", request()); drain(s)
        assertEquals(SyncProtocol.STATUS_INCOMING, t.headers(alice).single().status)
        assertEquals(SyncProtocol.STATUS_REFUSED, t.headers(bob).single().status)
    }

    @Test
    fun anEmptyServerRefusesInsteadOfSendingAnEmptyDocument() {
        val t = FakeTransport().awareOf(alice)
        val s = service(t, data = emptyList())
        s.handleRequest(alice, "alice", request()); drain(s)
        assertEquals(SyncProtocol.STATUS_REFUSED, t.headers(alice).single().status)
    }

    @Test
    fun aPayloadOverTheConfiguredLimitIsRefused() {
        // Pseudo-random text does not compress, so ~3 MB of it is a payload over the 1 MB limit.
        val rnd = java.util.Random(1)
        val noisy = (0 until 30).map { i ->
            val junk = buildString { repeat(100_000) { append(('a' + rnd.nextInt(26))) } }
            SourceFile("species", "pack:species/n$i.json", "file/pack", """{"name":"N$i","pad":"$junk"}""")
        }
        val t = FakeTransport().awareOf(alice)
        val s = service(t, ServerSyncConfig(maxPayloadMB = 1), data = noisy)
        s.handleRequest(alice, "alice", request()); drain(s)
        assertEquals(SyncProtocol.STATUS_REFUSED, t.headers(alice).single().status)
        assertTrue(t.chunks(alice).isEmpty())
    }

    @Test
    fun aClientThatListsAModWithTheSameVersionDoesNotGetThatModsData() {
        val data = listOf(
            SourceFile("species", "cobblemon:species/a.json", "mod/cobblemon", """{"name":"A"}"""),
            SourceFile("species", "pack:species/b.json", "file/pack", """{"name":"B"}"""),
        )
        val t = FakeTransport().awareOf(alice, bob)
        val s = service(t, mods = mapOf("cobblemon" to "1.8.2"), data = data)
        s.handleRequest(alice, "alice", request(mods = listOf("cobblemon@1.8.2")))
        s.handleRequest(bob, "bob", request(mods = listOf("cobblemon@1.7.3")))     // different version: must get it
        drain(s)
        assertEquals(setOf("pack:species/b.json"), receive(t, alice)!!.categories["species"]!!.keys)
        assertEquals(setOf("cobblemon:species/a.json", "pack:species/b.json"), receive(t, bob)!!.categories["species"]!!.keys)
    }

    @Test
    fun aModsPackIsRecognisedByBothLoadersNamingSchemesAndADatapackNeverIs() {
        val data = listOf(
            SourceFile("species", "cobblemon:species/fabric_style.json", "cobblemon", """{"name":"A"}"""),
            SourceFile("species", "cobblemon:species/neoforge_style.json", "mod/cobblemon", """{"name":"B"}"""),
            SourceFile("species", "pack:species/datapack_same_name.json", "file/cobblemon", """{"name":"C"}"""),
            SourceFile("species", "pack:species/openloader.json", "openloader/C:/packs/cobblemon.zip", """{"name":"D"}"""),
        )
        val t = FakeTransport().awareOf(alice)
        val s = service(t, mods = mapOf("cobblemon" to "1.8.2"), data = data)
        s.handleRequest(alice, "alice", request(mods = listOf("cobblemon@1.8.2")))
        drain(s)
        assertEquals(
            setOf("pack:species/datapack_same_name.json", "pack:species/openloader.json"),
            receive(t, alice)!!.categories["species"]!!.keys,
        )
    }

    @Test
    fun clientsWithTheSameModsShareOneBuild() {
        val t = FakeTransport().awareOf(alice, bob)
        val s = service(t)
        s.handleRequest(alice, "alice", request()); s.handleRequest(bob, "bob", request())
        drain(s)
        assertEquals(t.headers(alice).single().hash, t.headers(bob).single().hash)
    }

    @Test
    fun excludedFilesAndSpeciesAreNeverSent() {
        val data = listOf(
            SourceFile("species", "pack:species/secret.json", "file/pack", """{"name":"Secretmon"}"""),
            SourceFile("species", "pack:species/shown.json", "file/pack", """{"name":"Shownmon"}"""),
            SourceFile("species_additions", "pack:species_additions/x.json", "file/pack", """{"target":"cobblemon:hiddenmon"}"""),
            SourceFile("spawn_pool_world", "pack:spawn_pool_world/secret_event.json", "file/pack", """{"spawns":[]}"""),
        )
        val t = FakeTransport().awareOf(alice)
        val s = service(t, ServerSyncConfig(excludeSpecies = listOf("SecretMon", "cobblemon:hiddenmon"), excludeFiles = listOf("pack:spawn_pool_world/secret_*")), data = data)
        s.handleRequest(alice, "alice", request()); drain(s)
        val ids = receive(t, alice)!!.categories.values.flatMap { it.keys }
        assertEquals(listOf("pack:species/shown.json"), ids)
    }

    @Test
    fun transfersAreSpreadOverTicksWithinThePerTickBudget() {
        val t = FakeTransport().awareOf(alice, bob)
        val s = service(t, data = noisy(150))
        s.handleRequest(alice, "alice", request()); s.handleRequest(bob, "bob", request())
        s.tick()                                   // builds and starts both transfers, sends the first chunks
        val firstTick = t.chunks(alice).size + t.chunks(bob).size
        assertTrue(firstTick in 1..6, "at most 6 chunks per tick overall, was $firstTick")
        assertTrue(t.chunks(alice).size <= 2 && t.chunks(bob).size <= 2)
        drain(s, 5000)
        assertEquals(t.headers(alice).single().chunkCount, t.chunks(alice).size)
        assertEquals(t.headers(bob).single().chunkCount, t.chunks(bob).size)
    }

    @Test
    fun aPlayerWhoDisconnectsMidTransferStopsReceivingAndIsForgotten() {
        val t = FakeTransport().awareOf(alice)
        val s = service(t, data = noisy(150))
        s.handleRequest(alice, "alice", request()); s.tick()
        val before = t.chunks(alice).size
        assertEquals(1, s.activeTransfers())
        s.onDisconnect(alice)
        drain(s)
        assertEquals(before, t.chunks(alice).size)
        assertEquals(0, s.activeTransfers())
    }

    @Test
    fun aPlayerWhoIsGoneWithoutADisconnectEventEndsTheirTransfer() {
        val t = FakeTransport().awareOf(alice)
        val s = service(t, data = noisy(150))
        s.handleRequest(alice, "alice", request()); s.tick()
        t.gone.add(alice)
        drain(s)
        assertEquals(0, s.activeTransfers())
    }

    @Test
    fun aReloadedSnapshotIsServedToTheNextRequestNotTheOldCache() {
        val t = FakeTransport().awareOf(alice)
        val s = service(t)
        s.handleRequest(alice, "alice", request()); drain(s)
        val first = t.headers(alice).single().hash

        s.setSnapshot(files(2))
        now += 61_000; t.sent.clear()
        s.handleRequest(alice, "alice", request()); drain(s)
        assertTrue(t.headers(alice).single().hash != first)
    }

    @Test
    fun aRequestWhileTheBuildIsStillRunningSendsNothingYetAndThrowsNothing() {
        val t = FakeTransport().awareOf(alice)
        val failing = Executor { /* never runs the job: simulates a build that is still going */ }
        val s = ServerSyncService(t, ServerSyncConfig(), emptyMap(), failing, { now }).also { it.setSnapshot(files()) }
        s.handleRequest(alice, "alice", request())
        s.tick()
        assertTrue(t.sent.isEmpty())                 // still building, nothing sent, nothing thrown
    }
}
