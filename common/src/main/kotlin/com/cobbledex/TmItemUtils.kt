package com.cobbledex

object TmItemUtils {

    private val movePrefixes = listOf(
        "tmcraft:tm_",
        "tmcraft:tutor_",
        "tmcraft:egg_",
        "tmcraft:star_",
        "simpletms:tm_",
    )

    private val standardTmPrefixes = listOf(
        "tmcraft:tm_",
        "simpletms:tm_",
    )

    fun extractMove(itemId: String): String? {
        for (prefix in movePrefixes) {
            if (itemId.startsWith(prefix)) return itemId.removePrefix(prefix)
        }
        return null
    }

    fun isTmItem(itemId: String): Boolean = movePrefixes.any { itemId.startsWith(it) }

    fun tmItemIds(moveName: String): List<String> =
        standardTmPrefixes.map { prefix -> "$prefix${moveName.lowercase()}" }
}
