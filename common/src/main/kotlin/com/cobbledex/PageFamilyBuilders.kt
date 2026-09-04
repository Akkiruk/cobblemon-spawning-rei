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
    fun build(data: EvolutionRecipeData): SpawnDisplayHelper.EvolutionLayoutResult =
        SpawnDisplayHelper.buildEvolutionLayout(data)

    fun build(chain: EvolutionChainBuilder.ChainNode): SpawnDisplayHelper.ChainLayoutResult =
        SpawnDisplayHelper.buildEvolutionChainLayout(chain)
}

object ObtainmentPageBuilder {
    const val UNIFIED_ROUTE_SPACING = 8

    fun buildSpecial(data: ObtainmentRecipeData): PanelLayout =
        SpawnDisplayHelper.buildObtainmentLayout(data.speciesName, data.obtainment, data.entryIndex, data.entryTotal)

    fun measureUnifiedFixedHeight(): Int {
        val font = Minecraft.getInstance().font
        return 27 + 1 + 1 + 4 + font.lineHeight + PanelLayout.PADDING
    }

    fun measureUnifiedRouteHeight(speciesName: String, route: ObtainmentRoute): Int {
        val font = Minecraft.getInstance().font
        val detailWidth = unifiedDetailWidth(speciesName)
        var height = PanelLayout.LINE_HEIGHT

        for (line in routeLines(route)) {
            height += SpawnDisplayHelper.wrapText(font, line, detailWidth).size.coerceAtLeast(1) * PanelLayout.LINE_HEIGHT
        }

        val items = route.itemIds.distinct()
        if (items.isNotEmpty()) {
            val itemNames = items.joinToString(", ") { SpawnDisplayHelper.resolveItemName(it) }
            height += SpawnDisplayHelper.wrapText(font, "Items: $itemNames", detailWidth).size.coerceAtLeast(1) * PanelLayout.LINE_HEIGHT
        }

        return height
    }

