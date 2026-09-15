package com.cobbledex

import com.cobbledex.config.CobbleDexConfig
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

    /**
     * Pre-computes one not-yet-cached category's bounds per call. Driven off the client tick after
     * data + the sprite atlas are ready, it spreads the one-time `buildAllRecipes()` measurement cost
     * (otherwise paid as a visible hitch the first time each category is opened in REI/JEI/EMI) over a
     * handful of ticks during idle time instead. No-op once every enabled category is warm.
     */
    fun warmOneCategory() {
        if (!SpawnDataIndex.hasData()) return
        val config = try { CobbleDexConfig.get() } catch (_: Exception) { return }
        val next = DexCategory.ALL.firstOrNull { it.isEnabled(config) && getBoundsIfCached(it.id) == null }
            ?: return
        try { getBounds(next) } catch (_: Exception) {}
    }

    private fun getBoundsIfCached(id: String): PanelSize? {
        if (SpawnDataIndex.dataVersion != cachedVersion) return null
        return cache[id]
    }

    fun invalidateCache() {
        cache.clear()
        cachedVersion = -1L
        cachedLang = ""
    }

    /**
     * How many of a category's largest-by-[RecipeHandle.sizeHint] candidates get actually measured
     * (real layout build) instead of relying on the hint alone. A category like Spawn or Evolution
     * with 1000+ handles and no explicit `_width`/`_height` used to force a full layout build of
     * *every* handle just to find the tallest/widest one - this bounds that to a handful of the
     * likeliest outliers instead. sizeHint is a proxy (condition/method/route counts), not a render
     * measurement, so it can occasionally pick the "wrong" top handle - but that just means this
     * measures a slightly-too-small candidate, never an under-measured panel, since a hint of 0 on
     * every handle (a category that doesn't set it) falls back to the full scan below unchanged.
     */
    private const val SIZE_CANDIDATES = 24

    private fun computeBounds(category: DexCategory): PanelSize {
        // Shared with CobbleDexJEIPlugin.registerRecipes() - JEI calls getWidth()/getHeight() (which
        // resolve to this) on every category during its own registerCategories() sanity check, before
        // registerRecipes() builds the same category's recipes again a moment later. Without sharing
        // this build, the whole category gets built twice, back to back, on every world join.
        val recipes = try { RecipeBuildCache.getOrBuild(category) } catch (_: Exception) { emptyList() }
        if (recipes.isEmpty()) return PanelSize(200, 100)
        val candidates = if (recipes.size > SIZE_CANDIDATES && recipes.any { it.sizeHint > 0 })
            recipes.sortedByDescending { it.sizeHint }.take(SIZE_CANDIDATES)
        else recipes
        var maxW = PanelLayout.MIN_WIDTH
        var maxH = 80
        // This result is cached per category+dataVersion+language (getBounds
        // above), so it only runs once per data load/reload - not worth an
        // early-exit shortcut that can under-measure the panel when an
        // outlier (e.g. a species with a very long evolution requirement
        // list, or many "notable differences" bullet points) happens to sit
        // outside whatever sample window a shortcut would have checked.
        // Scanning every recipe here is what fixed text overflowing the
        // Evolution and Alternate Forms panels - sizeHint-based sampling
        // (above) is what keeps that scan cheap for categories that opt in,
        // without reintroducing that bug.
        for (handle in candidates) {
            try {
                val w = handle.width
                val h = handle.height
                if (w > maxW) maxW = w
                if (h > maxH) maxH = h
                // The category frame is hard-capped at MAX_HEIGHT below, so a recipe taller than
                // that renders with its bottom silently cut off by the viewer - no scroll region
                // exists to show the rest (see audits/PANEL_SIZING_V2_ARCHITECTURE.md, Hole 3).
                // Per-section content budgets keep this rare; when it does happen, log it once so
                // the clipping page is discoverable instead of just "looks cut off" with no trace.
                if (h > PanelLayout.MAX_HEIGHT) {
                    DebugLog.warnOnce("panel-overflow-${category.id}-${handle.recipeIdPath}") {
                        "${category.id}/${handle.recipeIdPath} is ${h}px tall, past the ${PanelLayout.MAX_HEIGHT}px " +
                        "category cap - its bottom will render clipped"
                    }
                }
            } catch (_: Exception) {}
        }
        return PanelSize(
            maxW.coerceIn(PanelLayout.MIN_WIDTH, PanelLayout.MAX_WIDTH),
            maxH.coerceAtMost(PanelLayout.MAX_HEIGHT)
        )
    }
}
