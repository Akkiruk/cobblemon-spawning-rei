@file:Suppress("unused")

package com.cobbledex.platform.neoforge

import net.neoforged.fml.ModList
import net.neoforged.fml.loading.FMLPaths
import java.nio.file.Path

object PlatformHelperImpl {

    @JvmStatic
    fun getConfigDir(): Path = FMLPaths.CONFIGDIR.get()

    @JvmStatic
    fun getGameDir(): Path = FMLPaths.GAMEDIR.get()

    @JvmStatic
    fun isModLoaded(modId: String): Boolean = ModList.get().isLoaded(modId)

    @JvmStatic
    fun getLoadedMods(): Map<String, String> =
        ModList.get().mods.associate { it.modId to it.version.toString() }
}
