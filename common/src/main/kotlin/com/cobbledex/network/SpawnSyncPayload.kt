package com.cobbledex.network

import com.cobbledex.CobbleDexMod
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation

class SpawnSyncPayload(val data: ByteArray) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<SpawnSyncPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<SpawnSyncPayload>(
            ResourceLocation.fromNamespaceAndPath(CobbleDexMod.MOD_ID, "spawn_sync")
        )

        val CODEC: StreamCodec<FriendlyByteBuf, SpawnSyncPayload> = CustomPacketPayload.codec(
            { payload, buf -> buf.writeByteArray(payload.data) },
            { buf -> SpawnSyncPayload(buf.readByteArray()) }
        )
    }
}
