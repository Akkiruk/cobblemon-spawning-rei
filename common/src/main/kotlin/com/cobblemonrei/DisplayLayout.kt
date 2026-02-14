package com.cobblemonrei

import com.cobblemonrei.config.CobblemonSpawningConfig
import net.minecraft.client.Minecraft

object DisplayLayout {

    const val PADDING = 6
    const val LINE_HEIGHT = 11
    const val SECTION_GAP = 3
    const val ICON_SIZE = 20
    const val ICON_GAP = 2
    const val TEXT_START_X = PADDING + ICON_SIZE + ICON_GAP
    const val INDENT_X = PADDING + 6
    const val SLOT_SIZE = 18
    const val ITEM_ROW_HEIGHT = 20
    const val MIN_WIDTH = 150
    const val MAX_WIDTH = 300

    data class PanelSize(val width: Int, val height: Int)

    @Volatile private var cachedSpawnMax: PanelSize? = null
    @Volatile private var cachedEvolutionMax: PanelSize? = null
    @Volatile private var cachedObtainmentMax: PanelSize? = null
    @Volatile private var cachedDropMax: PanelSize? = null
    @Volatile private var cachedStatsMax: PanelSize? = null
    @Volatile private var cachedPokedexInfoMax: PanelSize? = null
    @Volatile private var cachedDataVersion: Long = -1L

    fun invalidateCache() {
        cachedSpawnMax = null
        cachedEvolutionMax = null
        cachedObtainmentMax = null
        cachedDropMax = null
        cachedStatsMax = null
        cachedPokedexInfoMax = null
        cachedDataVersion = -1L
    }

    fun getMaxSpawnSize(): PanelSize {
        checkCacheValidity()
        cachedSpawnMax?.let { return it }
        return computeMaxSpawnSize().also { cachedSpawnMax = it }
    }

    fun getMaxEvolutionSize(): PanelSize {
        checkCacheValidity()
        cachedEvolutionMax?.let { return it }
        return computeMaxEvolutionSize().also { cachedEvolutionMax = it }
    }

    fun getMaxObtainmentSize(): PanelSize {
        checkCacheValidity()
        cachedObtainmentMax?.let { return it }
        return computeMaxObtainmentSize().also { cachedObtainmentMax = it }
    }

    fun getMaxDropSize(): PanelSize {
        checkCacheValidity()
        cachedDropMax?.let { return it }
        return computeMaxDropSize().also { cachedDropMax = it }
    }

    fun getMaxStatsSize(): PanelSize {
        checkCacheValidity()
        cachedStatsMax?.let { return it }
        return PanelSize(200, 130).also { cachedStatsMax = it }
    }

    fun getMaxPokedexInfoSize(): PanelSize {
        checkCacheValidity()
        cachedPokedexInfoMax?.let { return it }
        return computeMaxPokedexInfoSize().also { cachedPokedexInfoMax = it }
    }

    private fun checkCacheValidity() {
        val ver = SpawnDataIndex.dataVersion
        if (ver != cachedDataVersion) {
            cachedSpawnMax = null
            cachedEvolutionMax = null
            cachedObtainmentMax = null
            cachedDropMax = null
            cachedStatsMax = null
            cachedPokedexInfoMax = null
            cachedDataVersion = ver
        }
    }

    private fun computeMaxSpawnSize(): PanelSize {
        if (!SpawnDataIndex.hasData()) return PanelSize(180, 200)
        var maxW = MIN_WIDTH
        var maxH = 80
        for ((species, spawns) in SpawnDataIndex.spawnsBySpecies) {
            val sorted = SpawnDisplayHelper.buildSortedSpawns(spawns)
            for (entry in sorted) {
                val size = measureSpawnPanel(species, entry.spawn, entry.formVariants, entry.bucketIndex, entry.bucketTotal)
                if (size.width > maxW) maxW = size.width
                if (size.height > maxH) maxH = size.height
            }
        }
        return PanelSize(maxW, maxH)
    }

    private fun computeMaxEvolutionSize(): PanelSize {
        if (!SpawnDataIndex.hasData()) return PanelSize(180, 120)
        val branches = SpawnDisplayHelper.deduplicateEvolutions(SpawnDataIndex.evolutionsBySpecies)
        var maxW = MIN_WIDTH
        var maxH = 70
        for ((evo, idx, total) in branches) {
            val size = measureEvolutionPanel(evo, idx, total)
            if (size.width > maxW) maxW = size.width
            if (size.height > maxH) maxH = size.height
        }
        return PanelSize(maxW, maxH)
    }

