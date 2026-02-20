package com.cobbledex

/**
 * Represents a single Cobbleworkers job eligibility rule,
 * received from the server's Cobbleworkers config.
 */
data class JobRule(
    val id: String,
    val displayName: String,
    val description: String,
    val enabled: Boolean,
    val requiredType: String?,
    val designatedSpecies: List<String>,
    val requiredMoves: List<String>,
    val requiredAbility: String?,
    val hardcodedSpecies: List<String>,
    val hardcodedSpeciesEnabled: Boolean,
    val priority: String,
)

/**
 * The result of evaluating a species against all job rules.
 * Contains the job and the reason(s) the species qualifies.
 */
data class JobMatch(
    val rule: JobRule,
    val reasons: List<String>,
)

/** One recipe page per species-job combination. */
data class JobRecipeData(
    val speciesName: String,
    val match: JobMatch,
)
