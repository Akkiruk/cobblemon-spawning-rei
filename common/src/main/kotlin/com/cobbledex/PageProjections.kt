package com.cobbledex

data class PokemonPageProjection(
    val speciesName: String,
    val info: EvolutionDataLoader.SpeciesBasicInfo?,
    val spawns: List<SpawnInfo>,
    val sortedSpawns: List<SpawnDisplayHelper.SortedSpawnEntry>,
    val specialObtainments: List<ObtainmentInfo>,
    val fossils: List<FossilCombo>,
    val evolutionsFrom: List<EvolutionInfo>,
    val evolutionsTo: List<EvolutionInfo>,
    val jobs: List<JobMatch>,
    val riding: RidingInfo?,
    val forms: List<EvolutionDataLoader.SpeciesBasicInfo>,
    val materialFormDecision: MaterialFormPolicy.Decision?,
) {
    val obtainmentRoutes: List<ObtainmentRoute> by lazy(LazyThreadSafetyMode.NONE) {
        buildList {
            if (sortedSpawns.isNotEmpty()) add(ObtainmentRoute.WildSpawns(speciesName, sortedSpawns))
            specialObtainments.forEach { add(ObtainmentRoute.Special(speciesName, it)) }
            fossils.forEach { add(ObtainmentRoute.Fossil(speciesName, it)) }
            evolutionsTo.forEach { add(ObtainmentRoute.Evolution(speciesName, it)) }
        }
    }
}

sealed class ObtainmentRoute {
    abstract val speciesName: String
    abstract val itemIds: List<String>

    data class WildSpawns(
        override val speciesName: String,
        val entries: List<SpawnDisplayHelper.SortedSpawnEntry>,
    ) : ObtainmentRoute() {
        override val itemIds: List<String> = emptyList()
    }

    data class Special(
        override val speciesName: String,
        val obtainment: ObtainmentInfo,
    ) : ObtainmentRoute() {
        override val itemIds: List<String> = (obtainment.items + listOfNotNull(obtainment.block)).distinct()
    }

    data class Fossil(
        override val speciesName: String,
        val combo: FossilCombo,
    ) : ObtainmentRoute() {
        override val itemIds: List<String> = combo.fossilItems.distinct()
    }

    data class Evolution(
        override val speciesName: String,
        val evolution: EvolutionInfo,
    ) : ObtainmentRoute() {
        override val itemIds: List<String> = evolution.itemRequirements.map { it.itemId }.distinct()
    }
}

object PageProjectionBuilder {
    fun allPokemon(snapshot: CobbleDexDataSnapshot = SpawnDataIndex.currentSnapshot()): List<PokemonPageProjection> {
        val speciesNames = if (snapshot.allSpeciesNames.isNotEmpty()) snapshot.allSpeciesNames else fallbackSpeciesNames(snapshot)
        return speciesNames.mapNotNull { pokemon(it, snapshot) }
    }

    fun pokemon(
        speciesName: String,
        snapshot: CobbleDexDataSnapshot = SpawnDataIndex.currentSnapshot(),
    ): PokemonPageProjection? {
        val queries = CobbleDexDataQueries(snapshot)
        val normalized = SpeciesNameNormalizer.normalize(speciesName)
        if (!queries.shouldSurfaceSpecies(normalized)) return null
        val info = queries.getSpeciesInfo(normalized)
        val spawns = queries.getSpawnsFor(normalized)
        val specialObtainments = queries.getObtainmentFor(normalized)
        val fossils = queries.getFossilsFor(normalized)
        val evolutionsFrom = queries.getEvolutionsFrom(normalized)
        val evolutionsTo = queries.getEvolutionsTo(normalized)
        val riding = queries.getRidingFor(normalized)
        val forms = queries.getFormsOf(normalized)
        val jobs = queries.getJobsFor(normalized)

        if (info == null && spawns.isEmpty() && specialObtainments.isEmpty() && fossils.isEmpty() &&
            evolutionsFrom.isEmpty() && evolutionsTo.isEmpty() && riding == null && forms.isEmpty() && jobs.isEmpty()
        ) return null

        return PokemonPageProjection(
            speciesName = normalized,
            info = info,
            spawns = spawns,
            sortedSpawns = SpawnPageBuilder.sortedSpawns(spawns),
            specialObtainments = specialObtainments,
            fossils = fossils,
            evolutionsFrom = evolutionsFrom,
            evolutionsTo = evolutionsTo,
            jobs = jobs,
            riding = riding,
            forms = forms,
            materialFormDecision = queries.materialFormDecision(normalized),
        )
    }

    private fun fallbackSpeciesNames(snapshot: CobbleDexDataSnapshot): List<String> =
        (snapshot.speciesInfo.keys + snapshot.spawnsBySpecies.keys + snapshot.obtainmentBySpecies.keys +
            snapshot.fossilsBySpecies.keys + snapshot.evolutionsBySpecies.keys + snapshot.evolutionsToSpecies.keys +
            snapshot.ridingBySpecies.keys)
            .map { SpeciesNameNormalizer.normalize(it) }
            .distinct()
            .sorted()
}