package com.cobbledex

data class MeasuredPage<T>(
    val items: List<T>,
    val height: Int,
)

object MeasuredPagePlanner {
    fun <T> paginate(
        items: List<T>,
        maxHeight: Int = PanelLayout.MAX_HEIGHT,
        fixedHeight: Int = 0,
        spacingHeight: Int = 0,
        measureItemHeight: (T) -> Int,
    ): List<List<T>> = paginateMeasured(
        items = items,
        maxHeight = maxHeight,
        fixedHeight = fixedHeight,
        spacingHeight = spacingHeight,
        measureItemHeight = measureItemHeight,
    ).map { it.items }

    fun <T> paginateMeasured(
        items: List<T>,
        maxHeight: Int = PanelLayout.MAX_HEIGHT,
        fixedHeight: Int = 0,
        spacingHeight: Int = 0,
        measureItemHeight: (T) -> Int,
    ): List<MeasuredPage<T>> {
        if (items.isEmpty()) return emptyList()

        val pages = mutableListOf<MeasuredPage<T>>()
        var currentItems = mutableListOf<T>()
        var currentHeight = fixedHeight.coerceAtLeast(0)

        for (item in items) {
            val itemHeight = measureItemHeight(item).coerceAtLeast(0)
            val spacing = if (currentItems.isEmpty()) 0 else spacingHeight.coerceAtLeast(0)
            val candidateHeight = currentHeight + spacing + itemHeight

            if (currentItems.isNotEmpty() && candidateHeight > maxHeight) {
                pages.add(MeasuredPage(currentItems.toList(), currentHeight))
                currentItems = mutableListOf(item)
                currentHeight = fixedHeight.coerceAtLeast(0) + itemHeight
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