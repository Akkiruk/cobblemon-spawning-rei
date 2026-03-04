package com.cobbledex

object CategorySizer {

    data class PanelSize(val width: Int, val height: Int)

    private val cache = mutableMapOf<String, PanelSize>()
    @Volatile private var cachedVersion = -1L

    fun getBounds(category: DexCategory): PanelSize {
        val ver = SpawnDataIndex.dataVersion
        if (ver != cachedVersion) {
            cache.clear()
            cachedVersion = ver
        }
        return cache.getOrPut(category.id) { computeBounds(category) }
    }

    fun invalidateCache() {
        cache.clear()
        cachedVersion = -1L
    }

    private fun computeBounds(category: DexCategory): PanelSize {
        val recipes = try { category.buildAllRecipes() } catch (_: Exception) { emptyList() }
        if (recipes.isEmpty()) return PanelSize(200, 100)
        var maxW = PanelLayout.MIN_WIDTH
        var maxH = 80
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
            maxH
        )
    }
}
