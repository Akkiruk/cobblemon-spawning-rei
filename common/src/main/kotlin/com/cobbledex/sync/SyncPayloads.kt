package com.cobbledex.sync

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/*
 * The three packets of the sync. Their codecs are deliberately trivial and TOTAL: fixed-size fields,
 * then "the rest of the buffer", and they never read past what is there. A codec that reads more than
 * was sent kicks the player (DecoderException, tested), whereas an exception in a handler does not -
 * so every real decision happens in the handlers, never here.
 */

/** Client asks for the data. [body] is a small UTF-8 JSON document, see [SyncRequestBody]. */
class SyncRequestPayload(val protocol: Int, val cachedHash: Long, val body: ByteArray) : CustomPacketPayload {
    init {
        require(body.size <= SyncProtocol.MAX_REQUEST_BODY_BYTES) { "request body ${body.size} bytes is over the limit" }
    }

    override fun type(): CustomPacketPayload.Type<SyncRequestPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<SyncRequestPayload>(SyncProtocol.REQUEST_ID)

        private const val FIXED = 4 + 8

        val CODEC: StreamCodec<FriendlyByteBuf, SyncRequestPayload> = StreamCodec.of(
            { buf, p ->
                buf.writeInt(p.protocol)
                buf.writeLong(p.cachedHash)
                buf.writeBytes(p.body)
            },
            { buf -> decode(buf) }
        )

        internal fun decode(buf: FriendlyByteBuf): SyncRequestPayload {
            if (buf.readableBytes() < FIXED) {
                buf.skipBytes(buf.readableBytes())
                return SyncRequestPayload(-1, 0L, ByteArray(0))
            }
            val protocol = buf.readInt()
            val hash = buf.readLong()
            val body = readRest(buf, SyncProtocol.MAX_REQUEST_BODY_BYTES)
            return SyncRequestPayload(protocol, hash, body)
        }
    }
}

/** Server's answer: nothing changed, a transfer is coming, or no. */
class SyncHeaderPayload(
    val status: Int,
    val hash: Long,
    val chunkCount: Int,
    val compressedBytes: Int,
    val rawBytes: Int,
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<SyncHeaderPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<SyncHeaderPayload>(SyncProtocol.HEADER_ID)

        private const val FIXED = 1 + 8 + 4 + 4 + 4

        /** What a malformed header decodes to, so the handler can ignore it. */
        const val STATUS_MALFORMED = -1

        fun refused(): SyncHeaderPayload = SyncHeaderPayload(SyncProtocol.STATUS_REFUSED, 0L, 0, 0, 0)

        fun unchanged(hash: Long): SyncHeaderPayload = SyncHeaderPayload(SyncProtocol.STATUS_UNCHANGED, hash, 0, 0, 0)

        val CODEC: StreamCodec<FriendlyByteBuf, SyncHeaderPayload> = StreamCodec.of(
            { buf, p ->
                buf.writeByte(p.status)
                buf.writeLong(p.hash)
                buf.writeInt(p.chunkCount)
                buf.writeInt(p.compressedBytes)
                buf.writeInt(p.rawBytes)
            },
            { buf -> decode(buf) }
        )

        internal fun decode(buf: FriendlyByteBuf): SyncHeaderPayload {
            if (buf.readableBytes() < FIXED) {
                buf.skipBytes(buf.readableBytes())
                return SyncHeaderPayload(STATUS_MALFORMED, 0L, 0, 0, 0)
            }
            val status = buf.readByte().toInt()
            val hash = buf.readLong()
            val chunks = buf.readInt()
            val compressed = buf.readInt()
            val raw = buf.readInt()
            buf.skipBytes(buf.readableBytes())
            return SyncHeaderPayload(status, hash, chunks, compressed, raw)
        }
    }
}

/** One slice of the compressed document. The constructor refuses to build a chunk the wire could not carry. */
class SyncChunkPayload(val index: Int, val data: ByteArray) : CustomPacketPayload {
    init {
        require(data.size <= SyncProtocol.MAX_CHUNK_BYTES) { "chunk of ${data.size} bytes is over the ${SyncProtocol.MAX_CHUNK_BYTES} limit" }
    }

    override fun type(): CustomPacketPayload.Type<SyncChunkPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<SyncChunkPayload>(SyncProtocol.CHUNK_ID)

        private const val FIXED = 4

        /** A chunk read off the wire is allowed to be larger than we would send (a newer sender), up to the packet cap. */
        private const val READ_LIMIT = 2 * 1024 * 1024

        const val INDEX_MALFORMED = -1

        val CODEC: StreamCodec<FriendlyByteBuf, SyncChunkPayload> = StreamCodec.of(
            { buf, p ->
                buf.writeInt(p.index)
                buf.writeBytes(p.data)
            },
            { buf -> decode(buf) }
        )

        internal fun decode(buf: FriendlyByteBuf): SyncChunkPayload {
            if (buf.readableBytes() < FIXED) {
                buf.skipBytes(buf.readableBytes())
                return SyncChunkPayload(INDEX_MALFORMED, ByteArray(0))
            }
            val index = buf.readInt()
            val data = readRest(buf, READ_LIMIT)
            // Not the public constructor: a chunk above MAX_CHUNK_BYTES is rejected by the assembler, not by throwing here.
            return SyncChunkPayload.unchecked(index, data)
        }

        internal fun unchecked(index: Int, data: ByteArray): SyncChunkPayload =
            if (data.size <= SyncProtocol.MAX_CHUNK_BYTES) SyncChunkPayload(index, data)
            else SyncChunkPayload(INDEX_OVERSIZE, ByteArray(0))

        const val INDEX_OVERSIZE = -2
    }
}

/** Everything left in the buffer, capped at [limit]; the cap is read and the remainder skipped, never thrown on. */
private fun readRest(buf: FriendlyByteBuf, limit: Int): ByteArray {
    val n = minOf(buf.readableBytes(), limit)
    val out = ByteArray(n)
    buf.readBytes(out)
    buf.skipBytes(buf.readableBytes())
    return out
}
