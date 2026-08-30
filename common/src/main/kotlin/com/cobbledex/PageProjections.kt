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
    // Only genuine "special" obtainment routes (altar / shrine / resurrection / raid / gift / quest …)
    // are surfaced here. Wild spawns, fossils and evolution-into used to be folded in too, but those
    // only ever restated what the dedicated Spawn / Fossil / Evolution pages already show, so a
    // species with no special route now produces no Obtainment page at all.
    val obtainmentRoutes: List<ObtainmentRoute> by lazy(LazyThreadSafetyMode.NONE) {
        specialObtainments.map { ObtainmentRoute(speciesName, it) }
    }
}

data class ObtainmentRoute(
    val speciesName: String,
    val obtainment: ObtainmentInfo,
) {
    val itemIds: List<String> = (obtainment.items + listOfNotNull(obtainment.block)).distinct()
}

object PageProjectionBuilder {
    private val cacheLock = Any()
    @Volatile private var cachedSnapshot: CobbleDexDataSnapshot? = null
    @Volatile private var cachedAllPokemon: List<PokemonPageProjection>? = null
    private var cachedBySpecies = mutableMapOf<String, PokemonPageProjection?>()

    fun allPokemon(snapshot: CobbleDexDataSnapshot = SpawnDataIndex.currentSnapshot()): List<PokemonPageProjection> {
        if (cachedSnapshot === snapshot) {
            cachedAllPokemon?.let { return it }
        }

        synchronized(cacheLock) {
            prepareCache(snapshot)
            cachedAllPokemon?.let { return it }

            val queries = queriesFor(snapshot)
            val speciesNames = if (snapshot.allSpeciesNames.isNotEmpty()) snapshot.allSpeciesNames else fallbackSpeciesNames(snapshot)
            val projections = speciesNames.mapNotNull { name ->
                val normalized = SpeciesNameNormalizer.normalize(name)
                val projection = cachedBySpecies[normalized]
                    ?: buildPokemon(normalized, queries).also { cachedBySpecies[normalized] = it }
                projection
            }
            cachedAllPokemon = projections
            return projections
        }
    }

    fun pokemon(
        speciesName: String,
        snapshot: CobbleDexDataSnapshot = SpawnDataIndex.currentSnapshot(),
    ): PokemonPageProjection? {
        val normalized = SpeciesNameNormalizer.normalize(speciesName)
        synchronized(cacheLock) {
            prepareCache(snapshot)
            cachedBySpecies[normalized]?.let { return it }
            if (cachedBySpecies.containsKey(normalized)) return null

            val projection = buildPokemon(normalized, queriesFor(snapshot))
            cachedBySpecies[normalized] = projection
            return projection
        }
    }

    private fun prepareCache(snapshot: CobbleDexDataSnapshot) {
        if (cachedSnapshot === snapshot) return
        cachedSnapshot = snapshot
        cachedAllPokemon = null
        cachedBySpecies = mutableMapOf()
    }

    private fun queriesFor(snapshot: CobbleDexDataSnapshot): CobbleDexDataQueries =
        if (snapshot === SpawnDataIndex.currentSnapshot()) SpawnDataIndex.currentQueries()
        else CobbleDexDataQueries(snapshot)

    private fun buildPokemon(
        speciesName: String,
        queries: CobbleDexDataQueries,
    ): PokemonPageProjection? {
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