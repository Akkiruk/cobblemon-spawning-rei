package com.cobbledex

/**
 * The vocabulary of evolution requirements: every variant CobbleDex understands, and the data keys
 * each one carries.
 *
 * Requirements reach CobbleDex two ways — typed Cobblemon objects from the runtime API, and raw
 * JSON from this client's files. Both used to enumerate all 26 variants and all their key names
 * independently, so adding a requirement type upstream meant editing two files; miss one and that
 * source silently degraded to `"unknown"` while the other kept working. Since evolutions now come
 * from local files on every dedicated server and from the runtime in singleplayer, that asymmetry
 * showed up as a requirement rendering differently depending on where you were playing.
 *
 * This is the one place the names live. Both readers reference it, and [VARIANTS] lets a test
 * assert the JSON reader covers everything.
 */
object EvolutionRequirementSpec {

    /** Type of a value carried in a requirement's data map. */
    enum class FieldType { STRING, INT, BOOLEAN }

    data class Field(val key: String, val type: FieldType)

    data class Spec(
        /** Canonical variant name, as written into [EvolutionRequirement.variant]. */
        val variant: String,
        /** Data keys this variant carries. */
        val fields: List<Field>,
        /**
         * Extra variant names the JSON schema also uses for this requirement. Cobblemon accepts
         * several spellings (e.g. both `has_move` and `move_set`) and they must resolve alike.
         */
        val jsonAliases: List<String> = emptyList(),
    )

    private fun str(key: String) = Field(key, FieldType.STRING)
    private fun int(key: String) = Field(key, FieldType.INT)
    private fun bool(key: String) = Field(key, FieldType.BOOLEAN)

    val ALL: List<Spec> = listOf(
        Spec("level", listOf(int("minLevel"))),
        Spec("friendship", listOf(int("amount"))),
        Spec("time_range", listOf(str("range"))),
        Spec("held_item", listOf(str("itemCondition"))),
        Spec("owner_holds_item", listOf(str("itemCondition"))),
        Spec("move_type", listOf(str("type")), jsonAliases = listOf("has_move_type")),
        Spec("move_set", listOf(str("move")), jsonAliases = listOf("has_move")),
        Spec("biome", listOf(str("biomeCondition"), str("biomeAnticondition"))),
        Spec("structure", listOf(str("structureCondition"), str("structureAnticondition"))),
        Spec("stat_compare", listOf(str("highStat"), str("lowStat"))),
        Spec("stat_equal", listOf(str("statOne"), str("statTwo"))),
        Spec("pokemon_properties", listOf(str("target")), jsonAliases = listOf("properties")),
        Spec("property_range", listOf(str("range"), str("feature"))),
        Spec("blocks_traveled", listOf(int("amount"))),
        Spec("use_move", listOf(str("move"), int("amount"))),
        Spec("defeat", listOf(str("target"), int("amount"))),
        Spec("recoil", listOf(int("amount"))),
        Spec("damage_taken", listOf(int("amount"))),
        Spec("battle_critical_hits", listOf(int("amount"))),
        Spec("party_member", listOf(str("target"), bool("contains"))),
        Spec("moon_phase", listOf(str("moonPhase"))),
        Spec("weather", listOf(bool("isRaining"))),
        Spec("advancement", listOf(str("requiredAdvancement"))),
        Spec("world", listOf(str("identifier"))),
        Spec("attack_defence_ratio", listOf(str("ratio"))),
        // "any" carries no fields of its own — both readers unwrap it to its first possibility.
        Spec("any", emptyList()),
    )

    /** Canonical variant names, for coverage assertions. */
    val VARIANTS: List<String> = ALL.map { it.variant }

    private val byName: Map<String, Spec> = buildMap {
        for (spec in ALL) {
            put(spec.variant, spec)
            spec.jsonAliases.forEach { put(it, spec) }
        }
    }

    /** The spec for a variant name or JSON alias, or null when it isn't one CobbleDex knows. */
    fun forVariant(variant: String): Spec? = byName[variant]

    /**
     * Maps any known alias onto its canonical variant name, leaving unknown names untouched.
     *
     * Both readers pass their output through this, so a requirement is named the same whether it
     * arrived as a typed Cobblemon object or as JSON. Cobblemon's own runtime, for instance, names
     * `MoveSetRequirement` "has_move" while its JSON schema writes "move_set"; without this the
     * same requirement carried a different variant depending on whether you were in singleplayer
     * or on a server.
     */
    fun canonicalise(variant: String): String = byName[variant]?.variant ?: variant

    /** Variant name used when nothing else matches. */
    const val UNKNOWN = "unknown"

    /** The name of the requirement whose possibilities are unwrapped rather than rendered. */
    const val ANY = "any"
}
