package com.cobbledex.sync

import io.netty.buffer.Unpooled
import net.minecraft.network.FriendlyByteBuf
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SyncPayloadTest {

    private fun buf() = FriendlyByteBuf(Unpooled.buffer())

    @Test
    fun aRequestSurvivesTheWire() {
        val b = buf()
        SyncRequestPayload.CODEC.encode(b, SyncRequestPayload(1, 42L, "abc".toByteArray()))
        val back = SyncRequestPayload.CODEC.decode(b)
        assertEquals(1, back.protocol)
        assertEquals(42L, back.cachedHash)
        assertContentEquals("abc".toByteArray(), back.body)
        assertEquals(0, b.readableBytes())
    }

    @Test
    fun aHeaderSurvivesTheWire() {
        val b = buf()
        SyncHeaderPayload.CODEC.encode(b, SyncHeaderPayload(SyncProtocol.STATUS_INCOMING, -7L, 3, 1000, 5000))
        val back = SyncHeaderPayload.CODEC.decode(b)
        assertEquals(SyncProtocol.STATUS_INCOMING, back.status)
        assertEquals(-7L, back.hash)
        assertEquals(3, back.chunkCount)
        assertEquals(1000, back.compressedBytes)
        assertEquals(5000, back.rawBytes)
    }

    @Test
    fun aChunkSurvivesTheWire() {
        val b = buf()
        SyncChunkPayload.CODEC.encode(b, SyncChunkPayload(9, byteArrayOf(1, 2, 3)))
        val back = SyncChunkPayload.CODEC.decode(b)
        assertEquals(9, back.index)
        assertContentEquals(byteArrayOf(1, 2, 3), back.data)
    }

    // Reading more than was sent is what kicks a player, so every decoder must cope with any buffer at all.

    @Test
    fun noDecoderEverReadsPastTheEndOfAShortBuffer() {
        for (size in 0..24) {
            val bytes = ByteArray(size) { 7 }
            SyncRequestPayload.CODEC.decode(FriendlyByteBuf(Unpooled.wrappedBuffer(bytes)))
            SyncHeaderPayload.CODEC.decode(FriendlyByteBuf(Unpooled.wrappedBuffer(bytes)))
            SyncChunkPayload.CODEC.decode(FriendlyByteBuf(Unpooled.wrappedBuffer(bytes)))
        }
    }

    @Test
    fun aTooShortBufferDecodesToAMarkedMalformedPacketAndIsFullyConsumed() {
        val req = Unpooled.wrappedBuffer(ByteArray(5))
        assertEquals(-1, SyncRequestPayload.CODEC.decode(FriendlyByteBuf(req)).protocol)
        assertEquals(0, req.readableBytes())

        val head = Unpooled.wrappedBuffer(ByteArray(5))
        assertEquals(SyncHeaderPayload.STATUS_MALFORMED, SyncHeaderPayload.CODEC.decode(FriendlyByteBuf(head)).status)
        assertEquals(0, head.readableBytes())

        val chunk = Unpooled.wrappedBuffer(ByteArray(2))
        assertEquals(SyncChunkPayload.INDEX_MALFORMED, SyncChunkPayload.CODEC.decode(FriendlyByteBuf(chunk)).index)
        assertEquals(0, chunk.readableBytes())
    }

    @Test
    fun extraTrailingBytesAreConsumedNotRejected() {
        val b = buf()
        SyncHeaderPayload.CODEC.encode(b, SyncHeaderPayload.refused())
        b.writeBytes(ByteArray(40))
        val back = SyncHeaderPayload.CODEC.decode(b)
        assertEquals(SyncProtocol.STATUS_REFUSED, back.status)
        assertEquals(0, b.readableBytes())
    }

    @Test
    fun aChunkTheWireCouldNotCarryCannotBeBuilt() {
        assertFailsWith<IllegalArgumentException> { SyncChunkPayload(0, ByteArray(SyncProtocol.MAX_CHUNK_BYTES + 1)) }
        SyncChunkPayload(0, ByteArray(SyncProtocol.MAX_CHUNK_BYTES))
    }

    @Test
    fun anOversizedChunkReadOffTheWireBecomesAMarkerInsteadOfThrowing() {
        val b = buf()
        b.writeInt(3)
        b.writeBytes(ByteArray(SyncProtocol.MAX_CHUNK_BYTES + 10))
        assertEquals(SyncChunkPayload.INDEX_OVERSIZE, SyncChunkPayload.CODEC.decode(b).index)
    }

    @Test
    fun aRequestBodyOverTheClientToServerLimitCannotBeBuilt() {
        assertFailsWith<IllegalArgumentException> { SyncRequestPayload(1, 0L, ByteArray(SyncProtocol.MAX_REQUEST_BODY_BYTES + 1)) }
    }

    @Test
    fun theChannelIdsCarryTheProtocolVersion() {
        assertEquals("cobbledex:sync_request_v1", SyncProtocol.REQUEST_ID.toString())
        assertEquals("cobbledex:sync_header_v1", SyncProtocol.HEADER_ID.toString())
        assertEquals("cobbledex:sync_chunk_v1", SyncProtocol.CHUNK_ID.toString())
    }

    @Test
    fun theRequestBodyStaysUnderTheLimitNoMatterHowManyModsThereAre() {
        val mods = (1..5000).map { "some_mod_number_$it@1.2.3-long-version-$it" }
        val bytes = SyncRequestBody.encode(mods)
        assertTrue(bytes.size <= SyncProtocol.MAX_REQUEST_BODY_BYTES)
        assertTrue(SyncRequestBody.parse(bytes)!!.isNotEmpty())
    }

    @Test
    fun aRequestBodyThatIsNotOursParsesToNullInsteadOfThrowing() {
        assertNull(SyncRequestBody.parse("not json".toByteArray()))
        assertNull(SyncRequestBody.parse(ByteArray(0)))
        assertNull(SyncRequestBody.parse("[1,2,3]".toByteArray()))
    }
}
