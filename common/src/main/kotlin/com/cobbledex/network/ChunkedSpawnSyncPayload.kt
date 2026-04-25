package com.cobbledex.network

import com.cobbledex.CobbleDexMod
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation

class ChunkedSpawnSyncPayload(
    val chunkIndex: Int,
    val totalChunks: Int,
    val data: ByteArray
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<ChunkedSpawnSyncPayload> = TYPE

    companion object {
        const val MAX_CHUNK_SIZE = 900_000

        val TYPE = CustomPacketPayload.Type<ChunkedSpawnSyncPayload>(
            ResourceLocation.fromNamespaceAndPath(CobbleDexMod.MOD_ID, "chunk_sync")
        )

        val CODEC: StreamCodec<FriendlyByteBuf, ChunkedSpawnSyncPayload> = StreamCodec.of(
            { buf: FriendlyByteBuf, payload: ChunkedSpawnSyncPayload ->
                buf.writeVarInt(payload.chunkIndex)
                buf.writeVarInt(payload.totalChunks)
                buf.writeByteArray(payload.data)
            },
            { buf: FriendlyByteBuf ->
                val index = buf.readVarInt()
                val total = buf.readVarInt()
                val data = buf.readByteArray(MAX_CHUNK_SIZE + 1024)
                ChunkedSpawnSyncPayload(index, total, data)
            }
        )

        fun split(compressed: ByteArray): List<ChunkedSpawnSyncPayload> {
            val totalChunks = (compressed.size + MAX_CHUNK_SIZE - 1) / MAX_CHUNK_SIZE
            val chunks = mutableListOf<ChunkedSpawnSyncPayload>()
            var offset = 0
            var index = 0
            while (offset < compressed.size) {
                val end = minOf(offset + MAX_CHUNK_SIZE, compressed.size)
                chunks.add(ChunkedSpawnSyncPayload(index, totalChunks, compressed.copyOfRange(offset, end)))
                offset = end
                index++
            }
            return chunks
        }
    }
}
