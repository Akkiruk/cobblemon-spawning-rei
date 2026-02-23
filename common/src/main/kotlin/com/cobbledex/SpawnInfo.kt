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
    val conditionSummary: String = "",
    val conditionParts: List<WeightConditionPart> = emptyList()
)

data class WeightConditionPart(
    val type: String,
    val text: String? = null,
    val number: Int? = null,
    val ids: List<String> = emptyList()
)

fun WeightMultiplier.displayConditionSummary(): String {
    if (conditionParts.isEmpty()) {
        return conditionSummary.ifBlank { tr("cobbledex-rei-emi-jei.weight.conditional") }
    }

    val parts = conditionParts.mapNotNull { part ->
        when (part.type) {
            "always" -> tr("cobbledex-rei-emi-jei.weight.always")
            "thunderstorm" -> tr("cobbledex-rei-emi-jei.weight.thunderstorm")
            "rain" -> tr("cobbledex-rei-emi-jei.weight.rain")
            "conditional" -> tr("cobbledex-rei-emi-jei.weight.conditional")
            "time_range" -> part.text?.let { formatTimeRange(it) }
            "biomes" -> {
                if (part.ids.isEmpty()) null
                else {
                    val names = part.ids.map { formatId(it) }
                    if (names.size <= 3) names.joinToString(", ")
                    else "${names.take(2).joinToString(", ")} " + tr("cobbledex-rei-emi-jei.weight.and_more", names.size - 2)
                }
            }
            "lure" -> part.number?.let { tr("cobbledex-rei-emi-jei.weight.lure", it) }
            else -> part.text
        }
    }

    if (parts.isNotEmpty()) return parts.joinToString(", ")
    return conditionSummary.ifBlank { tr("cobbledex-rei-emi-jei.weight.conditional") }
}

fun titleCase(raw: String): String {
    return raw.replace("_", " ")
        .split(" ")
        .filter { it.isNotBlank() }
        .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
}

/**
 * Format a species name for display using Cobblemon's translation system.
 * Respects the client's language setting (e.g. French: "Bulbasaur" → "Bulbizarre").
 * For form entries, uses Cobblemon's form translation keys at render time (O11).
 */
fun formatSpeciesName(speciesName: String): String {
    // Check if this is a form entry with stored form metadata
    val info = SpawnDataIndex.getSpeciesInfo(speciesName)
    if (info?.formName != null && info.baseSpeciesName != null) {
        val speciesKey = "cobblemon.species.${info.baseSpeciesName}.name"
        val translatedSpecies = tr(speciesKey).let {
            if (it == speciesKey) titleCase(SpeciesNameNormalizer.toDisplayName(info.baseSpeciesName)) else it
        }
        // Try specific form key first, then generic, then titleCase fallback
        val formNameLower = info.formName
        val specificFormKey = "cobblemon.ui.pokedex.info.form.${info.baseSpeciesName}-$formNameLower"
        val genericFormKey = "cobblemon.ui.pokedex.info.form.$formNameLower"
        // Also try with hyphens preserved (Cobblemon uses inconsistent separators)
        val translatedForm = tr(specificFormKey).let { specific ->
            if (specific == specificFormKey) tr(genericFormKey).let { generic ->
                if (generic == genericFormKey) titleCase(formNameLower.replace("-", " "))
                else generic
            } else specific
        }
        return tr("cobbledex-rei-emi-jei.display.form_name", translatedSpecies, translatedForm)
    }

    val decomp = SpeciesNameNormalizer.decomposeFormSpecies(speciesName)
    val translationKey = "cobblemon.species.${decomp.baseName}.name"
    val translated = tr(translationKey)
    val baseName = if (translated != translationKey) {
        translated
    } else {
        titleCase(SpeciesNameNormalizer.toDisplayName(decomp.baseName))
    }
    if (decomp.regionKey != null) {
        val formKey = "cobblemon.ui.pokedex.info.form.${decomp.regionKey}"
        val formTranslated = tr(formKey)
        if (formTranslated != formKey) return "$formTranslated $baseName"
    }
    return baseName
}

fun stripNamespace(id: String): String {
    return id.removePrefix("#").substringAfter(":")
}

fun formatId(id: String): String {
    return titleCase(stripNamespace(id).replace("/", " "))
}

/**
 * Translate a block ID using Minecraft's block translation keys.
 * Falls back to titleCase if no translation exists.
 */