    private fun computeMaxObtainmentSize(): PanelSize {
        if (!SpawnDataIndex.hasData()) return PanelSize(180, 150)
        var maxW = MIN_WIDTH
        var maxH = 80
        for ((species, obtainments) in SpawnDataIndex.obtainmentBySpecies) {
            for ((i, info) in obtainments.withIndex()) {
                val size = measureObtainmentPanel(species, info, i + 1, obtainments.size)
                if (size.width > maxW) maxW = size.width
                if (size.height > maxH) maxH = size.height
            }
        }
        return PanelSize(maxW, maxH)
    }

    // --- Spawn panel measurement ---

    fun measureSpawnPanel(
        speciesName: String,
        spawn: SpawnInfo,
        mergedFormVariants: List<String>,
        bucketIndex: Int,
        bucketTotal: Int
    ): PanelSize {
        val font = Minecraft.getInstance().font
        val showWeights = CobblemonSpawningConfig.get().showSpawnWeights && spawn.weight > 0f

        val nameWidth = TEXT_START_X + font.width(formatSpeciesName(speciesName)) + PADDING
        val lvText = levelText(spawn.levelRange)
        val bucketText = SpawnDisplayHelper.bucketLabel(spawn.bucket)
        val lvBucketWidth = PADDING + font.width(lvText) + 6 + font.width(bucketText) + PADDING

        val ctxParts = SpawnDisplayHelper.buildContextParts(spawn, mergedFormVariants)
        val ctxText = ctxParts.joinToString(" \u00B7 ")
        val wtText = if (showWeights) weightText(spawn.weight) else ""
        val ctxRowWidth = if (ctxText.isNotEmpty() || wtText.isNotEmpty()) {
            PADDING + 4 + font.width(ctxText) + (if (wtText.isNotEmpty()) 6 + font.width(wtText) else 0) + PADDING
        } else 0

        val footerText = "${SpawnDisplayHelper.bucketLabel(spawn.bucket)} $bucketIndex/$bucketTotal"
        val footerWidth = PADDING + font.width(footerText) + PADDING

        val width = maxOf(nameWidth, lvBucketWidth, ctxRowWidth, footerWidth, MIN_WIDTH).coerceAtMost(MAX_WIDTH)

        val right = width - PADDING
        val indentWidth = right - INDENT_X
        var y = 42

        // Context/weight row
        if (showWeights && ctxText.isNotEmpty()) {
            val ctxMax = right - font.width(wtText) - (PADDING + 4) - 6
            y += SpawnDisplayHelper.wrapText(font, ctxText, ctxMax).size * LINE_HEIGHT
        } else if (ctxText.isNotEmpty()) {
            y += SpawnDisplayHelper.wrapText(font, ctxText, right - PADDING - 4).size * LINE_HEIGHT
        } else {
            y += LINE_HEIGHT
        }
        y += 4

        // Biomes
        val biomeNames = spawn.biomes.map { formatBiomeName(it) }
        if (biomeNames.isNotEmpty()) {
            y += LINE_HEIGHT
            y += SpawnDisplayHelper.wrapToWidth(font, biomeNames.joinToString(", "), indentWidth).size * LINE_HEIGHT
            y += SECTION_GAP
        }

        // Conditions
        val conditions = SpawnDisplayHelper.buildConditions(spawn)
        if (conditions.isNotEmpty()) {
            y += LINE_HEIGHT
            for (cond in conditions) {
                y += SpawnDisplayHelper.wrapText(font, cond, indentWidth).size * LINE_HEIGHT
            }
            y += SECTION_GAP
        }

        // Specials
        val specials = SpawnDisplayHelper.buildSpecials(spawn)
        if (specials.isNotEmpty()) {
            y += LINE_HEIGHT
            for (s in specials) {
                y += SpawnDisplayHelper.wrapText(font, s, indentWidth).size * LINE_HEIGHT
            }
            y += SECTION_GAP
        }

        // Exclusions
        val anti = spawn.anticondition
        if (anti != null && !anti.isEmpty) {
            val exLines = SpawnDisplayHelper.buildExclusionLines(anti)
            if (exLines.isNotEmpty()) {
                y += LINE_HEIGHT
                for (line in exLines) {
                    y += SpawnDisplayHelper.wrapText(font, line, indentWidth).size * LINE_HEIGHT
                }
                y += SECTION_GAP
            }
        }

        // Weight modifiers
        if (CobblemonSpawningConfig.get().showSpawnWeights && spawn.weightMultipliers.isNotEmpty()) {
            y += LINE_HEIGHT
            for (wm in spawn.weightMultipliers) {
                val arrow = if (wm.multiplier > 1f) "\u25B2" else if (wm.multiplier < 1f) "\u25BC" else "\u25CF"
                val wmText = "$arrow ${SpawnDisplayHelper.formatWeight(wm.multiplier)}x ${wm.conditionSummary}"
                y += SpawnDisplayHelper.wrapText(font, wmText, indentWidth).size * LINE_HEIGHT
            }
        }

        // Footer
        y += 1 + 1 + 4 + font.lineHeight + PADDING

        return PanelSize(width, y)
    }

