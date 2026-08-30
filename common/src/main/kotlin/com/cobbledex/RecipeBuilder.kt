package com.cobbledex

import com.cobbledex.config.CobbleDexConfig

object RecipeBuilder {
    private val FORM_REASON_PRIORITY = listOf(
        "typing",
        "abilities",
        "base stat total",
        "base stats",
        "form-specific evolution data",
        "form-specific spawn data",
        "form-specific obtainment data",
        "drops",
        "level-up moves",
        "egg moves",
        "tutor moves",
        "tm moves",
        "form-specific riding data",
        "labels",
        "body metrics",
        "catch rate",
        "ev yield",
        "egg groups",
        "gender ratio",
        "egg cycles",
        "experience group",
        "base experience",
        "base friendship",
        "shoulder mount",
        "form-specific fossil data",
    ).withIndex().associate { (index, value) -> value to index }

    private data class EvolutionTargetPage(
        val targetSpeciesName: String,
        val targetAspects: Set<String>,
        val methods: List<EvolutionMethodRecipeData>,
        val priority: Int,
    )

    fun buildAllOverviewRecipes(): List<PokemonOverviewRecipeData> =
        PageProjectionBuilder.allPokemon().map { PokemonOverviewRecipeData(it) }

    fun buildOverviewFor(speciesName: String): PokemonOverviewRecipeData? =
        PageProjectionBuilder.pokemon(speciesName)?.let { PokemonOverviewRecipeData(it) }

    fun buildSpawnRecipes(species: String, spawns: List<SpawnInfo>): List<SpawnRecipeData> {
        return SpawnPageBuilder.sortedSpawns(spawns).mapNotNull { entry ->
            try {
                SpawnRecipeData(species, entry.spawn, entry.formVariants, entry.bucketIndex, entry.bucketTotal)
            } catch (e: Exception) {
                DebugLog.once("recipe-spawn-$species-${entry.spawn.id}") { "Failed: ${e.message}" }
                null
            }
        }
    }

    fun buildAllEvolutionRecipes(
        snapshot: CobbleDexDataSnapshot = SpawnDataIndex.currentSnapshot(),
    ): List<EvolutionRecipeData> {
        val queries = CobbleDexDataQueries(snapshot)
        val speciesNames = if (snapshot.allSpeciesNames.isNotEmpty()) {
            snapshot.allSpeciesNames
        } else {
            (snapshot.speciesInfo.keys + snapshot.evolutionsBySpecies.keys)
                .map(SpeciesNameNormalizer::normalize)
                .distinct()
                .sorted()
        }

        return speciesNames
            .map(SpeciesNameNormalizer::normalize)
            .filter(queries::shouldSurfaceSpecies)
            .flatMap { species -> buildEvolutionPagesFor(species, snapshot) }
    }

    fun buildEvolutionPagesFor(
        speciesName: String,
        snapshot: CobbleDexDataSnapshot = SpawnDataIndex.currentSnapshot(),
    ): List<EvolutionRecipeData> {
        val queries = CobbleDexDataQueries(snapshot)
        val normalized = SpeciesNameNormalizer.normalize(speciesName)
        if (!queries.shouldSurfaceSpecies(normalized)) return emptyList()

        val info = queries.getSpeciesInfo(normalized)
        val sourceAspects = info?.formAspects ?: emptySet()
        val targets = buildEvolutionTargets(normalized, sourceAspects, snapshot, queries)
        if (targets.isEmpty()) {
            return listOf(
                EvolutionRecipeData(
                    sourceSpeciesName = normalized,
                    sourceAspects = sourceAspects,
                    pageIndex = 1,
                    pageTotal = 1,
                    totalOutcomes = 0,
                )
            )
        }

        val total = targets.size
        return targets.mapIndexed { index, target ->
            EvolutionRecipeData(
                sourceSpeciesName = normalized,
                sourceAspects = sourceAspects,
                targetSpeciesName = target.targetSpeciesName,
                targetAspects = target.targetAspects,
                methods = target.methods,
                pageIndex = index + 1,
                pageTotal = total,
                totalOutcomes = total,
            )
        }
    }

    fun buildEvolutionRecipesInto(
        speciesName: String,
        snapshot: CobbleDexDataSnapshot = SpawnDataIndex.currentSnapshot(),
    ): List<EvolutionRecipeData> {
        val queries = CobbleDexDataQueries(snapshot)
        val normalized = SpeciesNameNormalizer.normalize(speciesName)
        if (!queries.shouldSurfaceSpecies(normalized)) return emptyList()

        val incomingSources = queries.getEvolutionsTo(normalized)
            .map { evolution -> resolveEvolutionSourceKey(evolution, queries) }

        // The immediate parent for a mega/primal/gmax-style transform isn't
        // always the base species - e.g. "Mega Midnight" transforms from
        // "Midnight", not from base Lucario (see resolveTransformParent).
        val transformParentSource = queries.getSpeciesInfo(normalized)
            ?.takeIf { info -> info.formAspects.isNotEmpty() }
            ?.let { info -> resolveTransformParent(normalized, info.formAspects, queries)?.first }

        return (incomingSources + listOfNotNull(transformParentSource))
            .distinct()
            .flatMap { source -> buildEvolutionPagesFor(source, snapshot) }
            .filter { page -> SpeciesNameNormalizer.normalize(page.targetSpeciesName.orEmpty()) == normalized }
    }

