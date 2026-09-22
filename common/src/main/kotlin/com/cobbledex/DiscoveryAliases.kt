package com.cobbledex

import java.util.concurrent.ConcurrentHashMap

object DiscoveryAliases {
    data class PokemonContext(
        val species: String,
        val displayName: String,
        val baseSpeciesName: String? = null,
        val primaryType: String? = null,
        val secondaryType: String? = null,
        val abilities: List<String> = emptyList(),
        val hiddenAbility: String? = null,
        val formAspects: Set<String> = emptySet(),
        val jobAliases: List<String> = emptyList(),
    )

    /**
     * How many aliases JEI gets per Pokémon.
     *
     * JEI renders every alias an ingredient registers as a bullet line on its hover tooltip
     * (`searchIngredientAliases`, on by default) and its API offers no way to register an alias
     * that searches but doesn't render. So the only lever on tooltip length is how many aliases
     * get registered at all, and a full list ran to 30+ lines - tall enough to cover the
     * ingredient rows underneath it, including the name of the Pokémon being hovered.
     */
    const val JEI_ALIAS_LIMIT = 5

    private data class CachedSearchText(val dataVersion: Long, val text: String)

    private val searchTextCache = ConcurrentHashMap<String, CachedSearchText>()

    fun pokemonSearchText(species: String): String {
        val normalized = SpeciesNameNormalizer.normalize(species)
        val version = SpawnDataIndex.dataVersion
        searchTextCache[normalized]?.let { cached ->
            if (cached.dataVersion == version) return cached.text
        }

        val text = pokemonAliases(contextFor(normalized)).joinToString(" ")
        searchTextCache[normalized] = CachedSearchText(version, text)
        return text
    }

    /** Full alias list for a species (name, dex token, type:/ability:/form:/job aliases, …). */
    fun pokemonAliasList(species: String): List<String> =
        pokemonAliases(contextFor(SpeciesNameNormalizer.normalize(species)))

    fun pokemonAliases(context: PokemonContext): List<String> {
        val aliases = mutableListOf<String>()
        addAlias(aliases, context.displayName)
        addAlias(aliases, context.species)
        addAlias(aliases, "pokemon:${normalizedToken(context.species)}")

        context.baseSpeciesName?.let { base ->
            addAlias(aliases, readableName(base))
            addAlias(aliases, "base:${normalizedToken(base)}")
            addAlias(aliases, "form of ${readableName(base)}")
        }

        for (type in listOfNotNull(context.primaryType, context.secondaryType)) {
            val token = normalizedToken(type)
            addAlias(aliases, readableName(type))
            addAlias(aliases, "type:$token")
        }

        for (ability in context.abilities) {
            val token = normalizedToken(ability)
            addAlias(aliases, readableName(ability))
            addAlias(aliases, "ability:$token")
        }
        context.hiddenAbility?.let { ability ->
            val token = normalizedToken(ability)
            addAlias(aliases, readableName(ability))
            addAlias(aliases, "hidden ability:$token")
            addAlias(aliases, "ability:$token")
        }

        for (aspect in context.formAspects) {
            val token = normalizedToken(aspect)
            addAlias(aliases, readableName(aspect))
            addAlias(aliases, "form:$token")
        }

        for (jobAlias in context.jobAliases) addAlias(aliases, jobAlias)

        return aliases.distinctBy { it.lowercase() }
    }

    /** The [JEI_ALIAS_LIMIT] aliases JEI gets for a species, spread across as many categories as it has. */
    fun pokemonAliasesForJei(species: String): List<String> =
        curatedAliases(contextFor(SpeciesNameNormalizer.normalize(species)), JEI_ALIAS_LIMIT)

    /**
     * Picks at most [limit] aliases, taking one from each category in priority order before
     * taking a second from any of them - so the slots cover as many distinct ways of finding the
     * species (base species, typing, ability, form, job) as it actually has, instead of spending
     * all of them on one category's list. Ordered widest-net first: a form's base species name
     * finds it when its own name isn't known, then typing, then abilities.
     *
     * Exposed (rather than private behind [pokemonAliasesForJei]) so it can be tested against an
     * explicit context, with no live species index to load.
     */
    fun curatedAliases(context: PokemonContext, limit: Int = JEI_ALIAS_LIMIT): List<String> {
        val buckets = mutableListOf<List<String>>()

        context.baseSpeciesName?.let { base ->
            buckets.add(listOf("base:${normalizedToken(base)}"))
        }

        val typeAliases = listOfNotNull(context.primaryType, context.secondaryType)
            .map { "type:${normalizedToken(it)}" }
        if (typeAliases.isNotEmpty()) buckets.add(typeAliases)

        val abilityAliases = context.abilities.map { "ability:${normalizedToken(it)}" }
        if (abilityAliases.isNotEmpty()) buckets.add(abilityAliases)

        context.hiddenAbility?.let { buckets.add(listOf("ability:${normalizedToken(it)}")) }

        val formAliases = context.formAspects.map { "form:${normalizedToken(it)}" }
        if (formAliases.isNotEmpty()) buckets.add(formAliases)

        val jobAliases = context.jobAliases.filter { it.startsWith("job:") }
        if (jobAliases.isNotEmpty()) buckets.add(jobAliases)

        val result = mutableListOf<String>()
        var round = 0
        while (result.size < limit) {
            var addedThisRound = false
            for (bucket in buckets) {
                if (round >= bucket.size) continue
                val candidate = bucket[round]
                if (result.none { it.equals(candidate, ignoreCase = true) }) {
                    result.add(candidate)
                    addedThisRound = true
                    if (result.size >= limit) break
                }
            }
            if (!addedThisRound) break
            round++
        }
        return result
    }

    fun moveAliases(moveName: String): List<String> {
        val token = normalizedToken(moveName)
        val readable = readableName(moveName)
        return listOf(
            moveName,
            readable,
            "move:$token",
            "tm:$token",
            "tr:$token",
            "technical machine $readable",
        ).filter { it.isNotBlank() }.distinctBy { it.lowercase() }
    }

    private fun contextFor(species: String): PokemonContext {
        val queries = SpawnDataIndex.currentQueries()
        val info = queries.getSpeciesInfo(species)
        val jobs = queries.getJobsFor(species).flatMap { match ->
            listOf("job:${match.rule.id}", match.rule.displayName)
        }
        return PokemonContext(
            species = species,
            displayName = formatSpeciesName(species),
            baseSpeciesName = info?.baseSpeciesName,
            primaryType = info?.primaryType,
            secondaryType = info?.secondaryType,
            abilities = info?.abilities.orEmpty(),
            hiddenAbility = info?.hiddenAbility,
            formAspects = info?.formAspects.orEmpty(),
            jobAliases = jobs,
        )
    }

    private fun addAlias(aliases: MutableList<String>, value: String?) {
        val cleaned = value?.trim()?.replace(Regex("\\s+"), " ") ?: return
        if (cleaned.isBlank()) return
        aliases.add(cleaned)
    }

    private fun normalizedToken(value: String): String =
        value.lowercase().replace(Regex("[^a-z0-9]+"), "").trim()

    private fun readableName(value: String): String =
        value.replace(Regex("([a-z])([A-Z])"), "$1 $2")
            .replace(Regex("[_-]+"), " ")
            .trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
}