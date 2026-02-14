@file:Suppress("unused")

package com.cobbledex.platform.fabric

import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Path

object PlatformHelperImpl {

    @JvmStatic
    fun getConfigDir(): Path = FabricLoader.getInstance().configDir

    @JvmStatic
    fun getGameDir(): Path = FabricLoader.getInstance().gameDir
}