    fun buildEvolutionRecipesForItem(
        itemId: String,
        snapshot: CobbleDexDataSnapshot = SpawnDataIndex.currentSnapshot(),
    ): List<EvolutionRecipeData> {
        val normalizedItem = itemId.lowercase()
        val queries = CobbleDexDataQueries(snapshot)
        val sourceSpecies = snapshot.evolutionsBySpecies.values
            .flatten()
            .filter { evolution ->
                evolution.itemRequirements.any { requirement -> requirement.itemId.equals(normalizedItem, ignoreCase = true) }
            }
            .map { evolution -> resolveEvolutionSourceKey(evolution, queries) }
            .distinct()

        return sourceSpecies.flatMap { species -> buildEvolutionPagesFor(species, snapshot) }.filter { page ->
            page.methods.any { method ->
                method.itemRequirements.any { requirement -> requirement.itemId.equals(normalizedItem, ignoreCase = true) }
            }
        }
    }

    fun buildAllObtainmentRecipes(): List<ObtainmentRecipeData> {
        val recipes = mutableListOf<ObtainmentRecipeData>()
        for ((species, obtainments) in SpawnDataIndex.obtainmentBySpecies) {
            if (!SpawnDataIndex.shouldSurfaceSpecies(species)) continue
            if (obtainments.isEmpty()) continue
            obtainments.forEachIndexed { i, info ->
                recipes.add(ObtainmentRecipeData(species, info, i + 1, obtainments.size))
            }
        }
        return recipes
    }

    fun buildObtainmentsFor(species: String, obtainments: List<ObtainmentInfo>): List<ObtainmentRecipeData> {
        if (!SpawnDataIndex.shouldSurfaceSpecies(species)) return emptyList()
        if (obtainments.isEmpty()) return emptyList()
        return obtainments.mapIndexed { i, info ->
            ObtainmentRecipeData(species, info, i + 1, obtainments.size)
        }
    }

    fun buildAllUnifiedObtainmentRecipes(): List<UnifiedObtainmentRecipeData> {
        return PageProjectionBuilder.allPokemon()
            .flatMap { projection -> buildUnifiedObtainmentPages(projection.speciesName, projection.obtainmentRoutes) }
    }

    fun buildUnifiedObtainmentFor(speciesName: String): List<UnifiedObtainmentRecipeData> {
        val projection = PageProjectionBuilder.pokemon(speciesName) ?: return emptyList()
        return buildUnifiedObtainmentPages(projection.speciesName, projection.obtainmentRoutes)
    }

    fun buildUnifiedObtainmentForItem(itemId: String): List<UnifiedObtainmentRecipeData> {
        return PageProjectionBuilder.allPokemon().flatMap { projection ->
            val routes = projection.obtainmentRoutes.filter { route -> itemId in route.itemIds }
            buildUnifiedObtainmentPages(projection.speciesName, routes)
        }
    }

    private fun buildUnifiedObtainmentPages(
        speciesName: String,
        routes: List<ObtainmentRoute>,
    ): List<UnifiedObtainmentRecipeData> {
        if (routes.isEmpty()) return emptyList()
        val pages = MeasuredPagePlanner.paginate(
            items = routes,
            fixedHeight = ObtainmentPageBuilder.measureUnifiedFixedHeight(),
            spacingHeight = ObtainmentPageBuilder.UNIFIED_ROUTE_SPACING,
            measureItemHeight = { route -> ObtainmentPageBuilder.measureUnifiedRouteHeight(speciesName, route) },
        )
        val pageTotal = pages.size
        return pages.mapIndexed { index, pageRoutes ->
            UnifiedObtainmentRecipeData(
                speciesName = speciesName,
                routes = pageRoutes,
                pageIndex = index + 1,
                pageTotal = pageTotal,
                totalRoutes = routes.size,
            )
        }
    }

    fun buildAllDropRecipes(): List<DropRecipeData> {
        val recipes = mutableListOf<DropRecipeData>()
        for ((species, info) in SpawnDataIndex.speciesInfo) {
            if (!SpawnDataIndex.shouldSurfaceSpecies(species)) continue
            val drops = info.drops ?: continue
            if (drops.isEmpty()) continue
            recipes.addAll(paginateDrops(species, drops))
        }
        return recipes
    }

    fun buildDropsFor(speciesName: String): List<DropRecipeData> {
        if (!SpawnDataIndex.shouldSurfaceSpecies(speciesName)) return emptyList()
        val info = SpawnDataIndex.getSpeciesInfo(speciesName) ?: return emptyList()
        val drops = info.drops ?: return emptyList()
        if (drops.isEmpty()) return emptyList()
        return paginateDrops(speciesName, drops)
    }

    fun buildDropRecipesForItem(itemId: String): List<DropRecipeData> {
        val species = SpawnDataIndex.getSpeciesDroppingItem(itemId).distinct()
        if (species.isEmpty()) return emptyList()
        return species.mapNotNull { buildDropsFor(it).firstOrNull() }
    }

