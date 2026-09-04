package com.cobbledex.neoforge

import com.cobbledex.CobbleDexMod
import com.cobbledex.network.CobbleworkersJobSyncPayload
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.common.Mod
import net.neoforged.fml.loading.FMLEnvironment
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent

/**
 * CobbleDex is a pure client-side viewer. It runs no server logic and sends no packets of its
 * own — every Pokémon fact it shows is read from data Cobblemon and Minecraft already sync to
 * the client, or from the client's own files. See [com.cobbledex.SpawnDataIndex].
 *
 * The only payload registered here is CobbleCrew's, which that mod sends on its own channel.
 */
@Mod(CobbleDexMod.NEOFORGE_MOD_ID)
class CobbleDexNeoForge(modBus: IEventBus) {

    init {
        modBus.addListener(::onRegisterPayloadHandlers)

        if (FMLEnvironment.dist == Dist.CLIENT) {
            try {
                CobbleDexMod.init()
                CobbleDexNeoForgeClient.register()
            } catch (e: Exception) {
                CobbleDexMod.LOGGER.error("[CobbleDex] Client init failed: ${e.message}")
            }
        }
    }

    private fun onRegisterPayloadHandlers(event: RegisterPayloadHandlersEvent) {
        if (FMLEnvironment.dist != Dist.CLIENT) return
        // CobbleCrew job rules, sent by CobbleCrew's server side on its own channel.
        val cwRegistrar = event.registrar("cobblecrew").optional()
        cwRegistrar.playToClient(
            CobbleworkersJobSyncPayload.TYPE,
            CobbleworkersJobSyncPayload.CODEC
        ) { payload, context ->
            context.enqueueWork {
                try {
                    CobbleworkersJobSyncPayload.applyJobRules(payload.data)
                } catch (e: Exception) {
                    CobbleDexMod.LOGGER.error("[CobbleDex] Failed to process CobbleCrew job sync: ${e.message}")
                }
            }
        }
    }
}
