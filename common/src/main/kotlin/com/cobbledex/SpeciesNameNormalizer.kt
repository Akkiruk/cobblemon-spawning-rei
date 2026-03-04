package com.cobbledex

/**
 * Normalizes Pokémon species names for consistent lookup across different data sources.
 * 
 * Cobblemon uses display names like "mr. mime", "farfetch'd", "nidoran-f"
 * Spawn files use simplified names like "mrmime", "farfetchd", "nidoranf"
 * 
 * This normalizer converts both to a canonical form for reliable matching.
 */
object SpeciesNameNormalizer {
    
    // Regex to strip all non-alphanumeric characters except underscores
    private val STRIP_PATTERN = Regex("[^a-z0-9_]")
    
    // Known special mappings where simple stripping isn't enough
    private val CANONICAL_TO_DISPLAY = mapOf(
        "nidoranf" to "nidoran-f",
        "nidoranm" to "nidoran-m",
        "mrmime" to "mr. mime",
        "mimejr" to "mime jr.",
        "mrrime" to "mr. rime",
        "farfetchd" to "farfetch'd",
        "sirfetchd" to "sirfetch'd",
        "porygonz" to "porygon-z",
        "hooh" to "ho-oh",
        "jangmoo" to "jangmo-o",
        "hakamoo" to "hakamo-o",
        "kommoo" to "kommo-o",
        "type_null" to "type: null",
        "tapukoko" to "tapu koko",
        "tapulele" to "tapu lele",
        "tapubulu" to "tapu bulu",
        "tapufini" to "tapu fini",
        "greattusk" to "great tusk",
        "screamtail" to "scream tail",
        "brutebonnet" to "brute bonnet",
        "fluttermane" to "flutter mane",
        "slitherwing" to "slither wing",
        "sandyshocks" to "sandy shocks",
        "irontreads" to "iron treads",
        "ironbundle" to "iron bundle",
        "ironhands" to "iron hands",
        "ironjugulis" to "iron jugulis",
        "ironmoth" to "iron moth",
        "ironthorns" to "iron thorns",
        "roaringmoon" to "roaring moon",
        "ironvaliant" to "iron valiant",
        "walkingwake" to "walking wake",
        "ironleaves" to "iron leaves",
        "gougingfire" to "gouging fire",
        "ragingbolt" to "raging bolt",
        "ironboulder" to "iron boulder",
        "ironcrown" to "iron crown",
        "wochien" to "wo-chien",
        "chienpao" to "chien-pao",
        "tinglu" to "ting-lu",
        "chiyu" to "chi-yu"
    )
    
    // Reverse mapping for display -> canonical
    private val DISPLAY_TO_CANONICAL = CANONICAL_TO_DISPLAY.entries.associate { it.value to it.key }
    
    /**
     * Normalize a species name to canonical form (lowercase, no special chars)
     * Used for all map keys and lookups.
     */
    fun normalize(name: String): String {
        // Strip namespace prefix (e.g. "cobblemon:charizard" -> "charizard")
        val stripped = if (name.contains(':')) name.substringAfter(':') else name
        val lower = stripped.lowercase()
        // First check if this is a known display name
        DISPLAY_TO_CANONICAL[lower]?.let { return it }
        // Otherwise strip special characters
        return lower.replace(STRIP_PATTERN, "")
    }
    
    /**
     * Get the proper display name for a canonical/normalized species name.
     * Falls back to the input if no mapping exists.
     */
    fun toDisplayName(canonicalName: String): String {
        val normalized = normalize(canonicalName)
        return CANONICAL_TO_DISPLAY[normalized] ?: canonicalName
    }

    data class FormDecomposition(
        val baseName: String,
        val regionKey: String?,
        val cobblemonAspects: Set<String>
    )

    private val KNOWN_REGION_SUFFIXES = listOf(
        "alolan" to ("alola" to "alolan"),
        "galarian" to ("galar" to "galarian"),
        "hisuian" to ("hisui" to "hisuian"),
        "paldean" to ("paldea" to "paldean"),
    )

    private val REGION_BIAS_NAMES = mapOf(
        "alola" to ("alola" to "region_bias=alola"),
        "galar" to ("galar" to "region_bias=galar"),
        "hisui" to ("hisui" to "region_bias=hisui"),
        "paldea" to ("paldea" to "region_bias=paldea"),
    )

    fun decomposeFormSpecies(normalizedOrRaw: String): FormDecomposition {
        val normalized = normalize(normalizedOrRaw)
        for ((suffix, pair) in KNOWN_REGION_SUFFIXES) {
            if (normalized.endsWith(suffix) && normalized.length > suffix.length) {
                val base = normalized.removeSuffix(suffix)
                return FormDecomposition(base, pair.first, setOf(pair.second))
            }
        }
        val regionBiasIdx = normalized.indexOf("regionbias")
        if (regionBiasIdx > 0) {
            val base = normalized.substring(0, regionBiasIdx)
            val regionName = normalized.substring(regionBiasIdx + "regionbias".length)
            val pair = REGION_BIAS_NAMES[regionName]
            if (pair != null) {
                return FormDecomposition(base, pair.first, setOf(pair.second))
            }
        }
        return FormDecomposition(normalized, null, emptySet())
    }
}
