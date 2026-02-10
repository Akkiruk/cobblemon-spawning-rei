package com.cobblemonrei.emi

import com.cobblemonrei.DebugLog
import dev.emi.emi.api.EmiPlugin
import dev.emi.emi.api.EmiRegistry

open class CobblemonEMIPlugin : EmiPlugin {

    override fun register(registry: EmiRegistry) {
        DebugLog.info("EMI registration — not yet implemented")
        // TODO: Register spawn and evolution entries/recipes for EMI
    }
}
