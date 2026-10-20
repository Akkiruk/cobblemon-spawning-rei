package com.cobbledex

/**
 * Localization helper. All user-facing strings go through [tr].
 * Falls back to the raw key if I18n is unavailable (e.g. accidentally loaded on a dedicated server).
 */
fun tr(key: String, vararg args: Any): String = try {
    ClientI18n.get(key, *args)
} catch (_: NoClassDefFoundError) { key }

private object ClientI18n {
    /**
     * A key with no translation comes back as the key itself, untouched. Minecraft's own lookup would
     * run that missing key through `String.format`, so a key built from a form name like Zygarde's
     * "10%" returns "Format error: ..." - which no caller's "did it translate?" check
     * (`tr(key) == key`) recognises as untranslated, and it ended up shown as the form's name.
     */
    fun get(key: String, vararg args: Any): String =
        if (net.minecraft.client.resources.language.I18n.exists(key)) {
            net.minecraft.client.resources.language.I18n.get(key, *args)
        } else {
            key
        }
}

// --- Shared text producers (single source of truth for measurement + rendering) ---

fun levelText(levelRange: String): String = tr("cobbledex-rei-emi-jei.spawn.level", levelRange)

fun obtainmentUseText(block: String): String = tr("cobbledex-rei-emi-jei.obtainment.use", block)

fun obtainmentStructureText(structure: String): String = tr("cobbledex-rei-emi-jei.obtainment.structure", structure)

fun obtainmentDimensionText(dimension: String): String = tr("cobbledex-rei-emi-jei.obtainment.dimension", dimension)

fun evoBranchText(index: Int, total: Int): String = tr("cobbledex-rei-emi-jei.evo.branch", index, total)
