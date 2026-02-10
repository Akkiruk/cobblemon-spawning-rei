package com.cobblemonrei.network

import com.cobblemonrei.CobblemonSpawningMod
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation

data class SpawnSyncHashPayload(
    val fingerprint: String
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<SpawnSyncHashPayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<SpawnSyncHashPayload> = CustomPacketPayload.Type(
            ResourceLocation.fromNamespaceAndPath(CobblemonSpawningMod.MOD_ID, "spawn_hash")
        )

        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, SpawnSyncHashPayload> = object : StreamCodec<FriendlyByteBuf, SpawnSyncHashPayload> {
            override fun decode(buf: FriendlyByteBuf): SpawnSyncHashPayload {
                return SpawnSyncHashPayload(buf.readUtf(256))
            }

            override fun encode(buf: FriendlyByteBuf, value: SpawnSyncHashPayload) {
                buf.writeUtf(value.fingerprint, 256)
            }
        }
    }
}
