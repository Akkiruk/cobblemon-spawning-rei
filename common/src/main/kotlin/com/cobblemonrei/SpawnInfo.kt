package com.cobblemonrei

data class SpawnInfo(
    val id: String,
    val pokemon: String,
    val formAspects: String,
    val bucket: String,
    val weight: Float,
    val levelRange: String,
    val context: String,
    val biomes: List<String>,
    val timeRange: String?,
    val weather: SpawnWeather,
    val dimensions: List<String>,
    val structures: List<String>,
    val canSeeSky: Boolean?,
    val minLight: Int?,
    val maxLight: Int?,
    val minSkyLight: Int?,
    val maxSkyLight: Int?,
    val minY: Int?,
    val maxY: Int?,
    val neededNearbyBlocks: List<String>,
    val neededBaseBlocks: List<String>,
    val moonPhase: String?,
    val presets: List<String>,
    val fluid: String?,
    val anticondition: SpawnAntiCondition?,
    val weightMultipliers: List<WeightMultiplier>,
    val minLureLevel: Int?
) {
    val hasFormVariant: Boolean
        get() = formAspects.isNotBlank()

    val displayContext: String
        get() = when (context.lowercase()) {
            "grounded" -> tr("cobblemon-spawning-rei.context.land")
            "submerged" -> tr("cobblemon-spawning-rei.context.underwater")
            "surface" -> tr("cobblemon-spawning-rei.context.surface")
            "seafloor" -> tr("cobblemon-spawning-rei.context.seafloor")
            "fishing" -> tr("cobblemon-spawning-rei.context.fishing")
            else -> titleCase(context)
        }

    val isFishing: Boolean
        get() = context.equals("fishing", ignoreCase = true)
}

data class SpawnWeather(
    val isRaining: Boolean? = null,
    val isThundering: Boolean? = null
) {
    val displayText: String
        get() = when {
            isThundering == true -> tr("cobblemon-spawning-rei.weather.thunder")
            isRaining == true -> tr("cobblemon-spawning-rei.weather.rain")
            isRaining == false -> tr("cobblemon-spawning-rei.weather.clear")
            else -> tr("cobblemon-spawning-rei.weather.any")
        }
}

data class SpawnAntiCondition(
    val biomes: List<String> = emptyList(),
    val structures: List<String> = emptyList(),
    val neededBaseBlocks: List<String> = emptyList(),
    val neededNearbyBlocks: List<String> = emptyList(),
    val minY: Int? = null,
    val maxY: Int? = null
) {
    val isEmpty: Boolean
        get() = biomes.isEmpty() && structures.isEmpty() &&
            neededBaseBlocks.isEmpty() && neededNearbyBlocks.isEmpty() &&
            minY == null && maxY == null
}

data class WeightMultiplier(
    val multiplier: Float,
    val conditionSummary: String
)

fun titleCase(raw: String): String {
    return raw.replace("_", " ")
        .split(" ")
        .filter { it.isNotBlank() }
        .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
}

/**
 * Format a species name for display, handling special characters properly.
 * Converts normalized names like "mrmime" to "Mr. Mime".
 */
fun formatSpeciesName(speciesName: String): String {
    val displayName = SpeciesNameNormalizer.toDisplayName(speciesName)
    return titleCase(displayName)
}

fun stripNamespace(id: String): String {
    return id.removePrefix("#").substringAfter(":")
}

fun formatId(id: String): String {
    return titleCase(stripNamespace(id).replace("/", " "))
}

fun formatBiomeName(id: String): String {
    if (id.lowercase().endsWith("custom_spawn")) return tr("cobblemon-spawning-rei.biome.altar_only")
    return titleCase(
        stripNamespace(id)
            .substringBefore("/")
            .removePrefix("is_")
            .removePrefix("has_")
    )
}

fun sanitizePath(s: String): String = s.lowercase().replace(Regex("[^a-z0-9/._-]"), "")

private val REGION_ADJECTIVES = mapOf(
    "alola" to "Alolan", "alolan" to "Alolan",
    "galar" to "Galarian", "galarian" to "Galarian",
    "hisui" to "Hisuian", "hisuian" to "Hisuian",
    "paldea" to "Paldean", "paldean" to "Paldean"
)

fun formatAspect(aspect: String): String {
    val lower = aspect.lowercase().trim()
    if (lower.contains("=")) {
        val key = lower.substringBefore("=").trim()
        val value = lower.substringAfter("=").trim()
        return when (key) {
            "region_bias" -> REGION_ADJECTIVES[value] ?: titleCase(value)
            else -> titleCase(value)
        }
    }
    return REGION_ADJECTIVES[lower] ?: titleCase(aspect)
}

fun formatTypeName(rawType: String): String {
    val key = "cobblemon.type.${rawType.lowercase()}"
    val translated = tr(key)
    return if (translated == key) titleCase(rawType) else translated
}
