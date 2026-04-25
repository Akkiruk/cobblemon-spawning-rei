package com.cobbledex

import net.minecraft.client.Minecraft

object SpawnPageBuilder {
    fun sortedSpawns(spawns: List<SpawnInfo>): List<SpawnDisplayHelper.SortedSpawnEntry> =
        SpawnDisplayHelper.buildSortedSpawns(spawns)

    fun build(data: SpawnRecipeData): PanelLayout =
        SpawnDisplayHelper.buildSpawnLayout(
            data.speciesName,
            data.spawn,
            data.mergedFormVariants,
            data.bucketIndex,
            data.bucketTotal,
        )
}

object EvolutionPageBuilder {
    fun build(chain: EvolutionChainBuilder.ChainNode): SpawnDisplayHelper.ChainLayoutResult =
        SpawnDisplayHelper.buildEvolutionChainLayout(chain)
}

object ObtainmentPageBuilder {
    fun buildSpecial(data: ObtainmentRecipeData): PanelLayout =
        SpawnDisplayHelper.buildObtainmentLayout(data.speciesName, data.obtainment, data.entryIndex, data.entryTotal)

    fun buildUnified(data: UnifiedObtainmentRecipeData): PanelLayout {
        val font = Minecraft.getInstance().font
        val padding = PanelLayout.PADDING
        val headerTag = "How to Obtain"
        val speciesName = formatSpeciesName(data.speciesName)
        val width = maxOf(
            PanelLayout.TEXT_START_X + font.width(speciesName) + 8 + font.width(headerTag) + padding,
            236,
            PanelLayout.MIN_WIDTH,
        ).coerceAtMost(PanelLayout.MAX_WIDTH)
        val layout = PanelLayout(width)
        val right = layout.right
        val indentX = padding + 4
        val detailX = padding + 10
        val detailWidth = right - detailX

        layout.textAt(padding + 22, 6, speciesName, 0xFFFFFF)
        layout.textRightAt(6, headerTag, 0xDDCC99)
        layout.fill(padding, 20, right, 21, 0x50FFFFFF)
        layout.skipTo(27)

        if (data.routes.isEmpty()) {
            layout.wrapped(indentX, "No known spawn, fossil, evolution, or special obtainment route in the current data snapshot.", right - indentX, 0xBBBBBB)
        } else {
            data.routes.forEachIndexed { index, route ->
                drawRoute(layout, route, indentX, detailX, detailWidth)
                if (index != data.routes.lastIndex) {
                    layout.gap(3)
                    layout.separator(0x18FFFFFF)
                    layout.gap(4)
                }
            }
        }

        layout.gap(1)
        layout.separator(0x20FFFFFF)
        layout.gap(4)
        if (data.pageTotal > 1) {
            layout.text(padding, "${data.pageIndex}/${data.pageTotal}", 0xFFAA00)
        }
        layout.textRight("${data.totalRoutes} route${if (data.totalRoutes == 1) "" else "s"}", 0x888888)
        layout.gap(font.lineHeight + padding)

        return layout
    }

    private fun drawRoute(layout: PanelLayout, route: ObtainmentRoute, indentX: Int, detailX: Int, detailWidth: Int) {
        val color = when (route) {
            is ObtainmentRoute.WildSpawns -> 0x88DD88
            is ObtainmentRoute.Special -> 0xDDCC99
            is ObtainmentRoute.Fossil -> 0xCCAAFF
            is ObtainmentRoute.Evolution -> 0xFFCC66
        }
        layout.text(indentX, routeTitle(route), color)
        layout.line()
        for (line in routeLines(route)) {
            layout.wrapped(detailX, line, detailWidth, 0xDDDDDD)
        }
        val items = route.itemIds.distinct()
        if (items.isNotEmpty()) {
            val itemNames = items.joinToString(", ") { SpawnDisplayHelper.resolveItemName(it) }
            layout.wrapped(detailX, "Items: $itemNames", detailWidth, 0xFFCC66)
        }
    }

    private fun routeTitle(route: ObtainmentRoute): String = when (route) {
        is ObtainmentRoute.WildSpawns -> "Wild spawns"
        is ObtainmentRoute.Special -> route.obtainment.displayMethodName
        is ObtainmentRoute.Fossil -> "Fossil restoration"
        is ObtainmentRoute.Evolution -> "Evolution"
    }

    private fun routeLines(route: ObtainmentRoute): List<String> = when (route) {
        is ObtainmentRoute.WildSpawns -> wildSpawnLines(route)
        is ObtainmentRoute.Special -> specialLines(route.obtainment)
        is ObtainmentRoute.Fossil -> fossilLines(route.combo)
        is ObtainmentRoute.Evolution -> evolutionLines(route.evolution)
    }

