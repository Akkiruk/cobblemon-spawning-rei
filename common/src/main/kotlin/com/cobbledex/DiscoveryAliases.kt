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
        val materialFormReasons: List<String> = emptyList(),
    )

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
        for (reason in context.materialFormReasons) addAlias(aliases, "material form:${normalizedPhrase(reason)}")

        return aliases.distinctBy { it.lowercase() }
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
        val materialReasons = queries.materialFormDecision(species)?.reasons.orEmpty()
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
            materialFormReasons = materialReasons,
        )
    }

    private fun addAlias(aliases: MutableList<String>, value: String?) {
        val cleaned = value?.trim()?.replace(Regex("\\s+"), " ") ?: return
        if (cleaned.isBlank()) return
        aliases.add(cleaned)

        val compact = normalizedToken(cleaned)
        if (compact.length >= 3 && compact != cleaned.lowercase()) aliases.add(compact)
    }

    private fun normalizedPhrase(value: String): String =
        value.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()

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