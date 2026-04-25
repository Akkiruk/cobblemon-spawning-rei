package com.cobbledex

class CobbleDexDataQueries(private val snapshot: CobbleDexDataSnapshot) {
    fun getSpawnsFor(species: String): List<SpawnInfo> =
        snapshot.spawnsBySpecies[SpeciesNameNormalizer.normalize(species)] ?: emptyList()

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
        val info = snapshot.speciesInfo[normalized] ?: return null
        if (!info.isForm) return null
        return MaterialFormPolicy.decisionFor(
            normalized,
            snapshot.speciesInfo,
            snapshot.spawnsBySpecies,
            snapshot.obtainmentBySpecies,
            snapshot.evolutionsBySpecies,
            snapshot.fossilsBySpecies,
            snapshot.ridingBySpecies,
        )
    }

    fun shouldSurfaceSpecies(species: String): Boolean {
        val info = getSpeciesInfo(species) ?: return true
        if (!info.isForm) return true
        return materialFormDecision(species)?.surface == true
    }

    fun getFormsOf(baseSpecies: String): List<EvolutionDataLoader.SpeciesBasicInfo> {
        val normalized = SpeciesNameNormalizer.normalize(baseSpecies)
        return snapshot.speciesInfo.values.filter {
            it.isForm && SpeciesNameNormalizer.normalize(it.baseSpeciesName!!) == normalized && shouldSurfaceSpecies(it.name)
        }
    }

    fun getBaseOf(formSpecies: String): String? =
        getSpeciesInfo(formSpecies)?.baseSpeciesName

    fun getSpeciesWithTmMove(moveName: String): List<String> =
        snapshot.speciesByTmMove[moveName.lowercase()] ?: emptyList()

    fun getRidingFor(species: String): RidingInfo? =
        snapshot.ridingBySpecies[SpeciesNameNormalizer.normalize(species)]
}