    private fun wildSpawnLines(route: ObtainmentRoute.WildSpawns): List<String> {
        val entries = route.entries
        val bucketText = entries
            .groupingBy { it.spawn.bucket.lowercase() }
            .eachCount()
            .entries
            .sortedWith(compareBy<Map.Entry<String, Int>> { SpawnDisplayHelper.bucketSortOrder(it.key) }.thenBy { it.key })
            .joinToString(", ") { "${SpawnDisplayHelper.bucketLabel(it.key)} ${it.value}" }
        val contexts = entries.map { it.spawn.displayContext }.distinct().take(4)
        val dimensions = entries.flatMap { it.spawn.dimensions }.distinct().take(3)
        val biomeCount = entries.flatMap { it.spawn.biomes }.distinct().size
        return buildList {
            add("${entries.size} spawn table entr${if (entries.size == 1) "y" else "ies"}${if (bucketText.isNotBlank()) ": $bucketText" else ""}")
            if (contexts.isNotEmpty()) add("Contexts: ${contexts.joinToString(", ")}")
            if (dimensions.isNotEmpty()) add("Dimensions: ${dimensions.joinToString(", ") { SpawnDisplayHelper.formatDimension(it) }}")
            if (biomeCount > 0) add("Biome coverage: $biomeCount biome${if (biomeCount == 1) "" else "s"}")
        }
    }

    private fun specialLines(obtainment: ObtainmentInfo): List<String> = buildList {
        add(obtainment.displayDescription)
        obtainment.displayBlock?.let { add(obtainmentUseText(it)) }
        obtainment.displayStructure?.let { add(obtainmentStructureText(it)) }
        obtainment.displayDimension?.let { add(obtainmentDimensionText(it)) }
        addAll(obtainment.displayNotes)
    }

    private fun fossilLines(combo: FossilCombo): List<String> = buildList {
        add("Restore from ${combo.fossilItems.joinToString(", ") { SpawnDisplayHelper.resolveItemName(it) }}.")
        combo.extraTags?.takeIf { it.isNotBlank() }?.let { add("Extra tags: $it") }
    }

    private fun evolutionLines(evolution: EvolutionInfo): List<String> = buildList {
        add("Evolve from ${evolution.displayFromName}: ${evolution.displayRequirements}")
        val textOnly = evolution.textOnlyRequirements
        if (textOnly.isNotBlank() && textOnly != evolution.displayRequirements) add(textOnly)
    }
}

object DropPageBuilder {
    fun build(data: DropRecipeData): PanelLayout = SpawnDisplayHelper.buildDropLayout(data)
}

object PokemonInfoPageBuilder {
    fun buildStats(data: StatsRecipeData): PanelLayout =
        SpawnDisplayHelper.buildStatsLayout(data.speciesName, data.baseStats, data.baseStatTotal, data.primaryType, data.secondaryType, data.evYield)

    fun buildPokedex(data: PokedexInfoRecipeData): PanelLayout = SpawnDisplayHelper.buildPokedexInfoLayout(data)
    fun buildDescription(data: PokemonDescriptionRecipeData): PanelLayout = SpawnDisplayHelper.buildPokemonDescriptionLayout(data)
    fun buildTypeChart(data: TypeChartRecipeData): PanelLayout = SpawnDisplayHelper.buildTypeChartLayout(data)
    fun buildForms(data: FormRecipeData): SpawnDisplayHelper.FormLayoutResult = SpawnDisplayHelper.buildFormLayout(data)
    fun buildRiding(data: RidingRecipeData): PanelLayout = SpawnDisplayHelper.buildRidingLayout(data.speciesName, data)
}

object MovePageBuilder {
    fun buildMoves(data: MovesRecipeData): PanelLayout = SpawnDisplayHelper.buildMovesLayout(data)
    fun buildTmLearner(data: TmLearnerRecipeData): PanelLayout = SpawnDisplayHelper.buildTmLearnerLayout(data)
}

object MechanicPageBuilder {
    fun buildFossil(data: FossilRecipeData): PanelLayout = SpawnDisplayHelper.buildFossilLayout(data)
    fun buildNature(data: NatureRecipeData): PanelLayout = SpawnDisplayHelper.buildNatureLayout(data)
    fun buildJob(speciesName: String, match: JobMatch): PanelLayout = SpawnDisplayHelper.buildJobLayout(speciesName, match)
}