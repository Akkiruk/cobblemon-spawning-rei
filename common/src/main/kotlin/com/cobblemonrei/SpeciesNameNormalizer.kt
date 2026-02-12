package com.cobblemonrei

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
        val lower = name.lowercase()
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
    
    /**
     * Check if two species names refer to the same species.
     */
    fun matches(name1: String, name2: String): Boolean {
        return normalize(name1) == normalize(name2)
    }
}
