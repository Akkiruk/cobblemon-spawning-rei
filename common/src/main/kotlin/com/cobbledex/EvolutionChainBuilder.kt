package com.cobbledex

object EvolutionChainBuilder {

    data class ChainNode(
        val species: String,
        val aspects: Set<String> = emptySet(),
        val displayName: String,
        val evolutions: List<ChainEdge>
    )

    data class ChainEdge(
        val info: EvolutionInfo,
        val target: ChainNode
    )

    sealed class ChainRow {
        data class Pokemon(
            val species: String,
            val aspects: Set<String>,
            val displayName: String,
            val indent: Int
        ) : ChainRow()

        data class Arrow(
            val requirement: String,
            val items: List<EvolutionItemInfo>,
            val indent: Int
        ) : ChainRow()

        data class Branch(
            val species: String,
            val aspects: Set<String>,
            val displayName: String,
            val requirement: String,
            val items: List<EvolutionItemInfo>,
            val indent: Int
        ) : ChainRow()
    }

    const val CHAIN_PADDING = 4
    const val CHAIN_POKEMON_ROW = 20
    const val CHAIN_ARROW_ROW = 12
    const val CHAIN_BRANCH_ROW = 20
    const val CHAIN_INDENT = 14
    const val CHAIN_SLOT_SIZE = 16
    const val CHAIN_TEXT_X = 20
    const val CHAIN_TEXT_Y_OFF = 4

    @Volatile private var cachedVersion = -1L
    @Volatile private var cachedChains: Map<String, ChainNode>? = null
    @Volatile private var speciesChainMap: Map<String, String>? = null

    fun invalidateCache() {
        cachedChains = null
        speciesChainMap = null
        cachedVersion = -1L
    }

    fun getAllChains(): Map<String, ChainNode> {
        val version = SpawnDataIndex.dataVersion
        cachedChains?.let { if (cachedVersion == version) return it }
        val chains = buildAllChainsInternal()
        cachedVersion = version
        return chains
    }

    fun getChainFor(species: String): ChainNode? {
        getAllChains()
        val base = speciesChainMap?.get(SpeciesNameNormalizer.normalize(species)) ?: return null
        return cachedChains?.get(base)
    }

    fun findBase(species: String): String {
        val visited = mutableSetOf(SpeciesNameNormalizer.normalize(species))
        var current = species
        while (true) {
            val prevEvos = SpawnDataIndex.getEvolutionsTo(current)
            if (prevEvos.isEmpty()) return current
            val prev = SpeciesNameNormalizer.normalize(prevEvos.first().fromSpecies)
            if (prev in visited) return current
            visited.add(prev)
            current = prevEvos.first().fromSpecies
        }
    }

    fun buildChain(baseSpecies: String): ChainNode {
        val visited = mutableSetOf<String>()
        return buildNode(baseSpecies, visited)
    }

    private fun buildNode(species: String, visited: MutableSet<String>): ChainNode {
        val normalized = SpeciesNameNormalizer.normalize(species)
        if (normalized in visited) {
            return ChainNode(species, emptySet(), formatSpeciesName(species), emptyList())
        }
        visited.add(normalized)

        val allEvos = SpawnDataIndex.getEvolutionsFrom(species)
        // Filter to only evolutions matching this form's aspects
        val aspectGroups = allEvos.map { it.fromAspects }.distinct()
        val nodeAspects = if (aspectGroups.size == 1) aspectGroups.first() else emptySet()
        val evos = allEvos.filter { it.fromAspects == nodeAspects }

        // Collapse cosmetic aspect variants — group by base target species,
        // keep only one representative per species (prefer the one with no aspects)
        val grouped = evos.groupBy { SpeciesNameNormalizer.normalize(it.toSpecies) }
        val collapsed = grouped.map { (_, group) ->
            if (group.size == 1) group.first()
            else {
                // Prefer the evolution with no toAspects (the "base" variant)
                val base = group.firstOrNull { it.toAspects.isEmpty() } ?: group.first()
                val variantCount = group.size - 1
                if (variantCount > 0) base.withVariantNote(variantCount) else base
            }
        }

        val seen = mutableSetOf<String>()
        val edges = collapsed.mapNotNull { evo ->
            val targetNorm = SpeciesNameNormalizer.normalize(evo.toSpecies)
            if (!seen.add(targetNorm)) return@mapNotNull null
            ChainEdge(evo, buildNode(evo.toSpecies, visited))
        }

        // Append form-change branches (mega, primal, gmax, ultra_burst)
        val formEdges = buildFormChangeEdges(normalized, seen)

        return ChainNode(species, emptySet(), formatSpeciesName(species), edges + formEdges)
    }

    private val EVOLUTION_LIKE_LABELS = setOf("mega", "primal", "ultra_burst", "gmax")

