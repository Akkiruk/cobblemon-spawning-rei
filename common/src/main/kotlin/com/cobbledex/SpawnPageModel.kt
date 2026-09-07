package com.cobbledex

/**
 * Pure classification / phrasing helpers for the spawn page. No rendering, no Minecraft client - all
 * unit-testable. [SpawnDisplayHelper] turns the output of these into panel layout.
 */
object SpawnPageModel {

    // ---------------------------------------------------------------- biomes

    enum class BiomeClass { DIMENSION, CLIMATE, CONCRETE }

    private val DIMENSION_TAG_HINTS = listOf(
        "is_overworld", "is_nether", "is_end", "is_aether", "is_the_aether",
    )
    private val DIMENSION_NAMESPACES = setOf("the_bumblezone")

    /** A `#namespace:path` tag or a concrete `namespace:path` biome id. */
    fun classifyBiome(id: String): BiomeClass {
        val clean = id.removePrefix("#")
        val ns = clean.substringBefore(':', "")
        val path = clean.substringAfter(':')
        if (!id.startsWith("#")) {
            // A concrete biome from a whole-dimension mod still reads as "the <dimension>".
            return if (ns in DIMENSION_NAMESPACES) BiomeClass.DIMENSION else BiomeClass.CONCRETE
        }
        if (ns in DIMENSION_NAMESPACES) return BiomeClass.DIMENSION
        if (DIMENSION_TAG_HINTS.any { path == it }) return BiomeClass.DIMENSION
        return BiomeClass.CLIMATE
    }

    /** Dimension label for a DIMENSION-class biome id / a real dimension id, or null for the overworld. */
    fun dimensionLabelFor(id: String): String? {
        val clean = id.removePrefix("#")
        val ns = clean.substringBefore(':', "minecraft")
        val path = clean.substringAfter(':')
        return when {
            ns == "the_bumblezone" -> tr("cobbledex-rei-emi-jei.spawn.dim.bumblezone")
            ns == "aether" || path.contains("aether") -> tr("cobbledex-rei-emi-jei.spawn.dim.aether")
            path.contains("nether") -> tr("cobbledex-rei-emi-jei.spawn.dim.nether")
            path.contains("end") -> tr("cobbledex-rei-emi-jei.spawn.dim.end")
            else -> null // overworld - no line
        }
    }

    data class BiomeSplit(
        val climateOrConcrete: List<String>, // raw ids, for friendly-naming + per-name hover
        val dimensions: List<String>,        // resolved non-overworld dimension labels, deduped
        val hasOverworld: Boolean,           // an `is_overworld`-style tag was present
    )

    fun splitBiomes(rawBiomeIds: List<String>): BiomeSplit {
        val keep = mutableListOf<String>()
        val dims = linkedSetOf<String>()
        var overworld = false
        for (id in rawBiomeIds) {
            when (classifyBiome(id)) {
                BiomeClass.DIMENSION -> {
                    val label = dimensionLabelFor(id)
                    if (label == null) overworld = true else dims.add(label)
                }
                else -> keep.add(id)
            }
        }
        return BiomeSplit(keep, dims.toList(), overworld)
    }

    // ---------------------------------------------------------------- light

    /**
     * One phrase from every light bound at once. `null` when nothing is constrained.
     * skyLight = exposure to open sky (day/roof); blockLight = torches etc.
     */
    fun lightPhrase(
        minSky: Int?, maxSky: Int?, minLight: Int?, maxLight: Int?, canSeeSky: Boolean?,
    ): String? {
        val darkSky = maxSky != null && maxSky <= 0
        val openSky = minSky != null && minSky >= 8
        val darkBlock = maxLight != null && maxLight <= 0
        val dimBlock = maxLight != null && maxLight in 1..7
        val brightBlock = minLight != null && minLight >= 8

        return when {
            darkBlock && (darkSky || canSeeSky == false) -> tr("cobbledex-rei-emi-jei.spawn.light.total_dark")
            darkBlock && openSky -> tr("cobbledex-rei-emi-jei.spawn.light.open_unlit")
            darkBlock -> tr("cobbledex-rei-emi-jei.spawn.light.dark")
            dimBlock -> tr("cobbledex-rei-emi-jei.spawn.light.dim", maxLight!!)
            darkSky || canSeeSky == false -> tr("cobbledex-rei-emi-jei.spawn.light.under_cover")
            openSky -> tr("cobbledex-rei-emi-jei.spawn.light.open_sky")
            brightBlock -> tr("cobbledex-rei-emi-jei.spawn.light.lit")
            canSeeSky == true -> tr("cobbledex-rei-emi-jei.spawn.light.open_sky")
            else -> null
        }
    }

    // ---------------------------------------------------------------- time

    enum class TimeLabel { DAY, NIGHT, DAWN, DUSK, ANY, ODD }