    // --- Item-dropper lookup ---
    //
    // Looking up an item shows a grid of every Pokémon that drops it, paginated into grids rather
    // than one page per Pokémon (common items like leather or bones have dozens of droppers).

    fun buildItemDroppersForItem(itemId: String): List<ItemDroppersRecipeData> {
        val species = SpawnDataIndex.getSpeciesDroppingItem(itemId)
            .distinct()
            .filter { SpawnDataIndex.shouldSurfaceSpecies(it) && PokemonItemCache.canRender(it) }
            .sortedBy { SpawnDataIndex.getSpeciesInfo(it)?.nationalDexNumber?.takeIf { n -> n > 0 } ?: Int.MAX_VALUE }
        if (species.isEmpty()) return emptyList()

        val droppers = species.mapNotNull { sp ->
            val entries = SpawnDataIndex.getSpeciesInfo(sp)?.drops
                ?.filter { it.itemId.equals(itemId, ignoreCase = true) }
                ?.takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null
            ItemDropper(sp, entries)
        }
        if (droppers.isEmpty()) return emptyList()

        val pages = droppers.chunked(SpawnDisplayHelper.ITEM_DROPPERS_PER_PAGE)
        return pages.mapIndexed { i, chunk ->
            ItemDroppersRecipeData(itemId, chunk, i + 1, pages.size, droppers.size)
        }
    }

    /** Every item-dropper grid, one family per dropped item — used for "view all" browsing and panel sizing. */
    fun buildAllItemDropperRecipes(): List<ItemDroppersRecipeData> =
        SpawnDataIndex.dropsByItem.keys.sorted().flatMap { buildItemDroppersForItem(it) }

    private fun paginateDrops(speciesName: String, drops: List<DropEntryInfo>): List<DropRecipeData> {
        val pages = MeasuredPagePlanner.paginate(
            items = drops,
            fixedHeight = DropPageBuilder.measureFixedHeight(),
            measureItemHeight = DropPageBuilder::measureEntryHeight,
        )
        val totalPages = pages.size
        return pages.mapIndexed { index, pageDrops ->
            DropRecipeData(
                speciesName = speciesName,
                drops = pageDrops,
                pageIndex = index + 1,
                pageTotal = totalPages,
                totalDrops = drops.size
            )
        }
    }

    fun buildAllStatsRecipes(): List<StatsRecipeData> {
        val recipes = mutableListOf<StatsRecipeData>()
        for ((species, info) in SpawnDataIndex.speciesInfo) {
            if (!SpawnDataIndex.shouldSurfaceSpecies(species)) continue
            val stats = info.baseStats ?: continue
            val bst = info.baseStatTotal ?: continue
            if (stats.isEmpty()) continue
            recipes.add(StatsRecipeData(species, stats, bst, info.primaryType, info.secondaryType, info.evYield))
        }
        return recipes
    }

    fun buildStatsFor(speciesName: String): StatsRecipeData? {
        if (!SpawnDataIndex.shouldSurfaceSpecies(speciesName)) return null
        val info = SpawnDataIndex.getSpeciesInfo(speciesName) ?: return null
        val stats = info.baseStats ?: return null
        val bst = info.baseStatTotal ?: return null
        if (stats.isEmpty()) return null
        return StatsRecipeData(speciesName, stats, bst, info.primaryType, info.secondaryType, info.evYield)
    }

    fun buildAllPokedexInfoRecipes(): List<PokedexInfoRecipeData> {
        val recipes = mutableListOf<PokedexInfoRecipeData>()
        for ((species, info) in SpawnDataIndex.speciesInfo) {
            if (!SpawnDataIndex.shouldSurfaceSpecies(species)) continue
            recipes.add(buildPokedexInfoFrom(species, info))
        }
        return recipes
    }

    fun buildPokedexInfoFor(speciesName: String): PokedexInfoRecipeData? {
        if (!SpawnDataIndex.shouldSurfaceSpecies(speciesName)) return null
        val info = SpawnDataIndex.getSpeciesInfo(speciesName) ?: return null
        return buildPokedexInfoFrom(speciesName, info)
    }

    private fun buildPokedexInfoFrom(species: String, info: EvolutionDataLoader.SpeciesBasicInfo): PokedexInfoRecipeData {
        return PokedexInfoRecipeData(
            speciesName = species,
            abilities = info.abilities ?: emptyList(),
            hiddenAbility = info.hiddenAbility,
            eggGroups = info.eggGroups ?: emptyList(),
            maleRatio = info.maleRatio,
            eggCycles = info.eggCycles,
            catchRate = info.catchRate,
            baseFriendship = info.baseFriendship,
            experienceGroup = info.experienceGroup,
            baseExperienceYield = info.baseExperienceYield,
            height = info.height,
            weight = info.weight,
            description = info.description,
            shoulderMountable = info.shoulderMountable,
            source = info.source
        )
    }

    // Cost budget per page. Move rows cost 1; a method section header (grouped layout only) costs 1.
    private const val MOVES_PER_PAGE = 10

