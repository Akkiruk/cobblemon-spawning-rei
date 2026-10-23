package com.cobbledex

import com.cobbledex.platform.PlatformHelper
import net.minecraft.client.Minecraft

/**
 * How tall a recipe panel can be before the screen clips it. A panel is split into pages no taller
 * than this, so every row stays reachable at any window size and GUI scale. REI and JEI read it here;
 * EMI adds its own limits on top (see [com.cobbledex.emi.EmiPanelSlicer]).
 */
object ViewerBudget {

    /** Below this a panel is unusable anyway, so the budget never goes lower. */
    private const val FLOOR = 80

    /** Screen height REI's recipe window uses for its tabs, page bars and margins. */
    private const val REI_RESERVE = 80
    private const val JEI_RESERVE = 110

    /** The last real height seen; a minimised window reports none and must not shrink the budget. */
    @Volatile private var lastScaledHeight = 0

    /** The GUI-scaled height of the game window, or the last known one while the window has no size. */
    fun scaledHeight(): Int {
        val height = try {
            val window = Minecraft.getInstance().window
            if (window.height > 0) window.guiScaledHeight else 0
        } catch (_: Throwable) { 0 }
        if (height > 0) lastScaledHeight = height
        return if (lastScaledHeight > 0) lastScaledHeight else PanelLayout.MAX_HEIGHT + REI_RESERVE
    }

    private fun rei(): Int = (scaledHeight() - REI_RESERVE).coerceIn(FLOOR, PanelLayout.MAX_HEIGHT)

    private fun jei(): Int = (scaledHeight() - JEI_RESERVE).coerceIn(FLOOR, PanelLayout.MAX_HEIGHT)

    private val reiInstalled: Boolean by lazy { modLoaded("roughlyenoughitems") }
    private val jeiInstalled: Boolean by lazy { modLoaded("jei") && !modLoaded("emi") }

    private fun modLoaded(id: String): Boolean = try { PlatformHelper.isModLoaded(id) } catch (_: Throwable) { false }

    /** The page budget for REI and JEI. With both installed, the one that leaves less room wins. */
    fun current(): Int = when {
        reiInstalled && jeiInstalled -> minOf(rei(), jei())
        jeiInstalled -> jei()
        else -> rei()
    }

    /**
     * Budgets are compared in steps of this much, so a window being dragged or a size that differs by a
     * pixel doesn't count as a change.
     */
    const val STEP = 16

    fun bucket(budget: Int): Int = budget / STEP
}
