package com.cobbledex

/**
 * Spawn pools, fossils and obtainment data only know the species: a form is picked by a property
 * string beside it (`"tauros paldean bull_breed=blaze"`, `"meloetta lasers_song_forme=lament"`), so
 * every loader files what it reads under the base species. This resolves that string to the form it
 * really produces, so the data lands on that form's page. Anything that doesn't resolve to a form
 * stays where it is, which covers plain variants like Vivillon wings that are species features and
 * not forms at all.
 */
object FormRouter {

    data class FormRef(val key: String, val name: String?, val aspects: Set<String>)

    /** `key=value` properties that describe the Pokémon rather than choosing a form. */
    private val NON_FORM_PROPERTIES = setOf("tera_type", "gender", "nature", "level", "shiny", "ability")

    private val SEPARATORS = Regex("[-_]+")
    private val NON_ALPHANUMERIC = Regex("[^a-z0-9]")

    fun formsByBase(speciesInfo: Map<String, EvolutionDataLoader.SpeciesBasicInfo>): Map<String, List<FormRef>> =
        speciesInfo.values
            .filter { it.isForm }
            .groupBy(
                { SpeciesNameNormalizer.normalize(it.baseSpeciesName!!) },
                { FormRef(SpeciesNameNormalizer.normalize(it.name), it.formName, it.formAspects) },
            )

    /** The key [properties] of [species] belongs under: its form's, or [species] when there is none. */
    fun keyFor(species: String, properties: String?, forms: Map<String, List<FormRef>>): String =
        formFor(forms[species].orEmpty(), properties.orEmpty())?.key ?: species

    fun <T> route(
        bySpecies: Map<String, List<T>>,
        forms: Map<String, List<FormRef>>,
        properties: (T) -> String?,
    ): Map<String, List<T>> {
        if (forms.isEmpty()) return bySpecies
        val routed = LinkedHashMap<String, MutableList<T>>()
        for ((species, list) in bySpecies) {
            for (entry in list) {
                routed.getOrPut(keyFor(species, properties(entry), forms)) { mutableListOf() }.add(entry)
            }
        }
        return routed
    }

    /**
     * Follows Cobblemon's own rule: a Pokémon takes the form whose aspects it has all of, so
     * `paldean bull_breed=blaze` fits both Paldea-Combat (`paldean`) and Paldea-Blaze (`paldean`,
     * `blaze-breed`) and the form asking for the most wins. An explicit `form=` beats aspects.
     */
    internal fun formFor(forms: List<FormRef>, rawProperties: String): FormRef? {
        if (forms.isEmpty() || rawProperties.isBlank()) return null
        val bare = mutableSetOf<String>()
        val choices = mutableSetOf<String>()
        for (token in rawProperties.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }) {
            val key = token.substringBefore('=')
            val value = token.substringAfter('=', missingDelimiterValue = "")
            when {
                value.isEmpty() -> bare.add(flat(key))
                key == "form" -> bare.add(flat(value))
                value == "true" || value == "yes" -> bare.add(flat(key))
                key !in NON_FORM_PROPERTIES -> choices.add(dashed(value))
            }
        }

        forms.firstOrNull { form -> form.name != null && flat(form.name) in bare }?.let { return it }
        return forms
            .filter { form -> form.aspects.isNotEmpty() && form.aspects.all { hasAspect(it, bare, choices) } }
            .maxByOrNull { it.aspects.size }
    }

    /** A choice feature's aspect wraps its value in a format such as `{{choice}}-breed`. */
    private fun hasAspect(aspect: String, bare: Set<String>, choices: Set<String>): Boolean {
        if (flat(aspect) in bare) return true
        val wrapped = "-${dashed(aspect)}-"
        return choices.any { "-$it-" in wrapped }
    }

    private fun flat(raw: String): String = raw.lowercase().replace(NON_ALPHANUMERIC, "")

    private fun dashed(raw: String): String = raw.lowercase().replace(SEPARATORS, "-")
}
