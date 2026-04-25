package com.cobbledex

import net.minecraft.client.Minecraft
import java.util.concurrent.ConcurrentHashMap

object RecipeCatalogCache {

    private val cache = ConcurrentHashMap<String, List<RecipeHandle>>()

    @Volatile
    private var cachedVersion = -1L

    @Volatile
    private var cachedLang = ""

    fun getAllRecipes(category: DexCategory): List<RecipeHandle> {
        val version = SpawnDataIndex.dataVersion
        val lang = currentLanguage()
        if (version != cachedVersion || lang != cachedLang) {
            synchronized(this) {
                if (version != cachedVersion || lang != cachedLang) {
                    cache.clear()
                    cachedVersion = version
                    cachedLang = lang
                }
            }
        }
        return cache.computeIfAbsent(category.id) { category.buildAllRecipes() }
    }

    fun invalidate() {
        cache.clear()
        cachedVersion = -1L
        cachedLang = ""
    }

    private fun currentLanguage(): String = try {
        Minecraft.getInstance().languageManager.selected
    } catch (_: Exception) {
        "en_us"
    }
}