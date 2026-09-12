package com.cobbledex

/**
 * Caches each category's [DexCategory.buildAllRecipes] result for the current [SpawnDataIndex.dataVersion].
 *
 * JEI calls [DexCategory.buildAllRecipes] (through [CategorySizer]) once on its own, just to measure
 * a category's max width/height before it'll accept the category at all - IRecipeCategory's default
 * `getWidth()`/`getHeight()` call `getBackground()`, and JEI's own `addRecipeCategories()` calls both
 * as a sanity check, before we ever get to `registerRecipes()`. Without this cache, that's a full,
 * separate rebuild of the category's entire recipe list, thrown away the instant it's measured -
 * then `registerRecipes()` builds the *same* list again from scratch a moment later. Confirmed via
 * JEI's own diagnostic timing (added for issue #43's follow-up): "moves" (11104 recipes on a
 * 1400-species pack) was costing ~1.5s in each of `registerCategories()` and `registerRecipes()` -
 * i.e. the whole build, twice, every world join.
 *
 * Not a general-purpose recipe cache - just a same-version dedupe between whichever caller (JEI's
 * category sizing, JEI's recipe registration, REI/EMI's own registration) asks for a category's full
 * list first. A failed build is not cached, so it can be retried rather than sticking on empty.
 */
object RecipeBuildCache {
    @Volatile private var cachedVersion = -1L
    private val cache = mutableMapOf<String, List<RecipeHandle>>()

    fun getOrBuild(category: DexCategory): List<RecipeHandle> {
        val version = SpawnDataIndex.dataVersion
        if (version != cachedVersion) {
            cache.clear()
            cachedVersion = version
        }
        cache[category.id]?.let { return it }
        val built = category.buildAllRecipes()
        cache[category.id] = built
        return built
    }

    /** Forces the next [getOrBuild] call for any category to rebuild - not currently wired to
     *  anything automatic; the per-version check above already invalidates on a real data change. */
    fun invalidate() {
        cache.clear()
        cachedVersion = -1L
    }
}
