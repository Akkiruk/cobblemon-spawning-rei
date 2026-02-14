@file:Suppress("unused")

package com.cobbledex.platform.neoforge

import net.neoforged.fml.loading.FMLPaths
import java.nio.file.Path

object PlatformHelperImpl {

    @JvmStatic
    fun getConfigDir(): Path = FMLPaths.CONFIGDIR.get()

    @JvmStatic
    fun getGameDir(): Path = FMLPaths.GAMEDIR.get()
}