fun formatBlockName(id: String): String {
    val cleaned = id.removePrefix("#")
    val namespace = if (":" in cleaned) cleaned.substringBefore(":") else "minecraft"
    val path = cleaned.substringAfter(":")
    // Try block translation key first
    val blockKey = "block.$namespace.$path"
    val translated = tr(blockKey)
    if (translated != blockKey) return translated
    // For Cobblemon blocks, also try cobblemon-specific patterns
    if (namespace == "cobblemon") {
        val cobbleKey = "block.cobblemon.$path"
        val cobbleTranslated = tr(cobbleKey)
        if (cobbleTranslated != cobbleKey) return cobbleTranslated
    }
    return titleCase(path.replace("_", " "))
}

/**
 * Translate a structure ID. Uses our own translation keys first,
 * then Minecraft's structure translation keys, then falls back to titleCase.
 */
fun formatStructureName(id: String): String {
    val cleaned = id.removePrefix("#")
    val path = stripNamespace(cleaned).replace("/", "_")
    // Try our own translation key
    val ourKey = "cobbledex-rei-emi-jei.structure.$path"
    val ourTranslated = tr(ourKey)
    if (ourTranslated != ourKey) return ourTranslated
    return titleCase(stripNamespace(cleaned).replace("/", " "))
}

/**
 * Translate an ability name using Cobblemon's translation keys.
 */
fun formatAbilityName(name: String): String {
    val key = "cobblemon.ability.${name.lowercase().replace(" ", "").replace("-", "").replace("_", "")}"
    val translated = tr(key)
    return if (translated != key) translated else name
}

/**
 * Translate an egg group name using Cobblemon's translation keys.
 */
fun formatEggGroupName(group: String): String {
    val normalized = group.lowercase().replace("-", "_").replace(" ", "_")
    val key = "cobbledex-rei-emi-jei.egg_group.$normalized"
    val translated = tr(key)
    if (translated != key) return translated
    // Fallback to Cobblemon's own key if available
    val cobbleKey = "cobblemon.egg_group.$normalized"
    val cobbleTranslated = tr(cobbleKey)
    if (cobbleTranslated != cobbleKey) return cobbleTranslated
    return titleCase(group.replace("-", " "))
}

/**
 * Translate an EXP group name.
 */
fun formatExpGroup(group: String): String {
    val normalized = group.lowercase().replace(" ", "_")
    val key = "cobbledex-rei-emi-jei.exp_group.$normalized"
    val translated = tr(key)
    return if (translated != key) translated else titleCase(group.replace("_", " "))
}

/**
 * Convert tick-based time ranges to human-readable 24h clock format.
 * Minecraft day ticks: 0 = 6:00, 6000 = 12:00, 12000 = 18:00, 18000 = 0:00, 24000 = 6:00
 */
fun formatTimeRange(raw: String): String {
    // Check for named time ranges first ("day", "night", etc.)
    val lower = raw.lowercase().trim()
    if (lower.contains(Regex("[a-z]"))) return titleCase(raw)

    return raw.split(",").joinToString(", ") { segment ->
        val parts = segment.trim().split("-")
        if (parts.size == 2) {
            val start = parts[0].trim().toIntOrNull()
            val end = parts[1].trim().toIntOrNull()
            if (start != null && end != null) {
                "${tickToTime(start)}\u2013${tickToTime(end)}"
            } else segment.trim()
        } else segment.trim()
    }
}

private fun tickToTime(tick: Int): String {
    // Minecraft: tick 0 = 6:00 AM, tick 6000 = 12:00, tick 12000 = 18:00, tick 18000 = 0:00
    val totalMinutes = ((tick + 6000) % 24000) * 60 / 1000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return "%02d:%02d".format(hours, minutes)
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

private val REGION_KEYS = setOf("alola", "alolan", "galar", "galarian", "hisui", "hisuian", "paldea", "paldean")

private fun regionFormName(region: String): String {
    val normalized = region.removeSuffix("n").removeSuffix("a").let {
        if (it == "galari") "galar" else it
    }
    val key = "cobblemon.ui.pokedex.info.form.$normalized"
    val translated = tr(key)
    return if (translated != key) translated else titleCase(region)
}

fun formatAspect(aspect: String): String {
    val lower = aspect.lowercase().trim()
    if (lower.contains("=")) {
        val key = lower.substringBefore("=").trim()
        val value = lower.substringAfter("=").trim()
        return when (key) {
            "region_bias" -> regionFormName(value)
            else -> titleCase(value)
        }
    }
    if (lower in REGION_KEYS) return regionFormName(lower)
    return titleCase(aspect)
}

fun formatTypeName(rawType: String): String {
    val key = "cobblemon.type.${rawType.lowercase()}"
    val translated = tr(key)
    return if (translated == key) titleCase(rawType) else translated
}
