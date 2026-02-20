package com.cobbledex

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Discovers Cobbleworkers at runtime via reflection and extracts job rules.
 * Also evaluates species eligibility against received rules.
 */
object JobDataLoader {

    private const val PROVIDER_CLASS = "accieo.cobbleworkers.integration.cobbledex.CobbledexDataProvider"
    private const val PROVIDER_METHOD = "getJobRulesJson"

    private val gson = Gson()
    private val listType = object : TypeToken<List<JobRule>>() {}.type

    /**
     * Attempts to load job rules from Cobbleworkers via reflection.
     * Returns empty list if Cobbleworkers is not installed.
     */
    fun loadFromCobbleworkers(): List<JobRule> {
        return try {
            val clazz = Class.forName(PROVIDER_CLASS)
            val method = clazz.getMethod(PROVIDER_METHOD)
            val json = method.invoke(null) as? String ?: return emptyList()
            val rules: List<JobRule> = gson.fromJson(json, listType)
            DebugLog.info("Loaded ${rules.size} job rules from Cobbleworkers")
            rules
        } catch (_: ClassNotFoundException) {
            // Cobbleworkers not installed — expected
            emptyList()
        } catch (e: Exception) {
            DebugLog.warn("Failed to load Cobbleworkers job rules: ${e.message}")
            emptyList()
        }
    }

    /**
     * Checks if Cobbleworkers is available on this JVM (server or client).
     */
    fun isCobbleworkersPresent(): Boolean {
        return try {
            Class.forName(PROVIDER_CLASS)
            true
        } catch (_: ClassNotFoundException) {
            false
        }
    }

    /**
     * Evaluates which jobs a species qualifies for.
     *
     * @param rules All job rules from Cobbleworkers
     * @param primaryType Primary Pokémon type (UPPERCASE, e.g. "FIRE")
     * @param secondaryType Secondary Pokémon type or null
     * @param abilities All abilities including hidden (lowercase)
     * @param allMoves All known moves across all learn methods (lowercase)
     * @param speciesName The species name (lowercase, e.g. "charizard")
     */
    fun evaluateJobs(
        rules: List<JobRule>,
        primaryType: String?,
        secondaryType: String?,
        abilities: List<String>,
        allMoves: Set<String>,
        speciesName: String,
    ): List<JobMatch> {
        if (rules.isEmpty()) return emptyList()

        val types = buildSet {
            primaryType?.uppercase()?.let { add(it) }
            secondaryType?.uppercase()?.let { add(it) }
        }
        val normalizedSpecies = speciesName.lowercase()
        val normalizedMoves = allMoves.map { it.lowercase() }.toSet()
        val normalizedAbilities = abilities.map { it.lowercase() }

        return rules.mapNotNull { rule ->
            if (!rule.enabled) return@mapNotNull null

            val reasons = mutableListOf<String>()

            // Check hardcoded species (highest specificity)
            if (rule.hardcodedSpeciesEnabled &&
                rule.hardcodedSpecies.any { it.equals(normalizedSpecies, ignoreCase = true) }) {
                reasons += "Species (special)"
            }

            // Check designated species list
            if (rule.designatedSpecies.any { it.equals(normalizedSpecies, ignoreCase = true) && !it.equals("ditto", ignoreCase = true) }) {
                reasons += "Designated species"
            }

            // Check required ability
            if (rule.requiredAbility != null &&
                normalizedAbilities.any { it.equals(rule.requiredAbility, ignoreCase = true) }) {
                reasons += "Has ability: ${rule.requiredAbility}"
            }

            // Check required moves
            val matchingMoves = rule.requiredMoves.filter { reqMove ->
                normalizedMoves.any { it.equals(reqMove, ignoreCase = true) }
            }
            if (matchingMoves.isNotEmpty()) {
                reasons += if (matchingMoves.size == 1) {
                    "Knows move: ${matchingMoves.first()}"
                } else {
                    "Knows moves: ${matchingMoves.joinToString(", ")}"
                }
            }

            // Check type match
            val requiredType = rule.requiredType
            if (requiredType != null && requiredType != "NONE" && requiredType.uppercase() in types) {
                reasons += "${requiredType.lowercase().replaceFirstChar { it.uppercase() }} type"
            }

            // "ditto" in species list = universal type-based wildcard (only applies if type matches)
            // Don't explicitly show this as a reason — the type match already covers it

            if (reasons.isNotEmpty()) JobMatch(rule, reasons) else null
        }
    }

    /**
     * Collects all move names a species knows from SpeciesBasicInfo.
     */
    fun collectAllMoves(info: EvolutionDataLoader.SpeciesBasicInfo): Set<String> {
        val moves = mutableSetOf<String>()
        info.levelUpMoves?.forEach { entry -> entry.moves.forEach { moves += it.name.lowercase() } }
        info.eggMoves?.forEach { moves += it.name.lowercase() }
        info.tutorMoves?.forEach { moves += it.name.lowercase() }
        info.tmMoves?.forEach { moves += it.name.lowercase() }
        return moves
    }

    /**
     * Collects all abilities (regular + hidden) from SpeciesBasicInfo.
     */
    fun collectAllAbilities(info: EvolutionDataLoader.SpeciesBasicInfo): List<String> {
        val abilities = mutableListOf<String>()
        info.abilities?.forEach { abilities += it.lowercase() }
        info.hiddenAbility?.let { abilities += it.lowercase() }
        return abilities
    }
}
