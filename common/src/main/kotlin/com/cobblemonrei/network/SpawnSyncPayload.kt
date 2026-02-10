package com.cobblemonrei.network

import com.cobblemonrei.CobblemonSpawningMod
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation

data class SpawnSyncPayload(
    val chunkIndex: Int,
    val totalChunks: Int,
    val data: ByteArray
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<SpawnSyncPayload> = TYPE

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SpawnSyncPayload) return false
        return chunkIndex == other.chunkIndex && totalChunks == other.totalChunks && data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        var result = chunkIndex
        result = 31 * result + totalChunks
        result = 31 * result + data.contentHashCode()
        return result
    }

    companion object {
        val TYPE: CustomPacketPayload.Type<SpawnSyncPayload> = CustomPacketPayload.Type(
            ResourceLocation.fromNamespaceAndPath(CobblemonSpawningMod.MOD_ID, "spawn_sync")
        )

        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, SpawnSyncPayload> = object : StreamCodec<FriendlyByteBuf, SpawnSyncPayload> {
            override fun decode(buf: FriendlyByteBuf): SpawnSyncPayload {
                val chunkIndex = buf.readVarInt()
                val totalChunks = buf.readVarInt()
                val data = buf.readByteArray(1_048_576)
                return SpawnSyncPayload(chunkIndex, totalChunks, data)
            }

            override fun encode(buf: FriendlyByteBuf, value: SpawnSyncPayload) {
                buf.writeVarInt(value.chunkIndex)
                buf.writeVarInt(value.totalChunks)
                buf.writeByteArray(value.data)
            }
        }
    }
}
