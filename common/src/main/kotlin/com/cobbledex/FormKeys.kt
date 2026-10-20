package com.cobbledex

/**
 * The one place that turns "this species' form" into the key every CobbleDex map is filed under.
 *
 * Species info, evolutions, riding and the raw-JSON fallback in [JarDataCache] all have to agree on
 * a form's key or the form's pages fall apart (spawns under one key, stats under another). They
 * used to each carry their own copy of this, which drifted - riding ignored aspects entirely - and
 * all three gave every form with a regional marker the plain regional key, so Marowak's Alolan and
 * Alolan-Mega forms (and Darmanitan's Galarian and Galarian-Zen, Tauros' three Paldean breeds...)
 * landed on the same key and overwrote each other.
 *
 * Scheme: a plain regional form keeps the bare `<species><region>` key, because spawn and evolution
 * data elsewhere refers to it that way. Every other form is `<species>_<form name>`.
 */
object FormKeys {

    private val REGIONAL_LABEL_TO_SUFFIX = mapOf(
        "alolan_form" to "alolan",
        "galarian_form" to "galarian",
        "hisuian_form" to "hisuian",
        "paldean_form" to "paldean",
    )

    // Same suffixes keyed by the form's own aspect, for when a mod's labels are missing or wrong
    // (Farfetch'd Galar reports labels=[gen1, kantonian_form] at runtime; its aspect stays right).
    private val REGIONAL_ASPECT_TO_SUFFIX = mapOf(
        "alolan" to "alolan",
        "galarian" to "galarian",
        "hisuian" to "hisuian",
        "paldean" to "paldean",
    )

    // What a plain regional form is called in its species' JSON ("Alola", "Galar-..." does not count).
    private val PLAIN_REGIONAL_NAMES = setOf(
        "alola", "alolan", "galar", "galarian", "hisui", "hisuian", "paldea", "paldean",
    )

    private val COMPOUND_EXACT = setOf("mega", "mega_x", "mega_y", "primal", "ultra_burst", "gmax", "gigantamax")
    private val COMPOUND_SUFFIXES = listOf("-mode", "-breed", "-forme", "-form", "-style")

    /** An aspect that makes a regional form a different form altogether (see [build]). */
    private fun isCompoundTrait(aspect: String): Boolean =
        aspect in COMPOUND_EXACT || COMPOUND_SUFFIXES.any { aspect.endsWith(it) }

    fun build(
        baseName: String,
        formName: String?,
        labels: Collection<String>,
        aspects: Collection<String>,
    ): String {
        val base = SpeciesNameNormalizer.normalize(baseName)
        val lowerAspects = aspects.map { it.lowercase() }
        val name = nameKey(formName)

        val regionalSuffix = labels.firstNotNullOfOrNull { REGIONAL_LABEL_TO_SUFFIX[it] }
            ?: lowerAspects.firstNotNullOfOrNull { REGIONAL_ASPECT_TO_SUFFIX[it] }
        if (regionalSuffix != null) {
            // A regional form that is also a Mega, a battle mode, a breed... is its own form and must not take the
            // plain regional key. Anything else (the plain form itself, a region "bias" look, Basculin's
            // White-Striped) keeps the bare key it has always had, so favorites and links don't move.
            val hasOtherTrait = lowerAspects.any { isCompoundTrait(it) }
            if (!hasOtherTrait || name in PLAIN_REGIONAL_NAMES) return "$base$regionalSuffix"
        }
        return "${base}_$name"
    }

    /**
     * The alphanumeric part of a form name. A name that is nothing but symbols (Unown's "!" and "?")
     * would strip to nothing and make every such form share one key, so those are spelled out.
     */
    internal fun nameKey(formName: String?): String {
        val lower = formName?.lowercase().orEmpty()
        val stripped = lower.replace(Regex("[^a-z0-9]"), "")
        if (stripped.isNotEmpty()) return stripped
        val spelled = lower
            .replace("!", "exclamation")
            .replace("?", "question")
            .replace(Regex("[^a-z0-9]"), "")
        return spelled.ifEmpty { "form" }
    }
}
