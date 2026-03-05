package com.cobbledex

object RecipeBuilder {

    fun buildSpawnRecipes(species: String, spawns: List<SpawnInfo>): List<SpawnRecipeData> {
        return SpawnDisplayHelper.buildSortedSpawns(spawns).mapNotNull { entry ->
            try {
                SpawnRecipeData(species, entry.spawn, entry.formVariants, entry.bucketIndex, entry.bucketTotal)
            } catch (e: Exception) {
                DebugLog.once("recipe-spawn-$species-${entry.spawn.id}") { "Failed: ${e.message}" }
                null
            }
        }
    }

    fun buildAllObtainmentRecipes(): List<ObtainmentRecipeData> {
        val recipes = mutableListOf<ObtainmentRecipeData>()
        for ((species, obtainments) in SpawnDataIndex.obtainmentBySpecies) {
            if (obtainments.isEmpty()) continue
            obtainments.forEachIndexed { i, info ->
                recipes.add(ObtainmentRecipeData(species, info, i + 1, obtainments.size))
            }
        }
        return recipes
    }

    fun buildObtainmentsFor(species: String, obtainments: List<ObtainmentInfo>): List<ObtainmentRecipeData> {
        if (obtainments.isEmpty()) return emptyList()
        return obtainments.mapIndexed { i, info ->
            ObtainmentRecipeData(species, info, i + 1, obtainments.size)
        }
    }

    fun buildAllDropRecipes(): List<DropRecipeData> {
        val recipes = mutableListOf<DropRecipeData>()
        for ((species, info) in SpawnDataIndex.speciesInfo) {
            val drops = info.drops ?: continue
            if (drops.isEmpty()) continue
            recipes.add(DropRecipeData(species, drops))
        }
        return recipes
    }

    fun buildDropsFor(speciesName: String): List<DropRecipeData> {
        val info = SpawnDataIndex.getSpeciesInfo(speciesName) ?: return emptyList()
        val drops = info.drops ?: return emptyList()
        if (drops.isEmpty()) return emptyList()
        return listOf(DropRecipeData(speciesName, drops))
    }

    fun buildDropRecipesForItem(itemId: String): List<DropRecipeData> {
        val species = SpawnDataIndex.getSpeciesDroppingItem(itemId)
        if (species.isEmpty()) return emptyList()
        return species.mapNotNull { buildDropsFor(it).firstOrNull() }
    }

    fun buildAllStatsRecipes(): List<StatsRecipeData> {
        val recipes = mutableListOf<StatsRecipeData>()
        for ((species, info) in SpawnDataIndex.speciesInfo) {
            val stats = info.baseStats ?: continue
            val bst = info.baseStatTotal ?: continue
            if (stats.isEmpty()) continue
            recipes.add(StatsRecipeData(species, stats, bst, info.primaryType, info.secondaryType, info.evYield))
        }
        return recipes
    }

    fun buildStatsFor(speciesName: String): StatsRecipeData? {
        val info = SpawnDataIndex.getSpeciesInfo(speciesName) ?: return null
        val stats = info.baseStats ?: return null
        val bst = info.baseStatTotal ?: return null
        if (stats.isEmpty()) return null
        return StatsRecipeData(speciesName, stats, bst, info.primaryType, info.secondaryType, info.evYield)
    }

    fun buildAllPokedexInfoRecipes(): List<PokedexInfoRecipeData> {
        val recipes = mutableListOf<PokedexInfoRecipeData>()
        for ((species, info) in SpawnDataIndex.speciesInfo) {
            recipes.add(buildPokedexInfoFrom(species, info))
        }
        return recipes
    }

