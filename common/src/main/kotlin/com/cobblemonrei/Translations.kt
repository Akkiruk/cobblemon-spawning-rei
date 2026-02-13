package com.cobblemonrei

import net.minecraft.client.resources.language.I18n

/**
 * Localization helper. All user-facing strings go through [tr] so they
 * can be overridden via resource-pack lang files.
 */
fun tr(key: String, vararg args: Any): String = I18n.get(key, *args)

// --- Shared text producers (single source of truth for measurement + rendering) ---

fun levelText(levelRange: String): String = tr("cobblemon-spawning-rei.spawn.level", levelRange)

fun weightText(weight: Float): String = tr("cobblemon-spawning-rei.spawn.weight", SpawnDisplayHelper.formatWeight(weight))

fun obtainmentUseText(block: String): String = tr("cobblemon-spawning-rei.obtainment.use", block)

fun obtainmentStructureText(structure: String): String = tr("cobblemon-spawning-rei.obtainment.structure", structure)

fun obtainmentDimensionText(dimension: String): String = tr("cobblemon-spawning-rei.obtainment.dimension", dimension)

fun evoBranchText(index: Int, total: Int): String = tr("cobblemon-spawning-rei.evo.branch", index, total)

fun sourceLabel(source: String): String = when (source) {
    "bundled" -> tr("cobblemon-spawning-rei.source.builtin")
    "datapack" -> tr("cobblemon-spawning-rei.source.datapack")
    "mod" -> tr("cobblemon-spawning-rei.source.mod")
    else -> ""
}
