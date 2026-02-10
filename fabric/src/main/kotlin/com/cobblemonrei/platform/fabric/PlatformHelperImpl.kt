@file:Suppress("unused")

package com.cobblemonrei.platform.fabric

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer
import java.nio.file.Path

object PlatformHelperImpl {

    @JvmStatic
    fun getConfigDir(): Path = FabricLoader.getInstance().configDir

    @JvmStatic
    fun getGameDir(): Path = FabricLoader.getInstance().gameDir

    @JvmStatic
    fun sendPayloadToPlayer(player: ServerPlayer, payload: CustomPacketPayload) {
        if (ServerPlayNetworking.canSend(player, payload.type().id())) {
            ServerPlayNetworking.send(player, payload)
        }
    }
}
