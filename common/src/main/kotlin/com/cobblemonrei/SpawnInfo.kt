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
    val bucketColor: Int
        get() = when (bucket.lowercase()) {
            "common" -> 0xFF55FF55.toInt()
            "uncommon" -> 0xFFFFFF55.toInt()
            "rare" -> 0xFFFF5555.toInt()
            "ultra-rare" -> 0xFFFF55FF.toInt()
            else -> 0xFFFFFFFF.toInt()
        }

    val formattedBiomes: List<String>
        get() = biomes.map { formatId(it) }

    val hasFormVariant: Boolean
        get() = formAspects.isNotBlank()

    val displayContext: String
        get() = when (context.lowercase()) {
            "grounded" -> "Land"
            "submerged" -> "Underwater"
            "surface" -> "Water Surface"
            "seafloor" -> "Seafloor"
            "fishing" -> "Fishing"
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
            isThundering == true -> "Thunder"
            isRaining == true -> "Rain"
            isRaining == false -> "Clear"
            else -> "Any"
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

    val formattedBiomes: List<String>
        get() = biomes.map { formatId(it) }
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

fun stripNamespace(id: String): String {
    return id.removePrefix("#").substringAfter(":")
}

fun formatId(id: String): String {
    return titleCase(stripNamespace(id))
}

fun formatBiomeName(id: String): String {
    if (id.lowercase().endsWith("custom_spawn")) return "\u2726 Altar/Special Only"
    return titleCase(
        stripNamespace(id)
            .removePrefix("is_")
            .removePrefix("has_")
    )
}