    fun buildAllMovesRecipes(): List<MovesRecipeData> {
        val recipes = mutableListOf<MovesRecipeData>()
        for ((species, info) in SpawnDataIndex.speciesInfo) {
            if (!SpawnDataIndex.shouldSurfaceSpecies(species)) continue
            recipes.addAll(buildMovesPages(species, info))
        }
        return recipes
    }

    fun buildMovesFor(speciesName: String): List<MovesRecipeData> {
        if (!SpawnDataIndex.shouldSurfaceSpecies(speciesName)) return emptyList()
        val info = SpawnDataIndex.getSpeciesInfo(speciesName) ?: return emptyList()
        return buildMovesPages(speciesName, info)
    }

    private val GROUPED_METHOD_ORDER = mapOf("levelup" to 0, "egg" to 1, "tutor" to 2, "tm" to 3)

    /**
     * Collapse the four per-method learn lists into one [MoveEntry] per unique move (keyed by
     * lower-cased move name), merging every method that reaches it. First-seen [MoveDetail] wins;
     * all four lists resolve from the same Cobblemon move template, so the stats are identical.
     */
    internal fun mergeMoveEntries(info: EvolutionDataLoader.SpeciesBasicInfo): List<MoveEntry> {
        class Acc(val move: MoveDetail) {
            val levels = sortedSetOf<Int>()
            var egg = false
            var tutor = false
            var tm = false
        }
        val byName = LinkedHashMap<String, Acc>()
        fun slot(move: MoveDetail): Acc = byName.getOrPut(move.name.lowercase()) { Acc(move) }

        info.levelUpMoves?.forEach { lum -> lum.moves.forEach { slot(it).levels.add(lum.level) } }
        info.eggMoves?.forEach { slot(it).egg = true }
        info.tutorMoves?.forEach { slot(it).tutor = true }
        info.tmMoves?.forEach { slot(it).tm = true }

        return byName.values.map { MoveEntry(it.move, it.levels.toList(), it.egg, it.tutor, it.tm) }
    }

    private fun flatComparator(): Comparator<MoveEntry> =
        compareBy<MoveEntry>(
            { if (it.isLevelUp) 0 else 1 },
            { if (it.isLevelUp) it.levelUpLevels.min() else 0 },
            { it.move.name.lowercase() },
        )

    private fun groupedComparator(): Comparator<MoveEntry> =
        compareBy<MoveEntry>(
            { GROUPED_METHOD_ORDER[it.primaryMethod()] ?: Int.MAX_VALUE },
            { if (it.isLevelUp) it.levelUpLevels.min() else 0 },
            { it.move.name.lowercase() },
        )

    private fun buildMovesPages(species: String, info: EvolutionDataLoader.SpeciesBasicInfo): List<MovesRecipeData> {
        val entries = mergeMoveEntries(info)
        if (entries.isEmpty()) return emptyList()

        val grouped = CobbleDexConfig.get().groupMovesByMethod
        val ordered = entries.sortedWith(if (grouped) groupedComparator() else flatComparator())

        // Paginate by cost: every move row costs 1; in grouped mode a section header costs an extra
        // 1. The renderer re-emits a section header at the top of every page, so pagination charges
        // for the first group of each page too (prevMethod starts null).
        val pages = mutableListOf<List<MoveEntry>>()
        var i = 0
        while (i < ordered.size) {
            val page = mutableListOf<MoveEntry>()
            var cost = 0
            var prevMethod: String? = null
            while (i < ordered.size) {
                val entry = ordered[i]
                val method = entry.primaryMethod()
                val headerCost = if (grouped && method != prevMethod) 1 else 0
                if (page.isNotEmpty() && cost + headerCost + 1 > MOVES_PER_PAGE) break
                page.add(entry)
                cost += headerCost + 1
                prevMethod = method
                i++
            }
            pages.add(page)
        }

        val pageCount = pages.size
        return pages.mapIndexed { idx, pageEntries ->
            MovesRecipeData(species, pageEntries, idx + 1, pageCount, grouped)
        }
    }

    // --- Fossil recipes ---

    fun buildAllFossilRecipes(): List<FossilRecipeData> {
        val recipes = mutableListOf<FossilRecipeData>()
        for ((species, combos) in SpawnDataIndex.fossilsBySpecies) {
            if (!SpawnDataIndex.shouldSurfaceSpecies(species)) continue
            for (combo in combos) {
                recipes.add(FossilRecipeData(species, combo.fossilItems, combo.extraTags))
            }
        }
        return recipes
    }

    fun buildFossilsFor(speciesName: String): List<FossilRecipeData> {
        if (!SpawnDataIndex.shouldSurfaceSpecies(speciesName)) return emptyList()
        val combos = SpawnDataIndex.getFossilsFor(speciesName)
        if (combos.isEmpty()) return emptyList()
        return combos.map { FossilRecipeData(speciesName, it.fossilItems, it.extraTags) }
    }

