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
        val fallback = PanelLayout.error("Data unavailable")
        val recipes = try {
            RecipeCatalogCache.getAllRecipes(category)
        } catch (e: Exception) {
            DebugLog.once("category-size-${category.id}") { "Category sizing failed: ${e.message}" }
            emptyList()
        }
        if (recipes.isEmpty()) return PanelSize(fallback.width, fallback.height)
        var maxW = PanelLayout.MIN_WIDTH
        var maxH = fallback.height
        for (handle in recipes) {
            try {
                maxW = maxOf(maxW, handle.width)
                maxH = maxOf(maxH, handle.height)
            } catch (e: Exception) {
                DebugLog.once("category-size-${category.id}-${handle.recipeIdPath}") {
                    "Recipe sizing failed: ${e.message}"
                }
            }
        }
        return PanelSize(
            maxW.coerceIn(PanelLayout.MIN_WIDTH, PanelLayout.MAX_WIDTH),
            maxH.coerceIn(fallback.height, PanelLayout.MAX_HEIGHT)
        )
    }
}
