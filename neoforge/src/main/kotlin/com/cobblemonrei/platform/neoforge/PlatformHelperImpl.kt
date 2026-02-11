@file:Suppress("unused")

package com.cobblemonrei.platform.neoforge

import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer
import net.neoforged.fml.loading.FMLPaths
import java.nio.file.Path

object PlatformHelperImpl {

    @JvmStatic
    fun getConfigDir(): Path = FMLPaths.CONFIGDIR.get()

    @JvmStatic
    fun getGameDir(): Path = FMLPaths.GAMEDIR.get()

    @JvmStatic
    fun sendPayloadToPlayer(player: ServerPlayer, payload: CustomPacketPayload) {
        try {
            player.connection.send(ClientboundCustomPayloadPacket(payload))
        } catch (e: Exception) {
            com.cobblemonrei.DebugLog.once("send-payload-${player.uuid}") {
                "Failed to send payload to ${player.name.string}: ${e.message}"
            }
        }
    }
}
