package com.cobbledex.sync

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TransferTest {

    private fun file(category: String, id: String, json: String, pack: String = "pack/x") = SourceFile(category, id, pack, json)

    private fun sampleFiles() = listOf(
        file("species", "cobblemon:species/a.json", """{"name":"Alpha","moves":["1:tackle","tutor:bite"]}"""),
        file("species", "cobblemon:species/b.json", """{"name":"Béta ✦","moves":["1:growl"]}"""),
        file("spawn_pool_world", "cobblemon:spawn_pool_world/a.json", """{"enabled":true,"spawns":[{"pokemon":"alpha"}]}"""),
        file("species_additions", "x:species_additions/c.json", """{"target":"cobblemon:alpha","moves":["egg:splash"]}"""),
    )

    private fun built(files: List<SourceFile> = sampleFiles()) = PayloadWriter.write(files, skip = { false })

    private fun header(p: BuiltPayload, chunkBytes: Int) = SyncHeaderPayload(
        SyncProtocol.STATUS_INCOMING, p.hash, p.chunkCountFor(chunkBytes), p.compressed.size, p.rawBytes,
    )

    private fun chunks(p: BuiltPayload, chunkBytes: Int) =
        (0 until p.chunkCountFor(chunkBytes)).map { SyncChunkPayload(it, p.chunk(it, chunkBytes)) }

    // ---- writer and decoder ------------------------------------------------------------------

    @Test
    fun aPayloadRoundTripsThroughTheDecoder() {
        val p = built()
        val overlay = OverlayDecoder.decode(p.compressed, p.hash)
        assertNotNull(overlay)
        assertEquals(4, overlay.fileCount)
        assertEquals("Alpha", overlay.categories["species"]!!["cobblemon:species/a.json"]!!.get("name").asString)
        assertEquals("Béta ✦", overlay.categories["species"]!!["cobblemon:species/b.json"]!!.get("name").asString)
        assertEquals(1, overlay.categories["species_additions"]!!.size)
    }

    @Test
    fun theRawSizeCountsUtf8BytesNotCharacters() {
        val p = built(listOf(file("species", "a:species/a.json", """{"name":"✦✦✦✦✦✦✦✦✦✦"}""")))
        val inflated = java.util.zip.InflaterInputStream(p.compressed.inputStream(), java.util.zip.Inflater(true)).readBytes()
        assertEquals(inflated.size, p.rawBytes)
    }

    @Test
    fun aBrokenFileIsDroppedAndCountedWithoutSpoilingTheRest() {
        val p = built(sampleFiles() + file("species", "x:species/broken.json", "{ this is not json") + file("species", "x:species/array.json", "[1,2]"))
        assertEquals(4, p.files)
        assertEquals(2, p.invalid)
        assertEquals(4, OverlayDecoder.decode(p.compressed, p.hash)!!.fileCount)
    }

    @Test
    fun filesTheClientAlreadyHasAreSkipped() {
        val files = listOf(
            file("species", "cobblemon:species/a.json", """{"name":"A"}""", pack = "mod/cobblemon"),
            file("species", "pack:species/b.json", """{"name":"B"}""", pack = "file/mypack"),
        )
        val p = PayloadWriter.write(files, skip = { it.packId == "mod/cobblemon" })
        assertEquals(1, p.files)
        assertEquals(1, p.skipped)
        assertEquals(setOf("pack:species/b.json"), OverlayDecoder.decode(p.compressed, p.hash)!!.categories["species"]!!.keys)
    }

    @Test
    fun theSkippedModsAndSpeciesOriginsTravelWithThePayload() {
        val files = listOf(
            file("species", "pack:species/a.json", """{"name":"A"}"""),
            file("species", "pack:species/b.json", """{"name":"B"}"""),
            file("spawn_pool_world", "pack:spawn_pool_world/a.json", """{"spawns":[]}"""),
        )
        val p = PayloadWriter.write(files, skip = { false }, skippedMods = listOf("zeta", "alpha"), origin = { if (it.id.endsWith("a.json")) "Pack A" else null })
        val overlay = OverlayDecoder.decode(p.compressed, p.hash)!!
        assertEquals(setOf("alpha", "zeta"), overlay.serverMods)
        assertEquals(mapOf("pack:species/a.json" to "Pack A"), overlay.origins)   // species only, and only where there is a name
    }

    @Test
    fun aPayloadWithoutModsOrOriginsStillDecodes() {
        val p = built()
        val overlay = OverlayDecoder.decode(p.compressed, p.hash)!!
        assertTrue(overlay.serverMods.isEmpty())
        assertTrue(overlay.origins.isEmpty())
    }

    @Test
    fun theExcludeHookRemovesFiles() {
        val p = PayloadWriter.write(sampleFiles(), skip = { false }, exclude = { _, json -> json.get("name")?.asString == "Alpha" })
        assertEquals(3, p.files)
        assertEquals(1, p.skipped)
    }

    @Test
    fun aDecompressionBombIsRejectedNotInflated() {
        val big = file("species", "x:species/big.json", """{"name":"x","pad":"${"a".repeat(2_000_000)}"}""")
        val p = built(listOf(big))
        assertTrue(p.compressed.size < 10_000, "the bomb compresses to almost nothing")
        assertNull(OverlayDecoder.decode(p.compressed, p.hash, maxRaw = 100_000))
    }

    @Test
    fun garbageAndWrongVersionsDecodeToNullInsteadOfThrowing() {
        assertNull(OverlayDecoder.decode(ByteArray(0), 0))
        assertNull(OverlayDecoder.decode(ByteArray(100) { it.toByte() }, 0))
        val other = java.io.ByteArrayOutputStream().also { out ->
            java.util.zip.DeflaterOutputStream(out, java.util.zip.Deflater(9, true)).use { it.write("""{"v":99,"categories":{}}""".toByteArray()) }
        }.toByteArray()
        assertNull(OverlayDecoder.decode(other, 0))
    }

    @Test
    fun identicalInputGivesAnIdenticalHash() {
        assertEquals(built().hash, built().hash)
        assertTrue(built().hash != built(sampleFiles().drop(1)).hash)
    }

    // ---- assembler ---------------------------------------------------------------------------

    @Test
    fun anOrderedTransferCompletesWithTheOriginalBytes() {
        val p = built(); val a = TransferAssembler()
        assertNull(a.begin(header(p, 64)))
        val list = chunks(p, 64)
        list.dropLast(1).forEach { assertIs<TransferAssembler.Outcome.Waiting>(a.accept(it)) }
        val done = a.accept(list.last())
        assertIs<TransferAssembler.Outcome.Complete>(done)
        assertContentEquals(p.compressed, done.compressed)
        assertTrue(!a.active)
    }

    @Test
    fun aSingleChunkTransferWorks() {
        val p = built(); val a = TransferAssembler()
        assertNull(a.begin(header(p, SyncProtocol.MAX_CHUNK_BYTES)))
        assertIs<TransferAssembler.Outcome.Complete>(a.accept(chunks(p, SyncProtocol.MAX_CHUNK_BYTES).single()))
    }

    @Test
    fun aMissingOrReorderedChunkFailsTheTransfer() {
        val p = built(); val list = chunks(p, 32)
        assertTrue(list.size > 3)
        val skipped = TransferAssembler().also { it.begin(header(p, 32)) }
        skipped.accept(list[0])
        assertIs<TransferAssembler.Outcome.Failed>(skipped.accept(list[2]))
        assertTrue(!skipped.active)

        val swapped = TransferAssembler().also { it.begin(header(p, 32)) }
        assertIs<TransferAssembler.Outcome.Failed>(swapped.accept(list[1]))
    }

    @Test
    fun aDuplicateChunkFailsTheTransfer() {
        val p = built(); val list = chunks(p, 32)
        val a = TransferAssembler().also { it.begin(header(p, 32)) }
        a.accept(list[0])
        assertIs<TransferAssembler.Outcome.Failed>(a.accept(list[0]))
    }

    @Test
    fun aCorruptedByteFailsTheHashCheck() {
        val p = built(); val list = chunks(p, 32).toMutableList()
        val flipped = list.last().data.copyOf().also { it[0] = (it[0] + 1).toByte() }
        list[list.lastIndex] = SyncChunkPayload(list.lastIndex, flipped)
        val a = TransferAssembler().also { it.begin(header(p, 32)) }
        list.dropLast(1).forEach { a.accept(it) }
        val out = a.accept(list.last())
        assertIs<TransferAssembler.Outcome.Failed>(out)
        assertEquals("hash mismatch", out.reason)
    }

    @Test
    fun moreDataThanTheHeaderAnnouncedFails() {
        val p = built()
        val lying = header(p, 64).let { SyncHeaderPayload(it.status, it.hash, it.chunkCount, it.compressedBytes - 1, it.rawBytes) }
        val a = TransferAssembler().also { assertNull(it.begin(lying)) }
        val list = chunks(p, 64)
        var last: TransferAssembler.Outcome = TransferAssembler.Outcome.Waiting
        for (c in list) { last = a.accept(c); if (last is TransferAssembler.Outcome.Failed) break }
        assertIs<TransferAssembler.Outcome.Failed>(last)
    }

    @Test
    fun implausibleHeadersAreRejectedBeforeAnythingIsReserved() {
        val ok = SyncHeaderPayload(SyncProtocol.STATUS_INCOMING, 1L, 2, 1000, 5000)
        assertNull(TransferAssembler().begin(ok))
        fun bad(h: SyncHeaderPayload) = assertNotNull(TransferAssembler().begin(h), "should reject $h")
        bad(SyncHeaderPayload(SyncProtocol.STATUS_INCOMING, 1L, 0, 1000, 5000))
        bad(SyncHeaderPayload(SyncProtocol.STATUS_INCOMING, 1L, -3, 1000, 5000))
        bad(SyncHeaderPayload(SyncProtocol.STATUS_INCOMING, 1L, SyncProtocol.MAX_CHUNKS + 1, 1000, 5000))
        bad(SyncHeaderPayload(SyncProtocol.STATUS_INCOMING, 1L, 2, 0, 5000))
        bad(SyncHeaderPayload(SyncProtocol.STATUS_INCOMING, 1L, 2, Int.MAX_VALUE, 5000))
        bad(SyncHeaderPayload(SyncProtocol.STATUS_INCOMING, 1L, 2, 1000, Int.MAX_VALUE))
        bad(SyncHeaderPayload(SyncProtocol.STATUS_INCOMING, 1L, 1, 2 * SyncProtocol.MAX_CHUNK_BYTES, 5000))
        bad(SyncHeaderPayload(SyncProtocol.STATUS_REFUSED, 1L, 2, 1000, 5000))
    }

    @Test
    fun aChunkWithNoTransferInProgressIsIgnored() {
        assertIs<TransferAssembler.Outcome.Ignored>(TransferAssembler().accept(SyncChunkPayload(0, byteArrayOf(1))))
    }

    @Test
    fun aMalformedOrOversizedChunkFailsTheTransfer() {
        val p = built()
        val a = TransferAssembler().also { it.begin(header(p, 32)) }
        assertIs<TransferAssembler.Outcome.Failed>(a.accept(SyncChunkPayload(SyncChunkPayload.INDEX_MALFORMED, ByteArray(0))))
        val b = TransferAssembler().also { it.begin(header(p, 32)) }
        assertIs<TransferAssembler.Outcome.Failed>(b.accept(SyncChunkPayload(SyncChunkPayload.INDEX_OVERSIZE, ByteArray(0))))
    }

    @Test
    fun aStalledTransferExpires() {
        var now = 0L
        val p = built(); val a = TransferAssembler { now }
        a.begin(header(p, 32))
        a.accept(chunks(p, 32)[0])
        now += SyncProtocol.TRANSFER_TIMEOUT_MS + 1
        assertTrue(a.expireIfStalled())
        assertTrue(!a.active)
    }

    @Test
    fun aLateChunkAfterTheTimeoutFailsInsteadOfCompleting() {
        var now = 0L
        val p = built(); val a = TransferAssembler { now }
        a.begin(header(p, 32))
        now += SyncProtocol.TRANSFER_TIMEOUT_MS + 1
        assertIs<TransferAssembler.Outcome.Failed>(a.accept(chunks(p, 32)[0]))
    }

    @Test
    fun aNewHeaderReplacesATransferInProgress() {
        val p = built(); val q = built(sampleFiles().take(2))
        val a = TransferAssembler(); a.begin(header(p, 32)); a.accept(chunks(p, 32)[0])
        assertNull(a.begin(header(q, 32)))
        val list = chunks(q, 32)
        list.dropLast(1).forEach { a.accept(it) }
        assertIs<TransferAssembler.Outcome.Complete>(a.accept(list.last()))
    }
}
