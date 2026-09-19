package com.cobbledex

/**
 * The one home for "split this content so no single page exceeds [PanelLayout.MAX_HEIGHT]" - every
 * category or viewer that needs that job should extend or call into this file rather than writing a
 * second bin-packer. There are two entry points because the job happens at two different times:
 *
 *  - [MeasuredPagePlanner]: paginates a *list of source items* before any [PanelLayout] exists, using
 *    caller-supplied height estimates (e.g. Obtainment routes, drop entries, move rows). This is the
 *    cheap, preferred path - it runs once per category build and produces right-sized [RecipeHandle]s
 *    from the start.
 *  - [RecipeHandle.paginate] / [RecipeHandle.contentFor]: splits an *already-built* [PanelLayout] by
 *    its real rendered geometry. This is the fallback for panels that don't pre-paginate their source
 *    items (Spawn, Herds) or whose estimate turned out to undercount - it's more precise (it cuts at
 *    exact pixel boundaries between elements) but only runs after the expensive layout build, and
 *    only in [RecipeBuildCache]'s callers (REI/JEI/EMI registration), not at data-build time.
 *
 * If you're adding a new tall/variable-length panel, prefer pre-paginating its source list with
 * [MeasuredPagePlanner] (see [RecipeBuilder.buildUnifiedObtainmentPages] or
 * `RecipeBuilder.paginateDrops` for examples) - only fall back to relying on [RecipeHandle.paginate]
 * alone for panels with no natural "list of items" to split (a single Pokémon's whole spawn entry).
 */
data class MeasuredPage<T>(val items: List<T>, val height: Int)

object MeasuredPagePlanner {
    fun <T> paginate(
        items: List<T>,
        maxHeight: Int = PanelLayout.MAX_HEIGHT,
        fixedHeight: Int = 0,
        spacingHeight: Int = 0,
        measureItemHeight: (item: T, precedingOnPage: T?) -> Int,
    ): List<List<T>> = paginateMeasured(
        items = items,
        maxHeight = maxHeight,
        fixedHeight = fixedHeight,
        spacingHeight = spacingHeight,
        measureItemHeight = measureItemHeight,
    ).map { it.items }

    /**
     * [measureItemHeight] receives the item immediately before it *on the same page* (`null` for the
     * first item of any page, including right after a page break) - not just the previous item in
     * [items] - so a caller whose per-item cost depends on "does this repeat a group header at the
     * top of a new page" (see `RecipeBuilder.buildMovesPages`) can express that exactly, without the
     * planner needing to know what a "group" is.
     */
    fun <T> paginateMeasured(
        items: List<T>,
        maxHeight: Int = PanelLayout.MAX_HEIGHT,
        fixedHeight: Int = 0,
        spacingHeight: Int = 0,
        measureItemHeight: (item: T, precedingOnPage: T?) -> Int,
    ): List<MeasuredPage<T>> {
        if (items.isEmpty()) return emptyList()

        val pages = mutableListOf<MeasuredPage<T>>()
        var currentItems = mutableListOf<T>()
        var currentHeight = fixedHeight.coerceAtLeast(0)

        for (item in items) {
            val preceding = currentItems.lastOrNull()
            val itemHeight = measureItemHeight(item, preceding).coerceAtLeast(0)
            val spacing = if (preceding == null) 0 else spacingHeight.coerceAtLeast(0)
            val candidateHeight = currentHeight + spacing + itemHeight

            if (currentItems.isNotEmpty() && candidateHeight > maxHeight) {
                pages.add(MeasuredPage(currentItems.toList(), currentHeight))
                currentItems = mutableListOf(item)
                // Re-measured as the first item of the new page - its cost can differ from the
                // mid-page candidate just rejected (e.g. it now pays a repeated group-header cost).
                currentHeight = fixedHeight.coerceAtLeast(0) + measureItemHeight(item, null).coerceAtLeast(0)
            } else {
                currentItems.add(item)
                currentHeight = candidateHeight
            }
        }

        if (currentItems.isNotEmpty()) {
            pages.add(MeasuredPage(currentItems.toList(), currentHeight))
        }

        return pages
    }
}

// ---------------------------------------------------------------------------------------------
// Post-layout fallback: pages an already-built PanelLayout by its real rendered geometry. See the
// file-level doc above for when to prefer this over MeasuredPagePlanner.
// ---------------------------------------------------------------------------------------------

data class PanelPage(val top: Int, val bottom: Int, val shift: Int, val height: Int, val index: Int) {
    operator fun contains(y: Int) = y >= top && y < bottom
}

/** One paginated recipe/display: a page of a [handle], ready for any viewer to wrap and register. */
data class Paged(val handle: RecipeHandle, val page: PanelPage) {
    /** `0` keeps the bare id so existing favorites/bookmarks keep resolving after a reload. */
    val id: String get() = handle.recipeIdPath + if (page.index > 0) "/part_${page.index + 1}" else ""
}