    fun buildPokedexInfoFor(speciesName: String): PokedexInfoRecipeData? {
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

    private const val MOVES_PER_PAGE = 14

    fun buildAllMovesRecipes(): List<MovesRecipeData> {
        val recipes = mutableListOf<MovesRecipeData>()
        for ((species, info) in SpawnDataIndex.speciesInfo) {
            recipes.addAll(buildMovesPages(species, info))
        }
        return recipes
    }

    fun buildMovesFor(speciesName: String): List<MovesRecipeData> {
        val info = SpawnDataIndex.getSpeciesInfo(speciesName) ?: return emptyList()
        return buildMovesPages(speciesName, info)
    }

    private sealed class MoveLine {
        data class LevelUpLine(val level: Int, val move: MoveDetail) : MoveLine()
        data class EggLine(val move: MoveDetail) : MoveLine()
        data class TutorLine(val move: MoveDetail) : MoveLine()
        data class TmLine(val move: MoveDetail) : MoveLine()
    }

    private fun buildMovesPages(species: String, info: EvolutionDataLoader.SpeciesBasicInfo): List<MovesRecipeData> {
        val levelUp = info.levelUpMoves ?: emptyList()
        val egg = info.eggMoves ?: emptyList()
        val tutor = info.tutorMoves ?: emptyList()
        val tm = info.tmMoves ?: emptyList()
        if (levelUp.isEmpty() && egg.isEmpty() && tutor.isEmpty() && tm.isEmpty()) return emptyList()

        val allLines = mutableListOf<MoveLine>()
        for (entry in levelUp) {
            for (move in entry.moves) {
                allLines.add(MoveLine.LevelUpLine(entry.level, move))
            }
        }
        egg.forEach { allLines.add(MoveLine.EggLine(it)) }
        tutor.forEach { allLines.add(MoveLine.TutorLine(it)) }
        tm.forEach { allLines.add(MoveLine.TmLine(it)) }

        if (allLines.size <= MOVES_PER_PAGE) {
            return listOf(MovesRecipeData(species, levelUp, egg, tutor, tm, 1, 1))
        }

        // Reserve 1 line for each section header that starts on a page
        val pages = mutableListOf<List<MoveLine>>()
        var i = 0
        while (i < allLines.size) {
            val page = mutableListOf<MoveLine>()
            var linesUsed = 0
            // Check if a new section starts on this page (needs header line)
            var prevType: String? = if (pages.isEmpty()) null else pages.last().lastOrNull()?.let { lineType(it) }
            while (i < allLines.size && linesUsed < MOVES_PER_PAGE) {
                val line = allLines[i]
                val curType = lineType(line)
                val needsHeader = curType != "levelup" && curType != prevType && !page.any { lineType(it) == curType }
                val cost = if (needsHeader) 2 else 1  // header + move line
                if (linesUsed + cost > MOVES_PER_PAGE && page.isNotEmpty()) break
                page.add(line)
                linesUsed += cost
                prevType = curType
                i++
            }
            pages.add(page)
        }

        val pageCount = pages.size
        return pages.mapIndexed { idx, pageLines ->
            val pageLevelUp = mutableListOf<LevelUpMove>()
            val pageEgg = mutableListOf<MoveDetail>()
            val pageTutor = mutableListOf<MoveDetail>()
            val pageTm = mutableListOf<MoveDetail>()

            var currentLevel = -1
            var currentMoves = mutableListOf<MoveDetail>()
            for (line in pageLines) {
                when (line) {
                    is MoveLine.LevelUpLine -> {
                        if (line.level != currentLevel) {
                            if (currentMoves.isNotEmpty()) pageLevelUp.add(LevelUpMove(currentLevel, currentMoves))
                            currentLevel = line.level
                            currentMoves = mutableListOf()
                        }
                        currentMoves.add(line.move)
                    }
                    is MoveLine.EggLine -> pageEgg.add(line.move)
                    is MoveLine.TutorLine -> pageTutor.add(line.move)
                    is MoveLine.TmLine -> pageTm.add(line.move)
                }
            }
            if (currentMoves.isNotEmpty()) pageLevelUp.add(LevelUpMove(currentLevel, currentMoves))

            MovesRecipeData(species, pageLevelUp, pageEgg, pageTutor, pageTm, idx + 1, pageCount)
        }
    }

    private fun lineType(line: MoveLine): String = when (line) {
        is MoveLine.LevelUpLine -> "levelup"
        is MoveLine.EggLine -> "egg"
        is MoveLine.TutorLine -> "tutor"
        is MoveLine.TmLine -> "tm"
    }

    // --- Fossil recipes ---

    fun buildAllFossilRecipes(): List<FossilRecipeData> {
        val recipes = mutableListOf<FossilRecipeData>()
        for ((species, combos) in SpawnDataIndex.fossilsBySpecies) {
            for (combo in combos) {
                recipes.add(FossilRecipeData(species, combo.fossilItems, combo.extraTags))
            }
        }
        return recipes
    }

    fun buildFossilsFor(speciesName: String): List<FossilRecipeData> {
        val combos = SpawnDataIndex.getFossilsFor(speciesName)
        if (combos.isEmpty()) return emptyList()
        return combos.map { FossilRecipeData(speciesName, it.fossilItems, it.extraTags) }
    }

    // --- Type chart recipes ---

    fun buildAllTypeChartRecipes(): List<TypeChartRecipeData> {
        val recipes = mutableListOf<TypeChartRecipeData>()
        for ((species, info) in SpawnDataIndex.speciesInfo) {
            val matchups = TypeChart.getMatchups(info.primaryType, info.secondaryType)
            recipes.add(TypeChartRecipeData(
                species, info.primaryType, info.secondaryType,
                matchups.weaknesses, matchups.resistances, matchups.immunities
            ))
        }
        return recipes
    }

    fun buildTypeChartFor(speciesName: String): TypeChartRecipeData? {
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
            val description = info.description ?: continue
            if (description.isBlank()) continue
            recipes.add(PokemonDescriptionRecipeData(species, description))
        }
        return recipes
    }