    fun buildFossilRecipesForItem(itemId: String): List<FossilRecipeData> {
        return SpawnDataIndex.fossilsBySpecies.flatMap { (species, combos) ->
            if (!SpawnDataIndex.shouldSurfaceSpecies(species)) return@flatMap emptyList()
            combos.filter { it.fossilItems.contains(itemId) }
                .map { FossilRecipeData(species, it.fossilItems, it.extraTags) }
        }
    }

    // --- Type chart recipes ---

    fun buildAllTypeChartRecipes(): List<TypeChartRecipeData> {
        val recipes = mutableListOf<TypeChartRecipeData>()
        for ((species, info) in SpawnDataIndex.speciesInfo) {
            if (!SpawnDataIndex.shouldSurfaceSpecies(species)) continue
            val matchups = TypeChart.getMatchups(info.primaryType, info.secondaryType)
            recipes.add(TypeChartRecipeData(
                species, info.primaryType, info.secondaryType,
                matchups.weaknesses, matchups.resistances, matchups.immunities
            ))
        }
        return recipes
    }

    fun buildTypeChartFor(speciesName: String): TypeChartRecipeData? {
        if (!SpawnDataIndex.shouldSurfaceSpecies(speciesName)) return null
        val info = SpawnDataIndex.getSpeciesInfo(speciesName) ?: return null
        val matchups = TypeChart.getMatchups(info.primaryType, info.secondaryType)
        return TypeChartRecipeData(
            speciesName, info.primaryType, info.secondaryType,
            matchups.weaknesses, matchups.resistances, matchups.immunities
        )
    }

    // --- Nature table recipes ---

    fun buildNatureRecipes(): List<NatureRecipeData> {
        return listOf(NatureRecipeData(NatureData.NATURES))
    }

    // --- Pokemon description recipes ---

    fun buildAllDescriptionRecipes(): List<PokemonDescriptionRecipeData> {
        val recipes = mutableListOf<PokemonDescriptionRecipeData>()
        for ((species, info) in SpawnDataIndex.speciesInfo) {
            if (!SpawnDataIndex.shouldSurfaceSpecies(species)) continue
            val description = info.description ?: continue
            if (description.isBlank()) continue
            recipes.add(PokemonDescriptionRecipeData(species, description))
        }
        return recipes
    }

    fun buildDescriptionFor(speciesName: String): PokemonDescriptionRecipeData? {
        if (!SpawnDataIndex.shouldSurfaceSpecies(speciesName)) return null
        val info = SpawnDataIndex.getSpeciesInfo(speciesName) ?: return null
        val description = info.description ?: return null
        if (description.isBlank()) return null
        return PokemonDescriptionRecipeData(speciesName, description)
    }

    // --- Cobbleworkers Jobs ---

    fun buildAllJobsRecipes(): List<JobRecipeData> {
        if (!SpawnDataIndex.hasJobRules()) return emptyList()
        val recipes = mutableListOf<JobRecipeData>()
        for ((species, _) in SpawnDataIndex.speciesInfo) {
            if (!SpawnDataIndex.shouldSurfaceSpecies(species)) continue
            val matches = SpawnDataIndex.getJobsFor(species)
            for (match in matches) {
                recipes.add(JobRecipeData(species, match))
            }
        }
        return recipes
    }

    fun buildJobsFor(speciesName: String): List<JobRecipeData> {
        if (!SpawnDataIndex.shouldSurfaceSpecies(speciesName)) return emptyList()
        val matches = SpawnDataIndex.getJobsFor(speciesName)
        return matches.map { JobRecipeData(speciesName, it) }
    }

    // --- Move learner lookup ---
    //
    // Looking up a move (via its TM/egg/tutor disc, or from the Moves page) shows a grid of every
    // Pokémon that can learn it by ANY method — level-up, egg, tutor or TM — matching the data on the
    // per-species Moves page. Paginated into grids rather than one page per Pokémon (200+ learners).

    fun buildMoveLearnersForItem(itemId: String): List<MoveLearnersRecipeData> {
        val moveName = TmItemUtils.extractMove(itemId) ?: return emptyList()
        return buildMoveLearnersForMove(moveName)
    }

    /** Every move-learner grid, one family per move — used for "view all" browsing and panel sizing. */
    fun buildAllMoveLearnerRecipes(): List<MoveLearnersRecipeData> =
        SpawnDataIndex.speciesByMove.keys.sorted().flatMap { buildMoveLearnersForMove(it) }