    /** Named ranges map directly; tick ranges snap to the nearest window or fall to ODD. */
    fun normalizeTime(raw: String?): TimeLabel {
        if (raw.isNullOrBlank()) return TimeLabel.ANY
        val lower = raw.lowercase().trim()
        when {
            lower == "any" || lower == "all" -> return TimeLabel.ANY
            lower.contains("dawn") -> return TimeLabel.DAWN
            lower.contains("dusk") || lower.contains("twilight") -> return TimeLabel.DUSK
            lower.contains("night") -> return TimeLabel.NIGHT
            lower.contains("day") -> return TimeLabel.DAY
        }
        // numeric tick range(s): take the first segment, snap to a window
        val seg = lower.split(",").firstOrNull()?.trim() ?: return TimeLabel.ODD
        val parts = seg.split("-").mapNotNull { it.trim().toIntOrNull() }
        if (parts.size != 2) return TimeLabel.ODD
        val (start, end) = parts
        val span = (end - start + 24000) % 24000
        // Full day/night are ~12000-tick windows; anything much shorter is "odd".
        if (span < 8000 || span > 16000) return TimeLabel.ODD
        return when {
            start in 0..2000 || start in 22000..24000 -> TimeLabel.DAY
            start in 11000..14000 -> TimeLabel.NIGHT
            else -> TimeLabel.ODD
        }
    }

    // ---------------------------------------------------------------- weight multipliers

    data class MultPhrase(val text: String, val spatial: Boolean)

    /** A plain-language phrase for a weight multiplier, or null to fall back to the raw form. */
    fun phraseMultiplier(wm: WeightMultiplier): MultPhrase? {
        val m = wm.multiplier
        if (m == 1f) return null
        val dir = if (m > 1f) tr("cobbledex-rei-emi-jei.spawn.mult.more") else tr("cobbledex-rei-emi-jei.spawn.mult.less")
        val much = if (m >= 3f || (m > 0f && m <= 0.34f)) tr("cobbledex-rei-emi-jei.spawn.mult.much") + " " else ""
        val part = wm.conditionParts.firstOrNull { it.type !in setOf("always", "conditional") } ?: return null

        val (cond, spatial) = when (part.type) {
            "rain" -> tr("cobbledex-rei-emi-jei.spawn.mult.in_rain") to false
            "thunderstorm" -> tr("cobbledex-rei-emi-jei.spawn.mult.in_storm") to false
            "time_range" -> when (normalizeTime(part.text)) {
                TimeLabel.NIGHT -> tr("cobbledex-rei-emi-jei.spawn.mult.at_night") to false
                TimeLabel.DAY -> tr("cobbledex-rei-emi-jei.spawn.mult.by_day") to false
                TimeLabel.DAWN, TimeLabel.DUSK -> tr("cobbledex-rei-emi-jei.spawn.mult.at_twilight") to false
                else -> return null
            }
            "biomes" -> {
                val names = part.ids.map { formatBiomeName(it) }
                tr("cobbledex-rei-emi-jei.spawn.mult.in_biomes", names.take(2).joinToString(", ")) to true
            }
            "moon" -> tr("cobbledex-rei-emi-jei.spawn.mult.by_moon") to false
            "lure" -> tr("cobbledex-rei-emi-jei.spawn.mult.with_lure", part.number ?: 1) to false
            else -> return null
        }
        return MultPhrase("$much$dir $cond", spatial)
    }

    // ---------------------------------------------------------------- anticondition interpretation

    private const val NATURAL_GROUND_TAG = "#cobblemon:natural"

    data class AntiSplit(val positiveWhere: List<String>, val remaining: SpawnAntiCondition)

    /**
     * `not on natural ground` (from the `derelict`/`urban` presets) is really "on built/structure
     * blocks" - a positive locator, not an exclusion. Everything else stays an exclusion.
     */
    fun interpretAnti(anti: SpawnAntiCondition?): AntiSplit {
        if (anti == null) return AntiSplit(emptyList(), SpawnAntiCondition())
        val where = mutableListOf<String>()
        val stillExcludedBlocks = anti.neededBaseBlocks.filterNot { it == NATURAL_GROUND_TAG }
        if (NATURAL_GROUND_TAG in anti.neededBaseBlocks) {
            where.add(tr("cobbledex-rei-emi-jei.spawn.on_built"))
        }
        return AntiSplit(where, anti.copy(neededBaseBlocks = stillExcludedBlocks))
    }

    // ---------------------------------------------------------------- block chips

    /** [itemId] renders as a sprite when it resolves to a real item; [label] is the short caption. */
    data class BlockChip(val itemId: String?, val label: String)