    fun buildUnified(data: UnifiedObtainmentRecipeData): PanelLayout {
        val font = Minecraft.getInstance().font
        val padding = PanelLayout.PADDING
        val headerTag = "How to Obtain"
        val speciesName = formatSpeciesName(data.speciesName)
        val width = unifiedWidth(data.speciesName)
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
            layout.wrapped(indentX, "No special obtainment route for this Pokémon.", right - indentX, 0xBBBBBB)
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

    private fun unifiedWidth(speciesName: String): Int {
        val font = Minecraft.getInstance().font
        val padding = PanelLayout.PADDING
        val headerTag = "How to Obtain"
        val displayName = formatSpeciesName(speciesName)
        return SpawnDisplayHelper.computePanelWidth(
            PanelLayout.TEXT_START_X + font.width(displayName) + 8 + font.width(headerTag) + padding,
            236,
        )
    }

    private fun unifiedDetailWidth(speciesName: String): Int {
        val padding = PanelLayout.PADDING
        val width = unifiedWidth(speciesName)
        val right = width - padding
        val detailX = padding + 10
        return right - detailX
    }

    private fun drawRoute(layout: PanelLayout, route: ObtainmentRoute, indentX: Int, detailX: Int, detailWidth: Int) {
        layout.text(indentX, route.obtainment.displayMethodName, 0xDDCC99)
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

    private fun routeLines(route: ObtainmentRoute): List<String> = buildList {
        val obtainment = route.obtainment
        add(obtainment.displayDescription)
        obtainment.displayBlock?.let { add(obtainmentUseText(it)) }
        obtainment.displayStructure?.let { add(obtainmentStructureText(it)) }
        obtainment.displayDimension?.let { add(obtainmentDimensionText(it)) }
        addAll(obtainment.displayNotes)
    }
}

object DropPageBuilder {
    private const val DROP_ROW_HEIGHT = 20

    fun build(data: DropRecipeData): PanelLayout = SpawnDisplayHelper.buildDropLayout(data)

    fun measureFixedHeight(): Int {
        val font = Minecraft.getInstance().font
        return 38 + 2 + 1 + 4 + font.lineHeight + PanelLayout.PADDING
    }

    fun measureEntryHeight(drop: DropEntryInfo): Int = DROP_ROW_HEIGHT

    fun buildItemDroppers(data: ItemDroppersRecipeData): SpawnDisplayHelper.ItemDroppersLayoutResult =
        SpawnDisplayHelper.buildItemDroppersLayout(data)
}

object PokemonInfoPageBuilder {

    fun buildOverview(data: PokemonOverviewRecipeData): PanelLayout {
        val projection = data.projection
        val info = projection.info
        val font = Minecraft.getInstance().font
        val padding = PanelLayout.PADDING
        val headerTag = tr("category.cobbledex-rei-emi-jei.overview")
        val displayName = formatSpeciesName(projection.speciesName)
        val dexNumber = info?.nationalDexNumber?.takeIf { it > 0 }?.let { "#$it" }
        val typeText = info?.let { buildTypeText(it.primaryType, it.secondaryType) }
        val headerWidth = PanelLayout.TEXT_START_X + font.width(displayName) + 8 + font.width(headerTag) + padding
        val width = SpawnDisplayHelper.computePanelWidth(headerWidth, 252)
        val layout = PanelLayout(width)
        val right = layout.right
        val indentX = padding + 4
        val rowLabelWidth = 78
        val rowValueX = padding + rowLabelWidth
        val rowValueWidth = right - rowValueX

        layout.textAt(padding + 22, 6, displayName, 0xFFFFFF)
        layout.textRightAt(6, headerTag, 0xDDCC99)
        val typeMaxWidth = right - padding - (dexNumber?.let { font.width(it) + 6 } ?: 0)
        typeText?.let { layout.clippedAt(padding, 22, it, typeMaxWidth, 0xFFDD66) }
        dexNumber?.let { layout.textRightAt(22, it, 0xBBBBBB) }
        layout.fill(padding, 36, right, 37, 0x50FFFFFF)
        layout.skipTo(43)

        info?.description?.takeIf { it.isNotBlank() }?.let { descriptionKey ->
            // info.description stores a translation KEY (e.g.
            // "cobblemon.species.sableye_bloodmoon.desc1"), not resolved
            // text - the separate "Pokemon Description" REI category
            // correctly calls tr() on it (SpawnDisplayHelper.
            // buildPokemonDescriptionLayout), but this inline overview
            // snippet never did, so it rendered the raw key literally
            // whenever a species reached this page before that other
            // category (confirmed via Fai's Mythical Monstrosities' Sableye
            // Bloodmoon, whose Overview page showed
            // "cobblemon.species.sableye_bloodmoon.desc1" as-is).
            val description = tr(descriptionKey)
            if (description != descriptionKey && description.isNotBlank()) {
                val lines = SpawnDisplayHelper.wrapText(font, description, right - indentX).take(3)
                lines.forEach { line ->
                    layout.clipped(indentX, line, right - indentX, 0xCCCCCC)
                    layout.line()
                }
                layout.gap(3)
                layout.separator(0x18FFFFFF)
                layout.gap(4)
            }
        }

        drawOverviewSection(layout, tr("cobbledex-rei-emi-jei.overview.profile"))
        drawOverviewRow(layout, tr("cobbledex-rei-emi-jei.overview.obtainment"), obtainmentSummary(projection), rowValueX, rowValueWidth, rowLabelWidth)
        info?.baseStatTotal?.let { drawOverviewRow(layout, tr("cobbledex-rei-emi-jei.overview.stats"), statSummary(info), rowValueX, rowValueWidth, rowLabelWidth) }
        info?.catchRate?.let { drawOverviewRow(layout, tr("cobbledex-rei-emi-jei.overview.catch"), it.toString(), rowValueX, rowValueWidth, rowLabelWidth) }
        info?.let { pokemonInfo ->
            val abilityText = abilitySummary(pokemonInfo)
            if (abilityText.isNotBlank()) drawOverviewRow(layout, tr("cobbledex-rei-emi-jei.overview.abilities"), abilityText, rowValueX, rowValueWidth, rowLabelWidth)
        }

        layout.gap(3)
        drawOverviewSection(layout, tr("cobbledex-rei-emi-jei.overview.knowledge"))
        drawOverviewRow(layout, tr("cobbledex-rei-emi-jei.overview.moves"), moveSummary(info), rowValueX, rowValueWidth, rowLabelWidth)
        drawOverviewRow(layout, tr("cobbledex-rei-emi-jei.overview.drops"), countText(info?.drops?.size ?: 0, "drop"), rowValueX, rowValueWidth, rowLabelWidth)
        drawOverviewRow(layout, tr("cobbledex-rei-emi-jei.overview.forms"), countText(projection.forms.size, "form"), rowValueX, rowValueWidth, rowLabelWidth)
        projection.riding?.let { riding ->
            drawOverviewRow(layout, tr("cobbledex-rei-emi-jei.overview.riding"), riding.allMountTypes.joinToString(", ") { titleCase(it.lowercase()) }, rowValueX, rowValueWidth, rowLabelWidth)
        }
        if (projection.jobs.isNotEmpty()) {
            drawOverviewRow(layout, tr("cobbledex-rei-emi-jei.overview.jobs"), countText(projection.jobs.size, "job"), rowValueX, rowValueWidth, rowLabelWidth)
        }

        if (info?.isForm == true) {
            layout.gap(3)
            val base = info.baseSpeciesName?.let { formatSpeciesName(it) } ?: "base species"
            val reasons = projection.materialFormDecision?.reasons?.joinToString(", ") ?: "material data"
            layout.wrapped(indentX, tr("cobbledex-rei-emi-jei.overview.form_note", base, reasons), right - indentX, 0xBBBBBB)
        }

        val source = info?.source?.let { sourceLabel(it).ifBlank { titleCase(it) } }
        if (!source.isNullOrBlank()) {
            layout.gap(3)
            layout.textRight(tr("cobbledex-rei-emi-jei.overview.source", source), 0x888888)
            layout.line()
        }
        layout.gap(padding)

        return layout
    }

    fun buildStats(data: StatsRecipeData): PanelLayout =
        SpawnDisplayHelper.buildStatsLayout(data.speciesName, data.baseStats, data.baseStatTotal, data.primaryType, data.secondaryType, data.evYield)

    fun buildPokedex(data: PokedexInfoRecipeData): PanelLayout = SpawnDisplayHelper.buildPokedexInfoLayout(data)
    fun buildDescription(data: PokemonDescriptionRecipeData): PanelLayout = SpawnDisplayHelper.buildPokemonDescriptionLayout(data)
    fun buildTypeChart(data: TypeChartRecipeData): PanelLayout = SpawnDisplayHelper.buildTypeChartLayout(data)
    fun buildForms(data: FormRecipeData): SpawnDisplayHelper.FormLayoutResult = SpawnDisplayHelper.buildFormLayout(data)
    fun buildRiding(data: RidingRecipeData): PanelLayout = SpawnDisplayHelper.buildRidingLayout(data.speciesName, data)

    private fun buildTypeText(primaryType: String, secondaryType: String?): String =
        listOfNotNull(formatTypeName(primaryType), secondaryType?.let { formatTypeName(it) }).joinToString(" / ")

    private fun drawOverviewSection(layout: PanelLayout, title: String) {
        layout.text(PanelLayout.PADDING, title, 0xEEEEEE)
        layout.line()
    }

    private fun drawOverviewRow(
        layout: PanelLayout,
        label: String,
        value: String,
        valueX: Int,
        valueWidth: Int,
        labelWidth: Int,
    ) {
        if (value.isBlank()) return
        val y = layout.y
        layout.clipped(PanelLayout.PADDING + 4, label, labelWidth - 8, 0x999999)
        val lines = layout.wrapped(valueX, value, valueWidth, 0xDDDDDD)
        if (lines == 0) layout.skipTo(y + PanelLayout.LINE_HEIGHT)
    }

    private fun obtainmentSummary(projection: PokemonPageProjection): String {
        val parts = mutableListOf<String>()
        if (projection.sortedSpawns.isNotEmpty()) parts.add(countText(projection.sortedSpawns.size, "spawn"))
        if (projection.specialObtainments.isNotEmpty()) parts.add(countText(projection.specialObtainments.size, "special route"))
        if (projection.fossils.isNotEmpty()) parts.add(countText(projection.fossils.size, "fossil"))
        if (projection.evolutionsTo.isNotEmpty()) parts.add(countText(projection.evolutionsTo.size, "evolution"))
        return parts.ifEmpty { listOf(tr("cobbledex-rei-emi-jei.overview.none")) }.joinToString(" | ")
    }

    private fun statSummary(info: EvolutionDataLoader.SpeciesBasicInfo): String {
        val parts = mutableListOf<String>()
        info.baseStatTotal?.let { parts.add(tr("cobbledex-rei-emi-jei.stats.bst", it)) }
        info.evYield?.takeIf { it.isNotEmpty() }?.let { yields ->
            parts.add(tr("cobbledex-rei-emi-jei.stats.ev_yield", yields.entries.joinToString("/") { "${it.value} ${statLabel(it.key)}" }))
        }
        return parts.joinToString(" | ")
    }

    private fun abilitySummary(info: EvolutionDataLoader.SpeciesBasicInfo): String {
        val abilities = (info.abilities ?: emptyList()).map { formatAbilityName(it) }.toMutableList()
        info.hiddenAbility?.let { abilities.add("${formatAbilityName(it)} ${tr("cobbledex-rei-emi-jei.info.hidden_ability")}") }
        return abilities.joinToString(", ")
    }

    private fun moveSummary(info: EvolutionDataLoader.SpeciesBasicInfo?): String {
        if (info == null) return tr("cobbledex-rei-emi-jei.overview.none")
        val levelCount = info.levelUpMoves?.sumOf { it.moves.size } ?: 0
        val eggCount = info.eggMoves?.size ?: 0
        val tutorCount = info.tutorMoves?.size ?: 0
        val tmCount = info.tmMoves?.size ?: 0
        val parts = mutableListOf<String>()
        if (levelCount > 0) parts.add(countText(levelCount, "level"))
        if (eggCount > 0) parts.add(countText(eggCount, "egg"))
        if (tutorCount > 0) parts.add(countText(tutorCount, "tutor"))
        if (tmCount > 0) parts.add(countText(tmCount, "TM"))
        return parts.ifEmpty { listOf(tr("cobbledex-rei-emi-jei.overview.none")) }.joinToString(" | ")
    }

    private fun countText(count: Int, singular: String): String =
        "$count $singular${if (count == 1) "" else "s"}"

    private fun statLabel(stat: String): String = when (stat.lowercase()) {
        "attack", "atk" -> tr("cobbledex-rei-emi-jei.stat.atk")
        "defence", "defense", "def" -> tr("cobbledex-rei-emi-jei.stat.def")
        "special_attack", "special-attack", "sp_atk", "spa" -> tr("cobbledex-rei-emi-jei.stat.spa")
        "special_defence", "special_defense", "special-defense", "sp_def", "spd" -> tr("cobbledex-rei-emi-jei.stat.spd")
        "speed", "spe" -> tr("cobbledex-rei-emi-jei.stat.spe")
        "hp" -> tr("cobbledex-rei-emi-jei.stat.hp")
        else -> titleCase(stat)
    }
}

object MovePageBuilder {
    fun buildMoves(data: MovesRecipeData): SpawnDisplayHelper.MovesLayoutResult =
        SpawnDisplayHelper.buildMovesLayout(data)
    fun buildMoveLearners(data: MoveLearnersRecipeData): SpawnDisplayHelper.MoveLearnersLayoutResult =
        SpawnDisplayHelper.buildMoveLearnersLayout(data)
}

object MechanicPageBuilder {
    fun buildFossil(data: FossilRecipeData): PanelLayout = SpawnDisplayHelper.buildFossilLayout(data)
    fun buildNature(data: NatureRecipeData): PanelLayout = SpawnDisplayHelper.buildNatureLayout(data)
    fun buildJob(speciesName: String, match: JobMatch): PanelLayout = SpawnDisplayHelper.buildJobLayout(speciesName, match)
}