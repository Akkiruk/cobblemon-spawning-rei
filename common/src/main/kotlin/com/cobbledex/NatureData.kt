package com.cobbledex

data class NatureInfo(
    val name: String,
    val increasedStat: String?,
    val decreasedStat: String?
) {
    val isNeutral: Boolean get() = increasedStat == null
}

object NatureData {

    private val STAT_NAME_KEYS = mapOf(
        "atk" to "cobbledex-rei-emi-jei.stat.attack",
        "def" to "cobbledex-rei-emi-jei.stat.defense",
        "spa" to "cobbledex-rei-emi-jei.stat.sp_atk",
        "spd" to "cobbledex-rei-emi-jei.stat.sp_def",
        "spe" to "cobbledex-rei-emi-jei.stat.speed"
    )

    fun statName(statId: String): String {
        val key = STAT_NAME_KEYS[statId] ?: return statId
        return tr(key)
    }

    private val NATURE_NAME_KEYS = mapOf(
        "Hardy" to "cobbledex-rei-emi-jei.nature.hardy",
        "Lonely" to "cobbledex-rei-emi-jei.nature.lonely",
        "Brave" to "cobbledex-rei-emi-jei.nature.brave",
        "Adamant" to "cobbledex-rei-emi-jei.nature.adamant",
        "Naughty" to "cobbledex-rei-emi-jei.nature.naughty",
        "Bold" to "cobbledex-rei-emi-jei.nature.bold",
        "Docile" to "cobbledex-rei-emi-jei.nature.docile",
        "Relaxed" to "cobbledex-rei-emi-jei.nature.relaxed",
        "Impish" to "cobbledex-rei-emi-jei.nature.impish",
        "Lax" to "cobbledex-rei-emi-jei.nature.lax",
        "Timid" to "cobbledex-rei-emi-jei.nature.timid",
        "Hasty" to "cobbledex-rei-emi-jei.nature.hasty",
        "Serious" to "cobbledex-rei-emi-jei.nature.serious",
        "Jolly" to "cobbledex-rei-emi-jei.nature.jolly",
        "Naive" to "cobbledex-rei-emi-jei.nature.naive",
        "Modest" to "cobbledex-rei-emi-jei.nature.modest",
        "Mild" to "cobbledex-rei-emi-jei.nature.mild",
        "Quiet" to "cobbledex-rei-emi-jei.nature.quiet",
        "Bashful" to "cobbledex-rei-emi-jei.nature.bashful",
        "Rash" to "cobbledex-rei-emi-jei.nature.rash",
        "Calm" to "cobbledex-rei-emi-jei.nature.calm",
        "Gentle" to "cobbledex-rei-emi-jei.nature.gentle",
        "Sassy" to "cobbledex-rei-emi-jei.nature.sassy",
        "Careful" to "cobbledex-rei-emi-jei.nature.careful",
        "Quirky" to "cobbledex-rei-emi-jei.nature.quirky"
    )

    fun natureName(rawName: String): String {
        val key = NATURE_NAME_KEYS[rawName] ?: return rawName
        return tr(key)
    }

    val NATURES: List<NatureInfo> = listOf(
        NatureInfo("Hardy", null, null),
        NatureInfo("Lonely", "atk", "def"),
        NatureInfo("Brave", "atk", "spe"),
        NatureInfo("Adamant", "atk", "spa"),
        NatureInfo("Naughty", "atk", "spd"),

        NatureInfo("Bold", "def", "atk"),
        NatureInfo("Docile", null, null),
        NatureInfo("Relaxed", "def", "spe"),
        NatureInfo("Impish", "def", "spa"),
        NatureInfo("Lax", "def", "spd"),

        NatureInfo("Timid", "spe", "atk"),
        NatureInfo("Hasty", "spe", "def"),
        NatureInfo("Serious", null, null),
        NatureInfo("Jolly", "spe", "spa"),
        NatureInfo("Naive", "spe", "spd"),

        NatureInfo("Modest", "spa", "atk"),
        NatureInfo("Mild", "spa", "def"),
        NatureInfo("Quiet", "spa", "spe"),
        NatureInfo("Bashful", null, null),
        NatureInfo("Rash", "spa", "spd"),

        NatureInfo("Calm", "spd", "atk"),
        NatureInfo("Gentle", "spd", "def"),
        NatureInfo("Sassy", "spd", "spe"),
        NatureInfo("Careful", "spd", "spa"),
        NatureInfo("Quirky", null, null)
    )
}