    private val BLOCK_TRANSLATIONS: Map<String, Pair<String, String>> = mapOf(
        // id/tag -> (sprite item id, i18n key for the whole label)
        "#cobblemon:trees" to ("minecraft:oak_leaves" to "cobbledex-rei-emi-jei.spawn.needs.trees"),
    )

    /**
     * After preset resolution, base blocks under a `structures:` constraint are just the structure's
     * building materials - dropped. Nearby blocks and un-structured base blocks are kept.
     */
    fun blockChips(
        baseBlocks: List<String>, nearbyBlocks: List<String>, hasStructure: Boolean,
    ): List<BlockChip> {
        val chips = mutableListOf<BlockChip>()
        if (!hasStructure) for (id in baseBlocks) chips.add(chipFor(id, onGround = true))
        for (id in nearbyBlocks) chips.add(chipFor(id, onGround = false))
        return chips.distinctBy { it.label }
    }

    private fun chipFor(id: String, onGround: Boolean): BlockChip {
        BLOCK_TRANSLATIONS[id]?.let { (sprite, key) -> return BlockChip(sprite, tr(key)) }
        val lower = id.lowercase()
        val prefix = if (onGround) tr("cobbledex-rei-emi-jei.spawn.needs.on") else tr("cobbledex-rei-emi-jei.spawn.needs.near")
        return when {
            lower.contains("water") -> BlockChip("minecraft:water_bucket", "$prefix ${tr("cobbledex-rei-emi-jei.fluid.water")}")
            lower.contains("lava") -> BlockChip("minecraft:lava_bucket", "$prefix ${tr("cobbledex-rei-emi-jei.fluid.lava")}")
            else -> BlockChip(id.removePrefix("#"), "$prefix ${formatBlockName(id)}")
        }
    }

    // ---------------------------------------------------------------- display grouping

    /** Coarse light category, for deciding whether two spawns are "the same place". */
    fun lightBucket(minSky: Int?, maxSky: Int?, minLight: Int?, maxLight: Int?, canSeeSky: Boolean?): String = when {
        maxLight != null && maxLight <= 0 -> "dark"
        (maxSky != null && maxSky <= 0) || canSeeSky == false -> "covered"
        (minSky != null && minSky >= 8) || canSeeSky == true -> "open"
        maxLight != null && maxLight <= 7 -> "dim"
        else -> "any"
    }

    /** Stable "which place" key (no `+N` count), for merging near-identical spawns into one page. */
    fun locatorKeyFor(s: SpawnInfo): String {
        if (s.structures.isNotEmpty()) return "s:" + s.structures.sorted().joinToString(",")
        val split = splitBiomes(s.biomes)
        val cc = split.climateOrConcrete
        if (cc.isNotEmpty()) {
            val climate = cc.filter { classifyBiome(it) == BiomeClass.CLIMATE }.sorted().firstOrNull()
            return "b:" + (climate ?: cc.sorted().first())
        }
        if (split.dimensions.isNotEmpty()) return "d:" + split.dimensions.sorted().first()
        if (split.hasOverworld) return "d:overworld"
        if (s.habitat != null) return "h"
        return "any"
    }

    /** Full grouping key: same key -> one merged spawn page + one index row. */
    fun displayGroupKey(s: SpawnInfo): String = listOf(
        s.bucket.lowercase(),
        locatorKeyFor(s),
        normalizeTime(s.timeRange).name,
        "${s.weather.isRaining}/${s.weather.isThundering}",
        lightBucket(s.minSkyLight, s.maxSkyLight, s.minLight, s.maxLight, s.canSeeSky),
        s.moonPhase ?: "-",
        "${s.minY}/${s.maxY}",
        (s.isPokeSnack == true).toString(),
        (s.herd != null).toString(),
        s.isFishing.toString(),
        (s.context.takeIf { it != "grounded" && it != "fishing" } ?: "-"),
    ).joinToString("|")

    // ---------------------------------------------------------------- index locator

    /** The most specific thing that says "where" - for one terse index row. */
    fun locatorFor(spawn: SpawnInfo): String {
        spawn.structures.firstOrNull()?.let { s ->
            val extra = if (spawn.structures.size > 1) " +${spawn.structures.size - 1}" else ""
            return formatStructureName(s) + extra
        }
        val split = splitBiomes(spawn.biomes)
        val concrete = split.climateOrConcrete.filter { classifyBiome(it) == BiomeClass.CONCRETE }
        val climate = split.climateOrConcrete.filter { classifyBiome(it) == BiomeClass.CLIMATE }
        (concrete + climate).firstOrNull()?.let { b ->
            val total = split.climateOrConcrete.size
            val extra = if (total > 1) " +${total - 1}" else ""
            return formatBiomeName(b) + extra
        }
        split.dimensions.firstOrNull()?.let { return it }
        return tr("cobbledex-rei-emi-jei.spawn.anywhere")
    }
}