    private fun buildFormChangeEdges(baseNormalized: String, seen: MutableSet<String>): List<ChainEdge> {
        val edges = mutableListOf<ChainEdge>()
        for ((key, info) in SpawnDataIndex.speciesInfo) {
            if (info.baseSpeciesName == null) continue
            if (SpeciesNameNormalizer.normalize(info.baseSpeciesName) != baseNormalized) continue
            val labels = info.labels ?: continue
            if (labels.none { it in EVOLUTION_LIKE_LABELS }) continue

            val keyNorm = SpeciesNameNormalizer.normalize(key)
            if (!seen.add(keyNorm)) continue

            val requirement = when {
                labels.any { it == "mega" } -> tr("cobbledex-rei-emi-jei.evo.form_change.mega")
                labels.any { it == "primal" } -> tr("cobbledex-rei-emi-jei.evo.form_change.primal")
                labels.any { it == "ultra_burst" } -> tr("cobbledex-rei-emi-jei.evo.form_change.ultra_burst")
                labels.any { it == "gmax" } -> tr("cobbledex-rei-emi-jei.evo.form_change.gmax")
                else -> tr("cobbledex-rei-emi-jei.evo.form_change")
            }

            val syntheticEvo = EvolutionInfo(
                id = "form_change_${info.formName}",
                fromSpecies = info.baseSpeciesName,
                fromAspects = emptySet(),
                toSpecies = key,
                toAspects = info.formAspects,
                variant = "form_change",
                requirements = emptyList(),
                requiredContext = requirement,
                consumeHeldItem = false
            )

            val node = ChainNode(key, info.formAspects, formatSpeciesName(key), emptyList())
            edges.add(ChainEdge(syntheticEvo, node))
        }
        return edges
    }

    fun flattenChain(root: ChainNode): List<ChainRow> {
        val rows = mutableListOf<ChainRow>()
        flattenNode(root, 0, rows, false)
        return rows
    }

    private fun flattenNode(node: ChainNode, indent: Int, rows: MutableList<ChainRow>, skipNodeRow: Boolean) {
        if (!skipNodeRow) {
            rows.add(ChainRow.Pokemon(node.species, node.aspects, node.displayName, indent))
        }

        when {
            node.evolutions.isEmpty() -> {}
            node.evolutions.size == 1 -> {
                val edge = node.evolutions[0]
                val req = if (edge.info.itemRequirements.isNotEmpty()) edge.info.textOnlyRequirements
                    else edge.info.displayRequirements
                rows.add(ChainRow.Arrow(req, edge.info.itemRequirements, indent))
                flattenNode(edge.target, indent, rows, false)
            }
            else -> {
                for (edge in node.evolutions) {
                    val req = if (edge.info.itemRequirements.isNotEmpty()) edge.info.textOnlyRequirements
                        else edge.info.displayRequirements
                    rows.add(ChainRow.Branch(
                        edge.target.species, edge.target.aspects,
                        edge.target.displayName, req, edge.info.itemRequirements, indent + 1
                    ))
                    if (edge.target.evolutions.isNotEmpty()) {
                        flattenNode(edge.target, indent + 1, rows, true)
                    }
                }
            }
        }
    }

    fun collectAllSpecies(node: ChainNode): Set<String> {
        val result = mutableSetOf<String>()
        collectSpeciesRecursive(node, result)
        return result
    }

    private fun collectSpeciesRecursive(node: ChainNode, result: MutableSet<String>) {
        result.add(SpeciesNameNormalizer.normalize(node.species))
        for (edge in node.evolutions) {
            collectSpeciesRecursive(edge.target, result)
        }
    }

    private fun buildAllChainsInternal(): Map<String, ChainNode> {
        val processed = mutableSetOf<String>()
        val chains = mutableMapOf<String, ChainNode>()
        val mapping = mutableMapOf<String, String>()

        for (species in SpawnDataIndex.allSpeciesNames) {
            val normalized = SpeciesNameNormalizer.normalize(species)
            if (normalized in processed) continue
            if (SpawnDataIndex.getSpeciesInfo(species)?.baseSpeciesName != null) continue // skip form entries (O4)

            val hasEvosFrom = SpawnDataIndex.getEvolutionsFrom(species).isNotEmpty()
            val hasEvosTo = SpawnDataIndex.getEvolutionsTo(species).isNotEmpty()
            if (!hasEvosFrom && !hasEvosTo) continue

            val base = findBase(species)
            val normalizedBase = SpeciesNameNormalizer.normalize(base)
            if (normalizedBase in processed) continue

            val chain = buildChain(base)
            val allInChain = collectAllSpecies(chain)
            for (s in allInChain) {
                val norm = SpeciesNameNormalizer.normalize(s)
                processed.add(norm)
                mapping[norm] = normalizedBase
            }
            chains[normalizedBase] = chain
        }

        cachedChains = chains
        speciesChainMap = mapping
        return chains
    }
}
