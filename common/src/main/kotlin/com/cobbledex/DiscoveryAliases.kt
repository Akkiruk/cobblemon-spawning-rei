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
        val ridingStyles: List<String> = emptyList(),
        val rarityLabels: List<String> = emptyList(),
        val regionalVariant: String? = null,
    )

    /**
     * How many aliases JEI gets per Pokémon.
     *
     * JEI renders every alias an ingredient registers as a bullet line on its hover tooltip
     * (`searchIngredientAliases`, on by default) and its API offers no way to register an alias
     * that searches but doesn't render. So the only lever on tooltip length is how many aliases
     * get registered at all, and a full list ran to 30+ lines - tall enough to cover the
     * ingredient rows underneath it, including the name of the Pokémon being hovered.
     *
     * 11 is not an arbitrary trim: it's the exact ceiling [curatedAliases] can ever produce - 2
     * types + 3 riding styles + 1 base species + 1 rarity tag + 3 abilities (2 regular + hidden) +
     * 1 regional variant - so no real species ever gets truncated, only capped against a
     * datapack that stacks more rarity tags on one species than Cobblemon's own data ever does.
     */
    const val JEI_ALIAS_LIMIT = 11

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

    /** Up to [JEI_ALIAS_LIMIT] aliases JEI gets for a species - see [curatedAliases] for which six categories and why. */
    fun pokemonAliasesForJei(species: String): List<String> =
        curatedAliases(contextFor(SpeciesNameNormalizer.normalize(species)), JEI_ALIAS_LIMIT)

    /**
     * At most [limit] aliases from exactly six categories, in priority order: typing, riding
     * style, base species, rarity, ability, regional variant. Unlike the old diversity-first
     * scheme this replaced, this is a straight concatenation - each category is now an explicit,
     * ranked choice the player made (see the alias-priority discussion this came out of), not an
     * arbitrary grab-bag where spreading slots across categories was the only way to get variety.
     * A cap tight enough to bind should drop the *lowest*-priority category's values first, which
     * round-robin doesn't do: it hands every category a slot before any category gets a second one,
     * so "regional variant" could out-rank a species' second ability. At the default
     * [JEI_ALIAS_LIMIT] the cap never binds at all (see its doc) - it only matters for a smaller
     * [limit], or the rarity edge case [JEI_ALIAS_LIMIT]'s doc calls out.
     *
     * Exposed (rather than private behind [pokemonAliasesForJei]) so it can be tested against an
     * explicit context, with no live species index to load.
     */
    fun curatedAliases(context: PokemonContext, limit: Int = JEI_ALIAS_LIMIT): List<String> {
        val ordered = mutableListOf<String>()

        for (type in listOfNotNull(context.primaryType, context.secondaryType)) {
            ordered.add("type:${normalizedToken(type)}")
        }
        for (style in context.ridingStyles) {
            ordered.add("riding:${normalizedToken(style)}")
        }
        context.baseSpeciesName?.let { ordered.add("base:${normalizedToken(it)}") }
        for (rarity in context.rarityLabels) {
            ordered.add("rarity:${normalizedToken(rarity)}")
        }
        for (ability in context.abilities + listOfNotNull(context.hiddenAbility)) {
            ordered.add("ability:${normalizedToken(ability)}")
        }
        context.regionalVariant?.let { ordered.add("regional:${normalizedToken(it)}") }

        return ordered.distinctBy { it.lowercase() }.take(limit)
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
        val formAspects = info?.formAspects.orEmpty()
        val labels = info?.labels.orEmpty()
        return PokemonContext(
            species = species,
            displayName = formatSpeciesName(species),
            baseSpeciesName = info?.baseSpeciesName,
            primaryType = info?.primaryType,
            secondaryType = info?.secondaryType,
            abilities = info?.abilities.orEmpty(),
            hiddenAbility = info?.hiddenAbility,
            formAspects = formAspects,
            jobAliases = jobs,
            ridingStyles = queries.getRidingFor(species)?.allMountTypes.orEmpty(),
            rarityLabels = labels.filter { it in RARITY_LABELS },
            regionalVariant = RegionalForms.suffixFor(formAspects, labels),
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