package com.cobbledex.emi

import com.cobbledex.PanelLayout
import dev.emi.emi.config.EmiConfig
import net.minecraft.client.Minecraft

/**
 * EMI's per-recipe page budget - the one viewer-specific ceiling in the shared pagination scheme
 * (see [com.cobbledex.RecipeHandle.paginate]), because EMI's recipe area can be shorter than
 * [PanelLayout.MAX_HEIGHT] on a small window, unlike REI/JEI's fixed category bounds.
 */
object EmiPanelSlicer {
    fun budget(): Int {
        val screen = Minecraft.getInstance().window.guiScaledHeight - 52 - EmiConfig.verticalMargin
        return minOf(PanelLayout.MAX_HEIGHT, EmiConfig.maximumRecipeScreenHeight, screen - 46).coerceAtLeast(80)
    }
}
