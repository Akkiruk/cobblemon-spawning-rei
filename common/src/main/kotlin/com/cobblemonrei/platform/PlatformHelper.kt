package com.cobblemonrei.platform

import dev.architectury.injectables.annotations.ExpectPlatform
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer
import java.nio.file.Path

object PlatformHelper {

    @JvmStatic
    @ExpectPlatform
    fun getConfigDir(): Path {
        throw AssertionError("Expected platform implementation")
    }

    @JvmStatic
    @ExpectPlatform
    fun getGameDir(): Path {
        throw AssertionError("Expected platform implementation")
    }

    @JvmStatic
    @ExpectPlatform
    fun sendPayloadToPlayer(player: ServerPlayer, payload: CustomPacketPayload) {
        throw AssertionError("Expected platform implementation")
    }
}
