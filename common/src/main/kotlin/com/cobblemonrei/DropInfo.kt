package com.cobblemonrei

data class DropEntryInfo(
    val itemId: String,
    val percentage: Float,
    val quantity: Int,
    val quantityRange: String? = null
) {
    val displayQuantity: String
        get() = quantityRange ?: quantity.toString()

    val displayPercentage: String
        get() {
            if (percentage >= 100f) return "100%"
            if (percentage == percentage.toLong().toFloat()) return "${percentage.toLong()}%"
            return "%.1f%%".format(percentage)
        }
}

data class DropRecipeData(
    val speciesName: String,
    val drops: List<DropEntryInfo>
)