    // --- Evolution panel measurement ---

    fun measureEvolutionPanel(
        evolution: EvolutionInfo,
        branchIndex: Int,
        branchTotal: Int
    ): PanelSize {
        val font = Minecraft.getInstance().font
        val fromW = font.width(evolution.displayFromName)
        val toW = font.width(evolution.displayToName)

        // Width must fit both names + arrow + slots + margins without overlap
        val neededForNames = maxOf(fromW, toW) * 2 + font.width("\u2192") + 8 + 40 + SLOT_SIZE + PADDING * 2

        val items = evolution.itemRequirements
        var maxItemLabelWidth = 0
        for (item in items) {
            val stack = SpawnDisplayHelper.resolveItemStack(item.itemId)
            val name = if (!stack.isEmpty) stack.hoverName.string else titleCase(item.itemId.substringAfter(":"))
            maxItemLabelWidth = maxOf(maxItemLabelWidth, 30 + font.width("${item.label} $name") + PADDING)
        }

        val hasItems = items.isNotEmpty()
        val reqText = if (hasItems) evolution.textOnlyRequirements else evolution.displayRequirements
        val reqWidth = if (reqText.isNotBlank()) PADDING + font.width(reqText) + PADDING else 0

        val width = maxOf(neededForNames, maxItemLabelWidth, reqWidth, MIN_WIDTH).coerceAtMost(MAX_WIDTH)

        val right = width - PADDING
        var contentY = 48

        contentY += items.size * ITEM_ROW_HEIGHT

        if (reqText.isNotBlank()) {
            val lines = SpawnDisplayHelper.wrapToWidth(font, reqText, right - PADDING)
            contentY += lines.size * 12
        }

        if (branchTotal > 1) {
            contentY += font.lineHeight + 2
        }

        contentY += PADDING

        return PanelSize(width, contentY)
    }

    // --- Obtainment panel measurement ---

    fun measureObtainmentPanel(
        speciesName: String,
        obtainment: ObtainmentInfo,
        entryIndex: Int,
        entryTotal: Int
    ): PanelSize {
        val font = Minecraft.getInstance().font
        val methodText = obtainment.displayMethodName
        val headerWidth = TEXT_START_X + font.width(formatSpeciesName(speciesName)) + 6 + font.width(methodText) + PADDING

        val width = maxOf(headerWidth, MIN_WIDTH).coerceAtMost(MAX_WIDTH)

        val right = width - PADDING
        val indentX = PADDING + 4
        val indentWidth = right - indentX
        var y = 26

        y += SpawnDisplayHelper.wrapText(font, obtainment.displayDescription, indentWidth).size * LINE_HEIGHT
        y += 4

        if (obtainment.items.isNotEmpty()) {
            y += LINE_HEIGHT
            for (item in obtainment.displayItems) {
                y += SpawnDisplayHelper.wrapText(font, "\u2022 $item", indentWidth).size * LINE_HEIGHT
            }
            y += 4
        }

        if (obtainment.displayBlock != null || obtainment.displayStructure != null || obtainment.displayDimension != null) {
            y += LINE_HEIGHT
            obtainment.displayBlock?.let { y += SpawnDisplayHelper.wrapText(font, obtainmentUseText(it), indentWidth).size * LINE_HEIGHT }
            obtainment.displayStructure?.let { y += SpawnDisplayHelper.wrapText(font, obtainmentStructureText(it), indentWidth).size * LINE_HEIGHT }
            obtainment.displayDimension?.let { y += SpawnDisplayHelper.wrapText(font, obtainmentDimensionText(it), indentWidth).size * LINE_HEIGHT }
            y += 4
        }

        for (note in obtainment.notes) {
            y += SpawnDisplayHelper.wrapText(font, "\u2139 $note", indentWidth).size * LINE_HEIGHT
        }

        y += 1 + 1 + 4 + font.lineHeight + PADDING

        return PanelSize(width, y)
    }

