package com.cobbledex

/**
 * Turns the conditions on a spawn's weight multiplier into [WeightConditionPart]s. Multipliers reach
 * CobbleDex two ways, typed Cobblemon objects in a local world and raw JSON on a server, and each
 * reader used to keep its own short list of conditions it understood, so most bonuses read just
 * "conditional". Both readers now describe what they found as [Facts] and meet here.
 */
object WeightConditionParts {

    data class Facts(
        val isThundering: Boolean? = null,
        val isRaining: Boolean? = null,
        val timeRange: String? = null,
        val moonPhase: String? = null,
        val biomes: List<String> = emptyList(),
        val structures: List<String> = emptyList(),
        val nearbyBlocks: List<String> = emptyList(),
        val minLureLevel: Int? = null,
        val maxLureLevel: Int? = null,
        val minLight: Int? = null,
        val maxLight: Int? = null,
        val minY: Int? = null,
        val maxY: Int? = null,
        val isPokeSnack: Boolean? = null,
        val isSlimeChunk: Boolean? = null,
        val canSeeSky: Boolean? = null,
        val minSkyLight: Int? = null,
        val maxSkyLight: Int? = null,
        val dimensions: List<String> = emptyList(),
    )

    val ALWAYS = listOf(WeightConditionPart(type = "always"))
    val CONDITIONAL = listOf(WeightConditionPart(type = "conditional"))

    fun of(f: Facts): List<WeightConditionPart> = buildList {
        if (f.isPokeSnack == true) add(WeightConditionPart(type = "poke_snack"))
        if (f.isSlimeChunk == true) add(WeightConditionPart(type = "slime_chunk"))
        if (f.isThundering == true) add(WeightConditionPart(type = "thunderstorm"))
        if (f.isRaining == true) add(WeightConditionPart(type = "rain"))
        if (f.isRaining == false) add(WeightConditionPart(type = "clear"))
        if (f.canSeeSky == true) add(WeightConditionPart(type = "open_sky"))
        if (f.canSeeSky == false) add(WeightConditionPart(type = "covered"))
        rangeText(f.minSkyLight, f.maxSkyLight)?.let { add(WeightConditionPart(type = "sky_light", text = it)) }
        if (f.dimensions.isNotEmpty()) add(WeightConditionPart(type = "dimensions", ids = f.dimensions))
        f.timeRange?.takeIf { it.isNotBlank() }?.let { add(WeightConditionPart(type = "time_range", text = it)) }
        f.moonPhase?.takeIf { it.isNotBlank() }?.let { add(WeightConditionPart(type = "moon", text = it)) }
        if (f.biomes.isNotEmpty()) add(WeightConditionPart(type = "biomes", ids = f.biomes))
        if (f.structures.isNotEmpty()) add(WeightConditionPart(type = "structures", ids = f.structures))
        if (f.nearbyBlocks.isNotEmpty()) add(WeightConditionPart(type = "nearby_blocks", ids = f.nearbyBlocks))
        when {
            f.minLureLevel != null && f.maxLureLevel == null -> add(WeightConditionPart(type = "lure", number = f.minLureLevel))
            f.maxLureLevel != null -> add(WeightConditionPart(type = "lure", number = f.minLureLevel, text = rangeText(f.minLureLevel, f.maxLureLevel)))
        }
        rangeText(f.minLight, f.maxLight)?.let { add(WeightConditionPart(type = "light", text = it)) }
        rangeText(f.minY, f.maxY)?.let { add(WeightConditionPart(type = "height", text = it)) }
    }

    /** The anticondition form of a part: the same thing, prefixed `not_`. */
    fun negated(part: WeightConditionPart): WeightConditionPart = part.copy(type = "not_${part.type}")

    /** `2-5`, `2` for a single value, `≥2` or `≤5` when only one end is set; null when neither is. */
    fun rangeText(min: Int?, max: Int?): String? = when {
        min != null && max != null -> if (min == max) "$min" else "$min-$max"
        min != null -> "≥$min"
        max != null -> "≤$max"
        else -> null
    }
}
