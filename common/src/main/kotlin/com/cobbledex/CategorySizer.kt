package com.cobbledex

import net.minecraft.client.Minecraft

object CategorySizer {

    data class PanelSize(val width: Int, val height: Int)

    private val cache = mutableMapOf<String, PanelSize>()
    @Volatile private var cachedVersion = -1L
    @Volatile private var cachedLang = ""

    fun getBounds(category: DexCategory): PanelSize {
        val ver = SpawnDataIndex.dataVersion
        val lang = try { Minecraft.getInstance().languageManager.selected } catch (_: Exception) { "en_us" }
        if (ver != cachedVersion || lang != cachedLang) {
            cache.clear()
            cachedVersion = ver
            cachedLang = lang
        }
        return cache.getOrPut(category.id) { computeBounds(category) }
    }

    fun invalidateCache() {
        cache.clear()
        cachedVersion = -1L
        cachedLang = ""
    }

    private fun computeBounds(category: DexCategory): PanelSize {
        val recipes = try { category.buildAllRecipes() } catch (_: Exception) { emptyList() }
        if (recipes.isEmpty()) return PanelSize(200, 100)
        var maxW = PanelLayout.MIN_WIDTH
        var maxH = 80
        var settled = true
        for ((i, handle) in recipes.withIndex()) {
            try {
                val w = handle.width
                val h = handle.height
                if (w > maxW) { maxW = w; settled = false }
                if (h > maxH) { maxH = h; settled = false }
                // If size hasn't grown in the last 50 recipes, the rest won't push it higher
                if (settled && i >= 50) break
                if (!settled && i % 50 == 49) settled = true
            } catch (_: Exception) {}
        }
        return PanelSize(
            maxW.coerceIn(PanelLayout.MIN_WIDTH, PanelLayout.MAX_WIDTH),
            maxH.coerceAtMost(PanelLayout.MAX_HEIGHT)
        )
    }
}
