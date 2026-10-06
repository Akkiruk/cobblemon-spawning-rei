package com.cobbledex

import com.cobbledex.config.CobbleDexConfig
import com.cobbledex.platform.PlatformHelper
import net.minecraft.client.Minecraft

object CategorySizer {

    data class PanelSize(val width: Int, val height: Int)

    /** Sizes are valid for one data version in one language (panel text is measured). */
    private data class Epoch(val dataVersion: Long, val lang: String)

    private val cache = EpochCache<Epoch, PanelSize>()

    private fun currentEpoch(): Epoch {
        val lang = try { Minecraft.getInstance().languageManager.selected } catch (_: Exception) { "en_us" }
        return Epoch(SpawnDataIndex.dataVersion, lang)
    }

    fun getBounds(category: DexCategory): PanelSize =
        cache.get(currentEpoch(), category.id) { computeBounds(category) }

    /**
     * Pre-computes one not-yet-cached category's bounds per call. Driven off the client tick after
     * data + the sprite atlas are ready, it spreads the one-time `buildAllRecipes()` measurement cost
     * (otherwise paid as a visible hitch the first time each category is opened in REI/JEI/EMI) over a
     * handful of ticks during idle time instead. No-op once every enabled category is warm.
     */
    fun warmOneCategory() {
        if (!sizesAreUsed) return
        if (!SpawnDataIndex.hasData()) return
        val config = try { CobbleDexConfig.get() } catch (_: Exception) { return }
        // Skips a category another thread is already building rather than waiting on it - this runs
        // on the game thread, and that build (EMI registering alongside REI, say) is about to make
        // the result available anyway. Whatever is left is picked up on a later tick.
        val next = DexCategory.ALL.firstOrNull {
            it.isEnabled(config) && getBoundsIfCached(it.id) == null &&
                !RecipeBuildCache.isBuilding(it) && !cache.isComputing(currentEpoch(), it.id)
        } ?: return
        try { getBounds(next) } catch (_: Exception) {}
    }

    /**
     * Only REI and JEI size their categories from these bounds (EMI sizes each recipe itself, and
     * the JEI plugin stays idle when EMI is installed), so warming them for anyone else would just
     * be a full build of every category on the game thread that nothing reads.
     */
    private val sizesAreUsed: Boolean by lazy {
        try {
            PlatformHelper.isModLoaded("roughlyenoughitems") ||
                (PlatformHelper.isModLoaded("jei") && !PlatformHelper.isModLoaded("emi"))
        } catch (_: Throwable) { true }
    }

    private fun getBoundsIfCached(id: String): PanelSize? = cache.peek(currentEpoch(), id)

    fun invalidateCache() = cache.clear()

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
                // A handle taller than MAX_HEIGHT no longer clips: RecipeBuildCache's callers all
                // page a too-tall handle into multiple registered recipes/displays (see
                // RecipeHandle.paginate), each capped at MAX_HEIGHT - so the tallest *page* any
                // viewer ever actually registers is this cap, not the handle's raw height.
                val h = minOf(handle.height, PanelLayout.MAX_HEIGHT)
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
