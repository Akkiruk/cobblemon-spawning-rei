package com.cobblemonrei

data class ObtainmentInfo(
    val pokemon: String,
    val formAspects: String = "",
    val method: String,
    val description: String,
    val items: List<String> = emptyList(),
    val block: String? = null,
    val structure: String? = null,
    val dimension: String? = null,
    val notes: List<String> = emptyList(),
    val source: String = "datapack"
) {
    val displayMethodName: String
        get() = when (method.lowercase()) {
            "altar" -> "\u2726 Altar Summoning"
            "shrine" -> "\u2726 Shrine Ritual"
            "resurrection" -> "\u2699 Resurrection Machine"
            "transformation" -> "\u2605 Transformation"
            "event" -> "\u2605 Special Event"
            "quest" -> "\u2605 Quest Reward"
            "npc" -> "\u2605 NPC Trade"
            "raid" -> "\u2694 Raid Battle"
            "gift" -> "\u2605 Gift"
            else -> "\u2605 ${titleCase(method)}"
        }

    val displayDescription: String
        get() = description.ifBlank { displayMethodName }

    val displayItems: List<String>
        get() = items.map { formatItemId(it) }

    val displayBlock: String?
        get() = block?.let { formatItemId(it) }

    val displayStructure: String?
        get() = structure?.let { formatId(it) }

    val displayDimension: String?
        get() = dimension?.let { SpawnDisplayHelper.formatDimension(it) }

    private fun formatItemId(id: String): String {
        val name = if (id.contains(":")) id.substringAfter(":") else id
        return name.replace("_", " ")
            .split(" ")
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
    }
}
