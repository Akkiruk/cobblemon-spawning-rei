package com.cobbledex.platform

import dev.architectury.injectables.annotations.ExpectPlatform
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
    fun isModLoaded(modId: String): Boolean {
        throw AssertionError("Expected platform implementation")
    }

    /** Every loaded mod, `id` to `version`. */
    @JvmStatic
    @ExpectPlatform
    fun getLoadedMods(): Map<String, String> {
        throw AssertionError("Expected platform implementation")
    }
}
