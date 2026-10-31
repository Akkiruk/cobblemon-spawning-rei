package com.cobbledex

/** Clean-up of the evolution list once every source has been merged. */
object EvolutionEdges {

    /** Where an edge leads, whichever form it starts from. */
    fun destination(edge: EvolutionInfo): String =
        listOf(
            SpeciesNameNormalizer.normalize(edge.fromSpecies),
            SpeciesNameNormalizer.normalize(edge.toSpecies),
            // A region bias is carried across an evolution, not chosen by it, so it names no new result.
            edge.toAspects.map { it.lowercase() }.filterNot { it.startsWith("region-bias") }.sorted().joinToString(","),
        ).joinToString("|")

    private fun isCosmetic(aspect: String): Boolean = aspect.lowercase().startsWith("cosmetic_item")

    /**
     * Cobblemon repeats a species' evolutions on a cosmetic form for each item it can wear, Pikachu's
     * snacks being the obvious case. Those change nothing, so they go whenever the plain form reaches
     * the same result.
     */
    fun withoutCosmeticCopies(edges: Map<String, List<EvolutionInfo>>): Map<String, MutableList<EvolutionInfo>> {
        val plain = edges.values.flatten()
            .filter { edge -> edge.fromAspects.none(::isCosmetic) }
            .mapTo(HashSet(), ::destination)
        return edges
            .mapValues { (_, list) ->
                list.filterNotTo(mutableListOf()) { edge -> edge.fromAspects.any(::isCosmetic) && destination(edge) in plain }
            }
            .filterValues { it.isNotEmpty() }
    }
}
