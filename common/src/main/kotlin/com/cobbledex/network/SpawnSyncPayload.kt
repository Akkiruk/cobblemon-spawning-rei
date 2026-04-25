package com.cobbledex.network

import com.cobbledex.CobbleDexMod
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation

class SpawnSyncPayload(val data: ByteArray) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<SpawnSyncPayload> = TYPE

    companion object {
        const val MAX_PAYLOAD_SIZE = 1_048_576

        val TYPE = CustomPacketPayload.Type<SpawnSyncPayload>(
            ResourceLocation.fromNamespaceAndPath(CobbleDexMod.MOD_ID, "spawn_sync")
        )

        val CODEC: StreamCodec<FriendlyByteBuf, SpawnSyncPayload> = StreamCodec.of(
            { buf: FriendlyByteBuf, payload: SpawnSyncPayload -> buf.writeByteArray(payload.data) },
            { buf: FriendlyByteBuf -> SpawnSyncPayload(buf.readByteArray(MAX_PAYLOAD_SIZE)) }
        )
    }
}
