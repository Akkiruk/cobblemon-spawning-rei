package com.cobbledex

class CobbleDexDataQueries(private val snapshot: CobbleDexDataSnapshot) {
    private val materialDecisionCache = mutableMapOf<String, MaterialFormPolicy.Decision?>()
    private val surfaceSpeciesCache = mutableMapOf<String, Boolean>()
    private val formsByBaseCache = mutableMapOf<String, List<EvolutionDataLoader.SpeciesBasicInfo>>()

    fun getSpawnsFor(species: String): List<SpawnInfo> =
        snapshot.spawnsBySpecies[SpeciesNameNormalizer.normalize(species)] ?: emptyList()

    /** The biome (of those this spawn lists) with clearly better odds, or null when it's close. */
    fun bestSpawnBiomeForWay(spawn: SpawnInfo): String? =
        SpawnBiomeRanking.bestBiomeForWay(spawn, snapshot.spawnBiomeCrowd)

    /** The best-odds biome across all of a species' spawn ways, or null. */
    fun headlineSpawnBiome(spawns: List<SpawnInfo>): String? =
        SpawnBiomeRanking.headline(spawns, snapshot.spawnBiomeCrowd)

    fun getEvolutionsFrom(species: String): List<EvolutionInfo> =
        snapshot.evolutionsBySpecies[SpeciesNameNormalizer.normalize(species)] ?: emptyList()

    fun getEvolutionsTo(species: String): List<EvolutionInfo> =
        snapshot.evolutionsToSpecies[SpeciesNameNormalizer.normalize(species)] ?: emptyList()

    fun getSpeciesInfo(species: String): EvolutionDataLoader.SpeciesBasicInfo? =
        snapshot.speciesInfo[SpeciesNameNormalizer.normalize(species)]

    fun getObtainmentFor(species: String): List<ObtainmentInfo> =
        snapshot.obtainmentBySpecies[SpeciesNameNormalizer.normalize(species)] ?: emptyList()

    fun getFossilsFor(species: String): List<FossilCombo> =
        snapshot.fossilsBySpecies[SpeciesNameNormalizer.normalize(species)] ?: emptyList()

    fun getHerdsFor(species: String): List<HerdInfo> =
        snapshot.herdsBySpecies[SpeciesNameNormalizer.normalize(species)] ?: emptyList()

    fun allHerds(): List<HerdInfo> = snapshot.allHerds

    fun getSpeciesDroppingItem(itemId: String): List<String> =
        snapshot.dropsByItem[itemId] ?: emptyList()

    fun getJobsFor(species: String): List<JobMatch> {
        if (snapshot.jobRules.isEmpty()) return emptyList()
        val info = getSpeciesInfo(species) ?: return emptyList()
        val allMoves = JobDataLoader.collectAllMoves(info)
        val allAbilities = JobDataLoader.collectAllAbilities(info)
        return JobDataLoader.evaluateJobs(
            snapshot.jobRules,
            info.primaryType,
            info.secondaryType,
            allAbilities,
            allMoves,
            species,
        )
    }

    fun hasJobRules(): Boolean = snapshot.jobRules.isNotEmpty()

    fun isForm(species: String): Boolean = getSpeciesInfo(species)?.isForm == true

    fun materialFormDecision(species: String): MaterialFormPolicy.Decision? {
        val normalized = SpeciesNameNormalizer.normalize(species)
        if (normalized in materialDecisionCache) return materialDecisionCache[normalized]
        val info = snapshot.speciesInfo[normalized] ?: return null
        if (!info.isForm) return null
        val decision = MaterialFormPolicy.decisionFor(
            normalized,
            snapshot.speciesInfo,
            snapshot.spawnsBySpecies,
            snapshot.obtainmentBySpecies,
            snapshot.evolutionsBySpecies,
            snapshot.fossilsBySpecies,
            snapshot.ridingBySpecies,
        )
        materialDecisionCache[normalized] = decision
        return decision
    }

    fun shouldSurfaceSpecies(species: String): Boolean {
        val normalized = SpeciesNameNormalizer.normalize(species)
        surfaceSpeciesCache[normalized]?.let { return it }
        val info = getSpeciesInfo(species) ?: return true
        val surface = !info.isForm || materialFormDecision(normalized)?.surface == true
        surfaceSpeciesCache[normalized] = surface
        return surface
    }

    fun getFormsOf(baseSpecies: String): List<EvolutionDataLoader.SpeciesBasicInfo> {
        val normalized = SpeciesNameNormalizer.normalize(baseSpecies)
        return formsByBaseCache.getOrPut(normalized) { snapshot.speciesInfo.values.filter {
            it.isForm && SpeciesNameNormalizer.normalize(it.baseSpeciesName!!) == normalized && shouldSurfaceSpecies(it.name)
        } }
    }

    fun findFormByAspects(baseSpecies: String, aspects: Set<String>): EvolutionDataLoader.SpeciesBasicInfo? {
        val targetAspects = normalizeAspects(aspects)
        if (targetAspects.isEmpty()) return null
        return getFormsOf(baseSpecies).firstOrNull { info -> normalizeAspects(info.formAspects) == targetAspects }
    }

    fun getBaseOf(formSpecies: String): String? =
        getSpeciesInfo(formSpecies)?.baseSpeciesName

    fun getSpeciesWithMove(moveName: String): List<String> =
        snapshot.speciesByMove[moveName.lowercase()] ?: emptyList()

    fun getRidingFor(species: String): RidingInfo? =
        snapshot.ridingBySpecies[SpeciesNameNormalizer.normalize(species)]

    fun allTms(): List<TmInfo> = snapshot.tmInfoByMove.values.sortedBy { it.moveName }

    fun getTmForMove(moveName: String): TmInfo? = snapshot.tmInfoByMove[moveName.lowercase()]

    fun getTmsForType(type: String): List<TmInfo> =
        snapshot.tmInfoByMove.values.filter { it.elementalType.equals(type, ignoreCase = true) }.sortedBy { it.moveName }

    fun getTmsUsingItem(itemId: String): List<TmInfo> =
        snapshot.tmInfoByMove.values
            .filter { tm -> tm.ingredients.any { itemId in it.itemIds || it.tagId == itemId } }
            .sortedBy { it.moveName }

    fun hasTms(): Boolean = snapshot.tmInfoByMove.isNotEmpty()

    private fun normalizeAspects(aspects: Set<String>): Set<String> =
        aspects.map { aspect ->
            aspect.lowercase().replace(Regex("[^a-z0-9]"), "")
        }.filter { it.isNotBlank() }.toSet()
}