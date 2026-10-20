package com.cobbledex

/**
 * Which of a species' registered forms are worth a page of their own.
 *
 * Cobblemon registers many more [com.cobblemon.mod.common.pokemon.FormData] entries than there are
 * forms a player would look up: a "Normal" copy of the base look, and one reskin per wardrobe
 * costume. The rule is about what a form *is*, not how its numbers compare to the base - Cherrim
 * Sunshine and Morpeko Hangry share their base form's stats, types and abilities and are still
 * forms players search for.
 */
object FormInclusion {

    // A label that marks a form as real even when its aspects say nothing. Labels can be wrong at
    // runtime (the live Eevee Gmax form reports [gen1], not [gen8, gmax]), which is why an aspect
    // alone is also enough - see [isDistinctForm].
    private val SIGNIFICANT_LABELS = setOf(
        "mega", "primal", "ultra_burst", "gmax",
        "alolan_form", "galarian_form", "hisuian_form", "paldean_form",
    )

    // Wardrobe costumes tag their aspect "<name>-costume", while the costume add-on packs use a bare
    // "costume" or "costume2" - Ash's Rowlet ("costume3") carries a stat buff, so it slipped past a
    // suffix-only check and was listed as a real form.
    private val COSTUME_ASPECT = Regex("""costume[0-9]*""", RegexOption.IGNORE_CASE)

    fun isCosmeticAspect(aspect: String): Boolean =
        aspect.endsWith("-costume", ignoreCase = true) || COSTUME_ASPECT.matches(aspect)

    /**
     * @param aspects the form's own aspects. A form with none can't look different from the species
     * (some `species_additions` patches register their own copy of the base "Normal" form, which is
     * what once surfaced a bogus "<species> Normal" entry for most of the dex).
     */
    fun isDistinctForm(name: String, aspects: Collection<String>, labels: Collection<String>): Boolean {
        if (name.isBlank()) return false
        if (aspects.any(::isCosmeticAspect)) return false
        if (labels.any { it in SIGNIFICANT_LABELS }) return true
        return aspects.isNotEmpty()
    }
}
