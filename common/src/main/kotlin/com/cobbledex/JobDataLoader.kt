package com.cobbledex

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Loads Cobbleworkers job rules via direct API call (compileOnly dependency).
 * Falls back to empty when Cobbleworkers isn't loaded.
 * Also evaluates species eligibility against received rules.
 */
object JobDataLoader {

    private val modLoadedCache = mutableMapOf<String, Boolean>()

    private fun isModLoaded(modId: String): Boolean {
        modLoadedCache[modId]?.let { return it }
        val loaded = checkModPresence(modId)
        modLoadedCache[modId] = loaded
        return loaded
    }

    private fun checkModPresence(modId: String): Boolean {
        try {
            val fabricLoader = Class.forName("net.fabricmc.loader.api.FabricLoader")
            val instance = fabricLoader.getMethod("getInstance").invoke(null)
            val result = instance.javaClass.getMethod("isModLoaded", String::class.java).invoke(instance, modId)
            return result as Boolean
        } catch (_: ClassNotFoundException) {}
        catch (_: Exception) {}

        try {
            val modList = Class.forName("net.neoforged.fml.ModList")
            val list = modList.getMethod("get").invoke(null)
            val result = list.javaClass.getMethod("isLoaded", String::class.java).invoke(list, modId)
            return result as Boolean
        } catch (_: ClassNotFoundException) {}
        catch (_: Exception) {}

        return false
    }

    /**
     * Loads job rules directly from Cobbleworkers API if the mod is present.
     * Returns empty list if Cobbleworkers is not installed.
     */
    fun loadFromCobbleworkers(): List<JobRule> {
        if (!isModLoaded("cobbleworkers")) return emptyList()
        return try {
            val rules = loadFromApi()
            DebugLog.info("Loaded ${rules.size} job rules from Cobbleworkers API")
            rules
        } catch (e: Exception) {
            DebugLog.warn("Failed to load Cobbleworkers job rules: ${e.message}")
            emptyList()
        }
    }

    /** Isolated method — only called when we know Cobbleworkers is on the classpath. */
    private fun loadFromApi(): List<JobRule> {
        val apiRules = accieo.cobbleworkers.api.CobbleworkersApi.getJobRules()
        return apiRules.map { r ->
            JobRule(
                id = r.id,
                displayName = r.displayName,
                description = r.description,
                enabled = r.enabled,
                requiredType = r.requiredType,
                designatedSpecies = r.designatedSpecies,
                requiredMoves = r.requiredMoves,
                requiredAbility = r.requiredAbility,
                hardcodedSpecies = r.hardcodedSpecies,
                hardcodedSpeciesEnabled = r.hardcodedSpeciesEnabled,
                priority = r.priority,
            )
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