    fun buildMoveLearnersForMove(moveName: String): List<MoveLearnersRecipeData> {
        val species = SpawnDataIndex.getSpeciesWithMove(moveName)
            .distinct()
            .filter { SpawnDataIndex.shouldSurfaceSpecies(it) && PokemonItemCache.canRender(it) }
            .sortedBy { SpawnDataIndex.getSpeciesInfo(it)?.nationalDexNumber?.takeIf { n -> n > 0 } ?: Int.MAX_VALUE }
        if (species.isEmpty()) return emptyList()

        var sharedDetail: MoveDetail? = null
        val learners = species.map { sp ->
            val info = SpawnDataIndex.getSpeciesInfo(sp)
            val methods = mutableListOf<LearnMethod>()

            info?.levelUpMoves?.forEach { entry ->
                entry.moves.firstOrNull { it.name.equals(moveName, ignoreCase = true) }?.let {
                    sharedDetail = sharedDetail ?: it
                    methods.add(LearnMethod("Level Up", "Lv. ${entry.level}"))
                }
            }
            info?.tmMoves?.firstOrNull { it.name.equals(moveName, ignoreCase = true) }?.let {
                sharedDetail = sharedDetail ?: it
                methods.add(LearnMethod("TM", null))
            }
            info?.eggMoves?.firstOrNull { it.name.equals(moveName, ignoreCase = true) }?.let {
                sharedDetail = sharedDetail ?: it
                methods.add(LearnMethod("Egg Move", null))
            }
            info?.tutorMoves?.firstOrNull { it.name.equals(moveName, ignoreCase = true) }?.let {
                sharedDetail = sharedDetail ?: it
                methods.add(LearnMethod("Tutor", null))
            }

            MoveLearner(sp, methods)
        }

        val pages = learners.chunked(SpawnDisplayHelper.MOVE_LEARNERS_PER_PAGE)
        return pages.mapIndexed { i, chunk ->
            MoveLearnersRecipeData(moveName, sharedDetail, chunk, i + 1, pages.size)
        }
    }

    // --- Alternate form recipes ---

    fun buildAllFormRecipes(
        snapshot: CobbleDexDataSnapshot = SpawnDataIndex.currentSnapshot(),
    ): List<FormRecipeData> {
        val queries = CobbleDexDataQueries(snapshot)
        val formsByBase = snapshot.speciesInfo.values
            .filter { info ->
                info.isForm && queries.shouldSurfaceSpecies(info.name)
            }
            .groupBy { info ->
                SpeciesNameNormalizer.normalize(info.baseSpeciesName!!)
            }

        return formsByBase.flatMap { (baseSpeciesName, forms) ->
            buildFormPages(baseSpeciesName, forms, queries)
        }
    }

    fun buildFormsFor(
        speciesName: String,
        snapshot: CobbleDexDataSnapshot = SpawnDataIndex.currentSnapshot(),
    ): List<FormRecipeData> {
        val queries = CobbleDexDataQueries(snapshot)
        val lookupInfo = queries.getSpeciesInfo(speciesName)
        val baseSpeciesName = SpeciesNameNormalizer.normalize(lookupInfo?.baseSpeciesName ?: speciesName)
        val forms = snapshot.speciesInfo.values.filter { info ->
            info.isForm &&
                SpeciesNameNormalizer.normalize(info.baseSpeciesName!!) == baseSpeciesName &&
                queries.shouldSurfaceSpecies(info.name)
        }
        if (forms.isEmpty()) return emptyList()
        return buildFormPages(baseSpeciesName, forms, queries)
    }

    private fun buildFormPages(
        baseSpeciesName: String,
        forms: List<EvolutionDataLoader.SpeciesBasicInfo>,
        queries: CobbleDexDataQueries,
    ): List<FormRecipeData> {
        val sortedForms = forms.sortedBy { formatSpeciesName(it.name) }
        val siblingFormKeys = sortedForms.map { info -> info.name }
        val totalPages = sortedForms.size
        val baseInfo = queries.getSpeciesInfo(baseSpeciesName)

        return sortedForms.mapIndexed { index, info ->
            FormRecipeData(
                baseSpeciesName = baseSpeciesName,
                form = toFormEntry(info.name, info),
                baseInfo = baseInfo,
                siblingFormKeys = siblingFormKeys,
                differenceReasons = describeFormDifferences(
                    queries.materialFormDecision(info.name)?.reasons.orEmpty()
                ),
                pageIndex = index + 1,
                pageTotal = totalPages,
                totalForms = totalPages,
            )
        }
    }

    private fun toFormEntry(key: String, info: EvolutionDataLoader.SpeciesBasicInfo) = FormInfoEntry(
        formKey = key,
        formDisplayName = formatSpeciesName(key),
        primaryType = info.primaryType,
        secondaryType = info.secondaryType,
        abilities = info.abilities ?: emptyList(),
        hiddenAbility = info.hiddenAbility,
        baseStats = info.baseStats,
        baseStatTotal = info.baseStatTotal,
        formAspects = info.formAspects
    )

