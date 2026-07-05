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
        // This result is cached per category+dataVersion+language (getBounds
        // above), so it only runs once per data load/reload - not worth an
        // early-exit shortcut that can under-measure the panel when an
        // outlier (e.g. a species with a very long evolution requirement
        // list, or many "notable differences" bullet points) happens to sit
        // outside whatever sample window a shortcut would have checked.
        // Scanning every recipe here is what fixed text overflowing the
        // Evolution and Alternate Forms panels.
        for (handle in recipes) {
            try {
                val w = handle.width
                val h = handle.height
                if (w > maxW) maxW = w
                if (h > maxH) maxH = h
            } catch (_: Exception) {}
        }
        return PanelSize(
            maxW.coerceIn(PanelLayout.MIN_WIDTH, PanelLayout.MAX_WIDTH),
            maxH.coerceAtMost(PanelLayout.MAX_HEIGHT)
        )
    }
}
