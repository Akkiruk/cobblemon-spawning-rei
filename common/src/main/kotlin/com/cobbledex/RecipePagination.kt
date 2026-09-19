package com.cobbledex

/**
 * REI, JEI and EMI all model "a recipe" as one fixed-size rectangle with no internal scroll - none
 * of the three viewer frameworks will scroll inside a single recipe/display. A [RecipeHandle] whose
 * content is taller than a viewer's budget has always silently overflowed or clipped past its
 * bounds (see the removed warning this replaced in [CategorySizer]). The fix all three viewers can
 * use is the one thing they already support: multiple registered recipes/displays for the same
 * lookup, paged through with each viewer's existing multi-recipe UI (REI pages displays, JEI scrolls
 * its recipe list, EMI scrolls its recipe list).
 *
 * [RecipeHandle.paginate] and [RecipeHandle.contentFor] are that split, written once here instead of
 * once per viewer plugin. [PanelPage.index] `0` always keeps the handle's own id (see [Paged.id]) so
 * a bookmark/favorite made before a data reload grows a panel from one page to several still
 * resolves to the same first page.
 */
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
