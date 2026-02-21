package com.cobbledex

object TmItemUtils {

    private val TM_PREFIXES = listOf("tmcraft:tm_", "tmcraft:tutor_", "tmcraft:egg_", "tmcraft:star_")

    fun extractMove(itemId: String): String? {
        for (prefix in TM_PREFIXES) {
            if (itemId.startsWith(prefix)) return itemId.removePrefix(prefix)
        }
        return null
    }

    fun isTmItem(itemId: String): Boolean = TM_PREFIXES.any { itemId.startsWith(it) }

    fun tmItemId(moveName: String): String = "tmcraft:tm_${moveName.lowercase()}"
}
