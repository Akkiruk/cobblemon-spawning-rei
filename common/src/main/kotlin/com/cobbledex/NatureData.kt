package com.cobbledex

data class NatureInfo(
    val name: String,
    val increasedStat: String?,
    val decreasedStat: String?
) {
    val isNeutral: Boolean get() = increasedStat == null
}

object NatureData {

    val STAT_NAMES = mapOf(
        "atk" to "Attack",
        "def" to "Defense",
        "spa" to "Sp. Atk",
        "spd" to "Sp. Def",
        "spe" to "Speed"
    )

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
