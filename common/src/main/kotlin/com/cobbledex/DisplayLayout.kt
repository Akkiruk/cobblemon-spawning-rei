package com.cobbledex

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
    @Volatile private var cachedMovesMax: PanelSize? = null
    @Volatile private var cachedFossilMax: PanelSize? = null
    @Volatile private var cachedTypeChartMax: PanelSize? = null
    @Volatile private var cachedNatureMax: PanelSize? = null
    @Volatile private var cachedDataVersion: Long = -1L

    fun invalidateCache() {
        cachedSpawnMax = null
        cachedEvolutionMax = null
        cachedObtainmentMax = null
        cachedDropMax = null
        cachedStatsMax = null
        cachedPokedexInfoMax = null
        cachedMovesMax = null
        cachedFossilMax = null
        cachedTypeChartMax = null
        cachedNatureMax = null
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
        return PanelSize(200, 145).also { cachedStatsMax = it }
    }

    fun getMaxPokedexInfoSize(): PanelSize {
        checkCacheValidity()
        cachedPokedexInfoMax?.let { return it }
        return computeMaxPokedexInfoSize().also { cachedPokedexInfoMax = it }
    }

    fun getMaxMovesSize(): PanelSize {
        checkCacheValidity()
        cachedMovesMax?.let { return it }
        return PanelSize(200, 220).also { cachedMovesMax = it }
    }

    fun getMaxFossilSize(): PanelSize {
        checkCacheValidity()
        cachedFossilMax?.let { return it }
        return computeMaxFossilSize().also { cachedFossilMax = it }
    }

    fun getMaxTypeChartSize(): PanelSize {
        checkCacheValidity()
        cachedTypeChartMax?.let { return it }
        return computeMaxTypeChartSize().also { cachedTypeChartMax = it }
    }

    fun getMaxNatureSize(): PanelSize {
        checkCacheValidity()
        cachedNatureMax?.let { return it }
        return PanelSize(200, 290).also { cachedNatureMax = it }
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
            cachedMovesMax = null
            cachedFossilMax = null
            cachedTypeChartMax = null
            cachedNatureMax = null
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
                val layout = SpawnDisplayHelper.buildSpawnLayout(species, entry.spawn, entry.formVariants, entry.bucketIndex, entry.bucketTotal)
                if (layout.width > maxW) maxW = layout.width
                if (layout.height > maxH) maxH = layout.height
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
            val layout = SpawnDisplayHelper.buildEvolutionLayout(evo, idx, total)
            if (layout.width > maxW) maxW = layout.width
            if (layout.height > maxH) maxH = layout.height
        }
        return PanelSize(maxW, maxH)
    }

    private fun computeMaxObtainmentSize(): PanelSize {
        if (!SpawnDataIndex.hasData()) return PanelSize(180, 150)
        var maxW = MIN_WIDTH
        var maxH = 80
        for ((species, obtainments) in SpawnDataIndex.obtainmentBySpecies) {
            for ((i, info) in obtainments.withIndex()) {
                val layout = SpawnDisplayHelper.buildObtainmentLayout(species, info, i + 1, obtainments.size)
                if (layout.width > maxW) maxW = layout.width
                if (layout.height > maxH) maxH = layout.height
            }
        }
        return PanelSize(maxW, maxH)
    }

    // --- Drop size computation ---

    private fun computeMaxDropSize(): PanelSize {
        if (!SpawnDataIndex.hasData()) return PanelSize(180, 120)
        var maxW = MIN_WIDTH
        var maxH = 80
        for ((species, info) in SpawnDataIndex.speciesInfo) {
            val drops = info.drops ?: continue
            if (drops.isEmpty()) continue
            val layout = SpawnDisplayHelper.buildDropLayout(species, drops)
            if (layout.width > maxW) maxW = layout.width
            if (layout.height > maxH) maxH = layout.height
        }
        return PanelSize(maxW, maxH)
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

    private fun computeMaxFossilSize(): PanelSize {
        if (SpawnDataIndex.fossilsBySpecies.isEmpty()) return PanelSize(200, 100)
        var maxH = 80
        for ((_, combos) in SpawnDataIndex.fossilsBySpecies) {
            for (combo in combos) {
                // header (22) + sep (1) + label (13) + items + optional tags
                var y = 22 + 1 + LINE_HEIGHT + 2
                y += combo.fossilItems.size * ITEM_ROW_HEIGHT
                if (combo.extraTags != null) {
                    y += 4 + 1 + 4
                    y += combo.extraTags.split(" ").size * LINE_HEIGHT
                }
                y += PADDING
                if (y > maxH) maxH = y
            }
        }
        return PanelSize(200, maxH.coerceAtMost(250))
    }

    private fun computeMaxTypeChartSize(): PanelSize {
        if (!SpawnDataIndex.hasData()) return PanelSize(200, 200)
        var maxH = 80
        for ((_, info) in SpawnDataIndex.speciesInfo) {
            val matchups = TypeChart.getMatchups(info.primaryType, info.secondaryType)
            var h = 40
            if (matchups.weaknesses.isNotEmpty())
                h += LINE_HEIGHT + matchups.weaknesses.size * LINE_HEIGHT + SECTION_GAP
            if (matchups.resistances.isNotEmpty())
                h += LINE_HEIGHT + matchups.resistances.size * LINE_HEIGHT + SECTION_GAP
            if (matchups.immunities.isNotEmpty())
                h += LINE_HEIGHT + matchups.immunities.size * LINE_HEIGHT
            h += PADDING
            if (h > maxH) maxH = h
        }
        return PanelSize(200, maxH)
    }
}
