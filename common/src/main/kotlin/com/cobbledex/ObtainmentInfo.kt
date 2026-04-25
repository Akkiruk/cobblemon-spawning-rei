package com.cobbledex

data class ObtainmentInfo(
    val pokemon: String,
    val formAspects: String = "",
    val method: String,
    val description: String,
    val descriptionKey: String? = null,
    val items: List<String> = emptyList(),
    val block: String? = null,
    val structure: String? = null,
    val dimension: String? = null,
    val notes: List<String> = emptyList(),
    val noteKeys: List<String> = emptyList(),
    val source: String = "datapack"
) {
    val displayMethodName: String
        get() = when (method.lowercase()) {
            "altar" -> tr("cobbledex-rei-emi-jei.obtainment.method.altar")
            "shrine" -> tr("cobbledex-rei-emi-jei.obtainment.method.shrine")
            "resurrection" -> tr("cobbledex-rei-emi-jei.obtainment.method.resurrection")
            "transformation" -> tr("cobbledex-rei-emi-jei.obtainment.method.transformation")
            "event" -> tr("cobbledex-rei-emi-jei.obtainment.method.event")
            "quest" -> tr("cobbledex-rei-emi-jei.obtainment.method.quest")
            "npc" -> tr("cobbledex-rei-emi-jei.obtainment.method.npc")
            "raid" -> tr("cobbledex-rei-emi-jei.obtainment.method.raid")
            "gift" -> tr("cobbledex-rei-emi-jei.obtainment.method.gift")
            else -> tr("cobbledex-rei-emi-jei.obtainment.method.other", titleCase(method))
        }

    val displayDescription: String
        get() = descriptionKey?.let { tr(it) } ?: description.ifBlank { displayMethodName }

    val displayNotes: List<String>
        get() = if (noteKeys.isNotEmpty()) noteKeys.map { tr(it) } else notes

    val displayItems: List<String>
        get() = items.map { SpawnDisplayHelper.resolveItemName(it) }

    val displayBlock: String?
        get() = block?.let { SpawnDisplayHelper.resolveItemName(it) }

    val displayStructure: String?
        get() = structure?.let { formatId(it) }

    val displayDimension: String?
        get() = dimension?.let { SpawnDisplayHelper.formatDimension(it) }
}