    // --- Drop panel measurement ---

    private fun computeMaxDropSize(): PanelSize {
        if (!SpawnDataIndex.hasData()) return PanelSize(180, 120)
        var maxW = MIN_WIDTH
        var maxH = 80
        for ((species, info) in SpawnDataIndex.speciesInfo) {
            val drops = info.drops ?: continue
            if (drops.isEmpty()) continue
            val size = measureDropPanel(species, drops)
            if (size.width > maxW) maxW = size.width
            if (size.height > maxH) maxH = size.height
        }
        return PanelSize(maxW, maxH)
    }

    fun measureDropPanel(speciesName: String, drops: List<DropEntryInfo>): PanelSize {
        val font = Minecraft.getInstance().font

        val nameWidth = TEXT_START_X + font.width(formatSpeciesName(speciesName)) + PADDING
        val headerTag = tr("category.cobblemon-spawning-rei.drops")
        val headerWidth = nameWidth + 6 + font.width(headerTag) + PADDING

        var maxItemRowWidth = 0
        for (drop in drops) {
            val itemName = SpawnDisplayHelper.resolveItemName(drop.itemId)
            val rightText = "${drop.displayPercentage} \u00D7${drop.displayQuantity}"
            val rowWidth = PADDING + 22 + font.width(itemName) + 8 + font.width(rightText) + PADDING
            if (rowWidth > maxItemRowWidth) maxItemRowWidth = rowWidth
        }

        val width = maxOf(headerWidth, maxItemRowWidth, MIN_WIDTH).coerceAtMost(MAX_WIDTH)

        // header (22px) + separator (1px) + gap (4px) + label header (12px) + drops + footer
        var y = 22 + 1 + 4 + LINE_HEIGHT + 1
        y += drops.size * ITEM_ROW_HEIGHT
        y += 2 + 1 + 4 + font.lineHeight + PADDING

        return PanelSize(width, y)
    }

    private fun computeMaxPokedexInfoSize(): PanelSize {
        if (!SpawnDataIndex.hasData()) return PanelSize(200, 200)
        val font = Minecraft.getInstance().font
        var maxH = 120
        for ((_, info) in SpawnDataIndex.speciesInfo) {
            var y = 26
            val lh = LINE_HEIGHT
            if ((info.abilities != null && info.abilities.isNotEmpty()) || info.hiddenAbility != null) {
                y += lh + (info.abilities?.size ?: 0) * lh
                if (info.hiddenAbility != null) y += lh
                y += 3
            }
            if (info.eggGroups != null && info.eggGroups.isNotEmpty()) {
                y += lh + lh + 3
            }
            y += lh + lh + lh // physical, catch rate, height/weight
            if (info.maleRatio != null) y += lh
            y += 3
            if (info.eggCycles != null) y += lh + lh
            val hasTraining = info.experienceGroup != null || info.baseExperienceYield != null || info.baseFriendship != null
            if (hasTraining) {
                y += 1 + lh
                if (info.experienceGroup != null) y += lh
                if (info.baseExperienceYield != null) y += lh
                if (info.baseFriendship != null) y += lh
            }
            info.description?.let { desc ->
                y += 3 + 1 + 4
                y += SpawnDisplayHelper.wrapText(font, desc, 200 - PADDING * 2 - 4).size * lh
            }
            y += PADDING
            if (y > maxH) maxH = y
        }
        return PanelSize(200, maxH.coerceAtMost(350))
    }
}