    fun buildDescriptionFor(speciesName: String): PokemonDescriptionRecipeData? {
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
            val matches = SpawnDataIndex.getJobsFor(species)
            for (match in matches) {
                recipes.add(JobRecipeData(species, match))
            }
        }
        return recipes
    }

    fun buildJobsFor(speciesName: String): List<JobRecipeData> {
        val matches = SpawnDataIndex.getJobsFor(speciesName)
        return matches.map { JobRecipeData(speciesName, it) }
    }

    // --- TM learner lookup ---

    fun buildTmLearnersForItem(itemId: String): List<TmLearnerRecipeData> {
        val moveName = TmItemUtils.extractMove(itemId) ?: return emptyList()
        val species = SpawnDataIndex.getSpeciesWithTmMove(moveName)
        if (species.isEmpty()) return emptyList()

        val total = species.size
        return species.mapIndexed { i, sp ->
            val info = SpawnDataIndex.getSpeciesInfo(sp)
            val methods = mutableListOf<LearnMethod>()
            var moveDetail: MoveDetail? = null

            // Check level-up
            info?.levelUpMoves?.forEach { entry ->
                entry.moves.firstOrNull { it.name.equals(moveName, ignoreCase = true) }?.let {
                    moveDetail = moveDetail ?: it
                    methods.add(LearnMethod("Level Up", "Lv. ${entry.level}"))
                }
            }

            // Check TM
            info?.tmMoves?.firstOrNull { it.name.equals(moveName, ignoreCase = true) }?.let {
                moveDetail = moveDetail ?: it
                methods.add(LearnMethod("TM", null))
            }

            // Check egg
            info?.eggMoves?.firstOrNull { it.name.equals(moveName, ignoreCase = true) }?.let {
                moveDetail = moveDetail ?: it
                methods.add(LearnMethod("Egg Move", null))
            }

            // Check tutor
            info?.tutorMoves?.firstOrNull { it.name.equals(moveName, ignoreCase = true) }?.let {
                moveDetail = moveDetail ?: it
                methods.add(LearnMethod("Tutor", null))
            }

            TmLearnerRecipeData(sp, moveName, moveDetail, methods, i + 1, total)
        }
    }

    // --- Alternate form recipes ---

    fun buildAllFormRecipes(): List<FormRecipeData> {
        val formsByBase = mutableMapOf<String, MutableList<FormInfoEntry>>()
        for ((key, info) in SpawnDataIndex.speciesInfo) {
            val base = info.baseSpeciesName ?: continue
            formsByBase.getOrPut(SpeciesNameNormalizer.normalize(base)) { mutableListOf() }
                .add(toFormEntry(key, info))
        }
        return formsByBase.map { (base, forms) -> FormRecipeData(base, forms) }
    }

    fun buildFormsFor(speciesName: String): FormRecipeData? {
        val normalized = SpeciesNameNormalizer.normalize(speciesName)
        val forms = mutableListOf<FormInfoEntry>()
        for ((key, info) in SpawnDataIndex.speciesInfo) {
            if (info.baseSpeciesName == null) continue
            if (SpeciesNameNormalizer.normalize(info.baseSpeciesName) != normalized) continue
            forms.add(toFormEntry(key, info))
        }
        if (forms.isEmpty()) return null
        return FormRecipeData(normalized, forms)
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
}
