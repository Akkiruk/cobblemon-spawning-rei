package com.cobbledex

object PokemonSearchTerms {

    fun buildTerms(
        species: String,
        displayName: String? = null,
        includeDisplayName: Boolean = true,
    ): List<String> {
        val terms = linkedSetOf<String>()

        if (includeDisplayName) {
            displayName?.takeIf { it.isNotBlank() }?.let(terms::add)
        }

        val info = SpawnDataIndex.getSpeciesInfo(species)
        if (info != null) {
            listOfNotNull(info.primaryType, info.secondaryType)
                .map(::formatTypeName)
                .forEach(terms::add)

            if (info.isForm && info.baseSpeciesName != null) {
                terms.add(formatSpeciesName(info.baseSpeciesName))
            }
        }

        for (job in SpawnDataIndex.getJobsFor(species)) {
            terms.add("job:${job.rule.id}")
            terms.add(job.rule.displayName)
            terms.add("job:${job.rule.id} ${job.rule.displayName}")
        }

        return terms.toList()
    }

    fun buildSearchText(
        species: String,
        displayName: String? = null,
        includeDisplayName: Boolean = true,
    ): String = buildTerms(species, displayName, includeDisplayName).joinToString(" ")
}