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
            "altar" -> tr("cobblemon-spawning-rei.obtainment.method.altar")
            "shrine" -> tr("cobblemon-spawning-rei.obtainment.method.shrine")
            "resurrection" -> tr("cobblemon-spawning-rei.obtainment.method.resurrection")
            "transformation" -> tr("cobblemon-spawning-rei.obtainment.method.transformation")
            "event" -> tr("cobblemon-spawning-rei.obtainment.method.event")
            "quest" -> tr("cobblemon-spawning-rei.obtainment.method.quest")
            "npc" -> tr("cobblemon-spawning-rei.obtainment.method.npc")
            "raid" -> tr("cobblemon-spawning-rei.obtainment.method.raid")
            "gift" -> tr("cobblemon-spawning-rei.obtainment.method.gift")
            else -> tr("cobblemon-spawning-rei.obtainment.method.other", titleCase(method))
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