    private fun buildEvolutionTargets(
        sourceSpeciesName: String,
        sourceAspects: Set<String>,
        snapshot: CobbleDexDataSnapshot,
        queries: CobbleDexDataQueries,
    ): List<EvolutionTargetPage> {
        val exactAspects = normalizeAspects(sourceAspects)
        val directTargets = queries.getEvolutionsFrom(sourceSpeciesName)
            .filter { evolution -> normalizeAspects(evolution.fromAspects) == exactAspects }
            .groupBy { evolution -> resolveEvolutionTargetKey(evolution.toSpecies, evolution.toAspects, snapshot, queries) }
            .map { (targetSpeciesName, evolutions) ->
                EvolutionTargetPage(
                    targetSpeciesName = targetSpeciesName,
                    targetAspects = evolutions.first().toAspects,
                    methods = evolutions.map { evolution ->
                        EvolutionMethodRecipeData(
                            requirementText = evolution.displayRequirements,
                            itemRequirements = evolution.itemRequirements,
                        )
                    }.distinctBy { method ->
                        method.requirementText to method.itemRequirements.map(EvolutionItemInfo::itemId)
                    },
                    priority = 0,
                )
            }

        // Mega/Mega-Z/Gmax-style transforms have no real Cobblemon Evolution
        // data behind them at all (confirmed via /cobbledex forms: Lucario's
        // 23 forms and its species.evolutions/form.evolutions are all empty) -
        // Cobblemon just registers them as forms with a distinguishing aspect.
        // So the only way to find these is to compare aspect sets across the
        // whole family (base + every sibling form) and infer parent/child
        // transform edges from them - see resolveTransformParent's comment.
        val sourceAspectsRaw = sourceAspects.map { it.lowercase() }.toSet()
        val family = buildFamilyAspectMembers(sourceSpeciesName, queries)
        val transformTargets = family.mapNotNull { (targetKey, targetAspects) ->
            if (targetAspects == sourceAspectsRaw || !targetAspects.containsAll(sourceAspectsRaw)) return@mapNotNull null
            val parent = findTransformParent(targetAspects, family) ?: return@mapNotNull null
            if (parent.second != sourceAspectsRaw) return@mapNotNull null
            EvolutionTargetPage(
                targetSpeciesName = targetKey,
                targetAspects = targetAspects,
                methods = listOf(EvolutionMethodRecipeData(parent.third)),
                priority = 1,
            )
        }

        return (directTargets + transformTargets)
            .distinctBy { target -> target.targetSpeciesName }
            .sortedWith(
                compareBy<EvolutionTargetPage>({ it.priority }, { targetSortWeight(it.targetSpeciesName, snapshot) }, { formatSpeciesName(it.targetSpeciesName) })
            )
    }

    // Not private: DerivedDataBuilder reuses this to index the "what evolves
    // into X" reverse map by the exact aspect-qualified form (e.g.
    // "lucario_midnight"), not just the bare species name - see its usage there.
    fun resolveEvolutionTargetKey(
        targetSpeciesName: String,
        targetAspects: Set<String>,
        snapshot: CobbleDexDataSnapshot,
        queries: CobbleDexDataQueries,
    ): String {
        val normalizedTarget = SpeciesNameNormalizer.normalize(targetSpeciesName)
        if (targetAspects.isEmpty()) return normalizedTarget

        val targetAspectKey = normalizeAspects(targetAspects)
        val resolvedForm = queries.findFormByAspects(normalizedTarget, targetAspectKey)?.name

        return when {
            resolvedForm == null -> normalizedTarget
            queries.shouldSurfaceSpecies(resolvedForm) -> resolvedForm
            else -> normalizedTarget
        }
    }

    private fun normalizeAspects(aspects: Set<String>): Set<String> =
        aspects.map { aspect ->
            aspect.lowercase().replace(Regex("[^a-z0-9]"), "")
        }.filter { it.isNotBlank() }.toSet()

    // (speciesKey, raw lowercase aspects) for the base species plus every
    // sibling form - used to infer mega/primal/gmax-style transform edges
    // by comparing aspect sets directly, since Cobblemon doesn't back these
    // with real Evolution data (see buildEvolutionTargets' comment). Aspects
    // are kept raw/unstripped here (not run through normalizeAspects) so
    // markers like "mega_z" or "chef-costume" stay recognizable by prefix/
    // suffix in transformMethodTextFor and the costume filter upstream.
    private fun buildFamilyAspectMembers(
        speciesName: String,
        queries: CobbleDexDataQueries,
    ): List<Pair<String, Set<String>>> {
        val realBaseName = queries.getSpeciesInfo(speciesName)?.baseSpeciesName
            ?.let(SpeciesNameNormalizer::normalize)
            ?: SpeciesNameNormalizer.normalize(speciesName)
        val siblings = queries.getFormsOf(realBaseName).map { info ->
            info.name to info.formAspects.map { it.lowercase() }.toSet()
        }
        return listOf(realBaseName to emptySet<String>()) + siblings
    }

    private val TRANSFORM_ASPECT_MARKERS = mapOf(
        "cobbledex-rei-emi-jei.evo.form_change.mega" to setOf("mega"),
        "cobbledex-rei-emi-jei.evo.form_change.primal" to setOf("primal"),
        "cobbledex-rei-emi-jei.evo.form_change.ultra_burst" to setOf("ultra_burst"),
        "cobbledex-rei-emi-jei.evo.form_change.gmax" to setOf("gmax", "gigantamax"),
    )

    // Recognizes not just an exact aspect match ("mega") but also namespaced
    // variants a mod might register for custom mega-likes ("mega_z",
    // "mega-y") so third-party additions still get labeled sensibly instead
    // of being silently dropped from the evolution chain.
    private fun transformMethodTextFor(addedAspects: Set<String>): String? {
        for ((trKey, markers) in TRANSFORM_ASPECT_MARKERS) {
            val matches = addedAspects.any { aspect ->
                markers.any { marker -> aspect == marker || aspect.startsWith("${marker}_") || aspect.startsWith("${marker}-") }
            }
            if (matches) return tr(trKey)
        }
        return null
    }

