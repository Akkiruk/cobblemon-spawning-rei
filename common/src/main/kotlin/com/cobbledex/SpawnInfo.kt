package com.cobbledex

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
            "grounded" -> tr("cobbledex-rei-emi-jei.context.land")
            "submerged" -> tr("cobbledex-rei-emi-jei.context.underwater")
            "surface" -> tr("cobbledex-rei-emi-jei.context.surface")
            "seafloor" -> tr("cobbledex-rei-emi-jei.context.seafloor")
            "fishing" -> tr("cobbledex-rei-emi-jei.context.fishing")
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
            isThundering == true -> tr("cobbledex-rei-emi-jei.weather.thunder")
            isRaining == true -> tr("cobbledex-rei-emi-jei.weather.rain")
            isRaining == false -> tr("cobbledex-rei-emi-jei.weather.clear")
            else -> tr("cobbledex-rei-emi-jei.weather.any")
        }
}

data class SpawnAntiCondition(
    val biomes: List<String> = emptyList(),
    val structures: List<String> = emptyList(),
    val neededBaseBlocks: List<String> = emptyList(),
    val neededNearbyBlocks: List<String> = emptyList(),
    val minY: Int? = null,
    val maxY: Int? = null,
    val timeRange: String? = null,
    val dimensions: List<String> = emptyList(),
    val isRaining: Boolean? = null,
    val isThundering: Boolean? = null,
    val minLight: Int? = null,
    val maxLight: Int? = null,
    val moonPhase: String? = null
) {
    val isEmpty: Boolean
        get() = biomes.isEmpty() && structures.isEmpty() &&
            neededBaseBlocks.isEmpty() && neededNearbyBlocks.isEmpty() &&
            minY == null && maxY == null &&
            timeRange == null && dimensions.isEmpty() &&
            isRaining == null && isThundering == null &&
            minLight == null && maxLight == null && moonPhase == null
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
 * Format a species name for display, handling regional forms.
 * Converts "diglettalolan" to "Alolan Diglett", "mrmime" to "Mr. Mime".
 */
fun formatSpeciesName(speciesName: String): String {
    val decomp = SpeciesNameNormalizer.decomposeFormSpecies(speciesName)
    val baseDisplay = SpeciesNameNormalizer.toDisplayName(decomp.baseName)
    val baseName = titleCase(baseDisplay)
    return if (decomp.regionAdjective != null) "${decomp.regionAdjective} $baseName" else baseName
}

fun stripNamespace(id: String): String {
    return id.removePrefix("#").substringAfter(":")
}

fun formatId(id: String): String {
    return titleCase(stripNamespace(id).replace("/", " "))
}

fun formatBiomeName(id: String): String {
    if (id.lowercase().endsWith("custom_spawn")) return tr("cobbledex-rei-emi-jei.biome.altar_only")
    val cleaned = stripNamespace(id)
        .substringBefore("/")
        .removePrefix("is_")
        .removePrefix("has_")
    val key = "cobbledex-rei-emi-jei.biome.${cleaned.lowercase()}"
    val translated = tr(key)
    return if (translated != key) translated else titleCase(cleaned)
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
