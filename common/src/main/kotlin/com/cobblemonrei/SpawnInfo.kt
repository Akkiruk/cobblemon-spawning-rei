package com.cobblemonrei

data class SpawnInfo(
    val id: String,
    val pokemon: String,
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
    val neededNearbyBlocks: List<String>,
    val neededBaseBlocks: List<String>,
    val moonPhase: String?,
    val presets: List<String>
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
        get() = biomes.map { biome ->
            biome.removePrefix("#")
                .replace("cobblemon:", "")
                .replace("minecraft:", "")
                .replace("_", " ")
                .replaceFirstChar { it.uppercase() }
        }
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