    // Finds the closest family member whose aspect set is a proper subset of
    // targetAspects and whose "new" aspects (relative to that member) read as
    // a mega/primal/gmax-style transform. "Closest" = the candidate with the
    // largest aspect set, so e.g. a "Mega Midnight" form (aspects=[mega_y,y])
    // resolves to "Midnight" (aspects=[y]) as its parent rather than jumping
    // all the way back to the base species - both are technically subsets,
    // but Midnight is the direct one.
    private fun findTransformParent(
        targetAspects: Set<String>,
        familyMembers: List<Pair<String, Set<String>>>,
    ): Triple<String, Set<String>, String>? {
        var best: Triple<String, Set<String>, String>? = null
        for ((key, aspects) in familyMembers) {
            if (aspects == targetAspects || !targetAspects.containsAll(aspects)) continue
            val methodText = transformMethodTextFor(targetAspects - aspects) ?: continue
            if (best == null || aspects.size > best.second.size) {
                best = Triple(key, aspects, methodText)
            }
        }
        return best
    }

    private fun resolveTransformParent(
        speciesName: String,
        speciesAspects: Set<String>,
        queries: CobbleDexDataQueries,
    ): Triple<String, Set<String>, String>? {
        val family = buildFamilyAspectMembers(speciesName, queries)
        val targetAspects = speciesAspects.map { it.lowercase() }.toSet()
        return findTransformParent(targetAspects, family)
    }

    private fun resolveEvolutionSourceKey(
        evolution: EvolutionInfo,
        queries: CobbleDexDataQueries,
    ): String {
        val normalizedSource = SpeciesNameNormalizer.normalize(evolution.fromSpecies)
        if (evolution.fromAspects.isEmpty()) return normalizedSource
        return queries.findFormByAspects(normalizedSource, evolution.fromAspects)?.name ?: normalizedSource
    }

    private fun targetSortWeight(
        targetSpeciesName: String,
        snapshot: CobbleDexDataSnapshot,
    ): Int = snapshot.speciesInfo[SpeciesNameNormalizer.normalize(targetSpeciesName)]?.nationalDexNumber ?: Int.MAX_VALUE

    private fun describeFormDifferences(reasons: List<String>): List<String> {
        if (reasons.isEmpty()) return listOf("Distinct material form.")

        return reasons.distinct()
            .sortedBy { reason -> FORM_REASON_PRIORITY[reason.lowercase()] ?: Int.MAX_VALUE }
            .map { reason ->
                when (reason.lowercase()) {
                    "typing" -> "Typing differs from the base species."
                    "abilities" -> "Ability pool differs from the base species."
                    "base stats" -> "Individual base stats differ from the base species."
                    "base stat total" -> "Base stat total differs from the base species."
                    "ev yield" -> "EV yield differs from the base species."
                    "catch rate" -> "Catch rate differs from the base species."
                    "body metrics" -> "Height or weight differs from the base species."
                    "egg groups" -> "Egg groups differ from the base species."
                    "gender ratio" -> "Gender ratio differs from the base species."
                    "egg cycles" -> "Egg cycles differ from the base species."
                    "experience group" -> "Experience group differs from the base species."
                    "base experience" -> "Base experience yield differs from the base species."
                    "base friendship" -> "Base friendship differs from the base species."
                    "labels" -> "Special labels differ from the base species."
                    "drops" -> "Drop table differs from the base species."
                    "level-up moves" -> "Level-up move list differs from the base species."
                    "egg moves" -> "Egg move list differs from the base species."
                    "tutor moves" -> "Tutor move list differs from the base species."
                    "tm moves" -> "TM compatibility differs from the base species."
                    "shoulder mount" -> "Shoulder-mount support differs from the base species."
                    "form-specific spawn data" -> "Spawn data differs from the base species."
                    "form-specific obtainment data" -> "Obtainment routes differ from the base species."
                    "form-specific evolution data" -> "Evolution routes differ from the base species."
                    "form-specific fossil data" -> "Fossil data differs from the base species."
                    "form-specific riding data" -> "Riding data differs from the base species."
                    else -> "${reason.replaceFirstChar { ch -> ch.uppercase() }}."
                }
            }
    }

    // --- Riding recipes ---

    fun buildAllRidingRecipes(): List<RidingRecipeData> {
        return SpawnDataIndex.ridingBySpecies.flatMap { (species, info) ->
            if (!SpawnDataIndex.shouldSurfaceSpecies(species)) return@flatMap emptyList()
            info.mounts.mapIndexed { idx, mount ->
                RidingRecipeData(species, mount, idx, info.mounts.size, info.seats, info.allMountTypes)
            }
        }
    }

    fun buildRidingFor(speciesName: String): List<RidingRecipeData> {
        if (!SpawnDataIndex.shouldSurfaceSpecies(speciesName)) return emptyList()
        val info = SpawnDataIndex.getRidingFor(speciesName) ?: return emptyList()
        return info.mounts.mapIndexed { idx, mount ->
            RidingRecipeData(speciesName, mount, idx, info.mounts.size, info.seats, info.allMountTypes)
        }
    }
}
