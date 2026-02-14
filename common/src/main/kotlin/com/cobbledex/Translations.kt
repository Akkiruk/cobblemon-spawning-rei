package com.cobbledex

/**
 * Localization helper. All user-facing strings go through [tr].
 * Falls back to the raw key if I18n is unavailable (e.g. accidentally loaded on a dedicated server).
 */
fun tr(key: String, vararg args: Any): String = try {
    ClientI18n.get(key, *args)
} catch (_: NoClassDefFoundError) { key }

private object ClientI18n {
    fun get(key: String, vararg args: Any): String =
        net.minecraft.client.resources.language.I18n.get(key, *args)
}

// --- Shared text producers (single source of truth for measurement + rendering) ---

fun levelText(levelRange: String): String = tr("cobbledex-rei-emi-jei.spawn.level", levelRange)

fun weightText(weight: Float): String = tr("cobbledex-rei-emi-jei.spawn.weight", SpawnDisplayHelper.formatWeight(weight))

fun obtainmentUseText(block: String): String = tr("cobbledex-rei-emi-jei.obtainment.use", block)

fun obtainmentStructureText(structure: String): String = tr("cobbledex-rei-emi-jei.obtainment.structure", structure)

fun obtainmentDimensionText(dimension: String): String = tr("cobbledex-rei-emi-jei.obtainment.dimension", dimension)

fun evoBranchText(index: Int, total: Int): String = tr("cobbledex-rei-emi-jei.evo.branch", index, total)

fun sourceLabel(source: String): String = when (source) {
    "bundled" -> tr("cobbledex-rei-emi-jei.source.builtin")
    "datapack" -> tr("cobbledex-rei-emi-jei.source.datapack")
    "mod" -> tr("cobbledex-rei-emi-jei.source.mod")
    else -> ""
}
