package com.cobbledex

/**
 * Cobblemon's rarity/category labels - from a species JSON's "labels" array - that are worth
 * calling out to a player, as opposed to the many other labels species carry (`gen9`, `regional`,
 * form markers, etc.). Shared so the tooltip badge ([SpawnDisplayHelper]) and JEI's search alias
 * list ([DiscoveryAliases]) agree on exactly which labels count as "rarity".
 */
val RARITY_LABELS = setOf("legendary", "mythical", "ultra_beast", "paradox")

/**
 * The four vanilla regional-variant tags, keyed however a caller already has the form (its
 * Cobblemon `labels` set, which uses the `_form` suffix, or its `aspects` set, which doesn't).
 * Cobblemon's `FormData.labels` can drift from the correct value at runtime for reasons outside
 * any mod's own JSON (see [EvolutionDataLoader.REGIONAL_ASPECT_TO_SUFFIX]'s Farfetch'd note), so a
 * caller that has both should check aspects first.
 */
object RegionalForms {
    private val BY_LABEL = mapOf(
        "alolan_form" to "alolan",
        "galarian_form" to "galarian",
        "hisuian_form" to "hisuian",
        "paldean_form" to "paldean",
    )
    private val BY_ASPECT = mapOf(
        "alolan" to "alolan",
        "galarian" to "galarian",
        "hisuian" to "hisuian",
        "paldean" to "paldean",
    )

    fun suffixForLabel(label: String): String? = BY_LABEL[label]
    fun suffixForAspect(aspect: String): String? = BY_ASPECT[aspect]

    /** [aspects] checked first (see class doc), falling back to [labels]. */
    fun suffixFor(aspects: Collection<String>, labels: Collection<String>): String? =
        aspects.firstNotNullOfOrNull { BY_ASPECT[it] } ?: labels.firstNotNullOfOrNull { BY_LABEL[it] }
}