/** The subset of a [RecipeHandle]'s positioned content that falls inside one [PanelPage], shifted
 *  so it renders at the top of that page instead of at its original panel offset. */
data class PagedContent(
    val pokemonSlots: List<PokemonSlotDef>,
    val itemSlots: List<ItemSlotDef>,
    val moveLinks: List<MoveLinkDef>,
    val categoryLinks: List<CategoryLinkDef>,
    val tooltipZones: List<PanelLayout.TooltipZone>,
)

/** Splits [handles] into pages, one entry per rendered page instead of one per handle. */
fun List<RecipeHandle>.paged(budget: Int = PanelLayout.MAX_HEIGHT): List<Paged> =
    flatMap { handle -> handle.paginate(budget).map { Paged(handle, it) } }

/**
 * Splits this handle's [PanelLayout] into vertical pages no taller than [budget], cutting only
 * between elements - never through a line of text, a slot, a link or a tooltip zone. Returns a
 * single full-height page (no allocation cost beyond the one entry) when the panel already fits.
 */
fun RecipeHandle.paginate(budget: Int = PanelLayout.MAX_HEIGHT): List<PanelPage> {
    val height = this.height
    if (height <= budget) return listOf(PanelPage(0, height, 0, height, 0))

    // Every positioned thing on the panel is uncuttable - not just text/fill (occupiedSpans), but
    // slots and the links/tooltip zones overlaid on them too. Blocking on all of them (rather than
    // relying on links/tooltips happening to coincide with a text row) is what keeps a cut from ever
    // landing inside a clickable region or a hover zone.
    val blocked = BooleanArray(height + 1)
    fun block(span: IntRange) {
        for (y in maxOf(span.first + 1, 0)..minOf(span.last, height)) blocked[y] = true
    }
    for (span in layout.occupiedSpans()) block(span)
    for (slot in slots.pokemon) block(slot.y until slot.y + PanelLayout.SLOT_SIZE)
    for (slot in slots.items) block(slot.y until slot.y + slot.size)
    for (link in moveLinks) block(link.y until link.y + link.height)
    for (link in slots.categoryLinks) block(link.y until link.y + link.height)
    for (zone in layout.tooltipZones) block(zone.y until zone.y + zone.height)

    val pages = mutableListOf<PanelPage>()
    var top = 0
    var lead = 0
    var index = 0
    while (height - top + lead > budget) {
        val limit = top + budget - lead
        // No safe cut in range (one element alone is taller than budget) - cut through it rather
        // than looping forever; better to show almost everything once than nothing at all.
        val cut = (limit downTo top + 1).firstOrNull { !blocked[it] } ?: run {
            DebugLog.warnOnce("panel-unsliceable-$recipeIdPath-$top") {
                "$recipeIdPath has an element taller than the ${budget}px page budget near y=$top - cutting through it"
            }
            limit
        }
        // Consecutive pages never share a page, so fill it instead of showing the viewer's background.
        pages += PanelPage(top, cut, top - lead, budget, index)
        top = cut
        lead = PanelLayout.PADDING
        index++
    }
    pages += PanelPage(top, height, top - lead, height - top + lead, index)
    return pages
}

/** This handle's slots/links/tooltip zones that fall on [page], shifted to render at its top. */
fun RecipeHandle.contentFor(page: PanelPage): PagedContent = PagedContent(
    pokemonSlots = slots.pokemon.filter { it.y in page }.map { it.shiftedBy(page.shift) },
    itemSlots = slots.items.filter { it.y in page }.map { it.shiftedBy(page.shift) },
    moveLinks = moveLinks.filter { it.y in page }.map { it.shiftedBy(page.shift) },
    categoryLinks = slots.categoryLinks.filter { it.y in page }.map { it.shiftedBy(page.shift) },
    tooltipZones = layout.tooltipZones.filter { it.y in page }.map { it.shiftedBy(page.shift) },
)

private fun PokemonSlotDef.shiftedBy(dy: Int): PokemonSlotDef = if (dy == 0) this else copy(y = y - dy)
private fun ItemSlotDef.shiftedBy(dy: Int): ItemSlotDef = if (dy == 0) this else copy(y = y - dy)
private fun MoveLinkDef.shiftedBy(dy: Int): MoveLinkDef = if (dy == 0) this else copy(y = y - dy)
private fun CategoryLinkDef.shiftedBy(dy: Int): CategoryLinkDef = if (dy == 0) this else copy(y = y - dy)
private fun PanelLayout.TooltipZone.shiftedBy(dy: Int): PanelLayout.TooltipZone =
    if (dy == 0) this else copy(y = y - dy)
