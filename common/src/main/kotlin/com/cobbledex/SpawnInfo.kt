package com.cobbledex

data class SpawnInfo(
    val id: String,
    val pokemon: String,
    val formAspects: String,
    /** Filed under the form it spawns as (see [FormRouter]), so its page needn't name the variant. */
    val onOwnFormPage: Boolean = false,
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
    val minLureLevel: Int?,
    val conditionWarnings: List<String> = emptyList(),
    /** `true` = this spawn only fires from a Poké Snack block; null = spawns ambiently (1.8.0+). */
    val isPokeSnack: Boolean? = null,
    /** `true` = only inside slime chunks. */
    val isSlimeChunk: Boolean? = null,
    /** Present when this entry came from a `pokemon-herd` spawn detail (1.8.0+). */
    val herd: HerdContext? = null,
    /** Present when this entry came from a habitat spawn pool (1.8.0+). */
    val habitat: HabitatContext? = null,
    val maxLureLevel: Int? = null,
    /** The rod and bait a fishing spawn needs, as registry ids. */
    val rodType: String? = null,
    val bait: String? = null,
    /** World-coordinate limits, such as Shellos' east and west seas. */
    val minX: Int? = null,
    val maxX: Int? = null,
    val minZ: Int? = null,
    val maxZ: Int? = null,
    /** How much vertical space the spawn needs. */
    val minHeight: Int? = null,
    val maxHeight: Int? = null,
    /** What this particular spawn drops instead of the species' normal table. */
    val spawnDrops: List<DropEntryInfo> = emptyList(),
) {
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

    companion object {
        /**
         * Builds a [SpawnInfo] with only the core identity / level fields set and every
         * environmental condition (biomes, weather, light, Y range, blocks, …) left at its
         * empty default. For spawn sources that carry no world-condition data of their own,
         * such as habitat pools.
         */
        fun minimal(
            id: String,
            pokemon: String,
            bucket: String,
            weight: Float,
            levelRange: String,
            formAspects: String = "",
            context: String = "grounded",
            minLight: Int? = null,
            maxLight: Int? = null,
            herd: HerdContext? = null,
            habitat: HabitatContext? = null,
        ): SpawnInfo = SpawnInfo(
            id = id, pokemon = pokemon, formAspects = formAspects, bucket = bucket,
            weight = weight, levelRange = levelRange, context = context,
            biomes = emptyList(), timeRange = null, weather = SpawnWeather(),
            dimensions = emptyList(), structures = emptyList(), canSeeSky = null,
            minLight = minLight, maxLight = maxLight, minSkyLight = null, maxSkyLight = null,
            minY = null, maxY = null, neededNearbyBlocks = emptyList(), neededBaseBlocks = emptyList(),
            moonPhase = null, presets = emptyList(), fluid = null, anticondition = null,
            weightMultipliers = emptyList(), minLureLevel = null, herd = herd, habitat = habitat,
        )
    }
}

/**
 * Herd-spawn context (Cobblemon 1.8.0+). The *same* [HerdContext] instance is attached to every
 * member species' [SpawnInfo] for one `pokemon-herd` detail, so it describes the whole formation, not
 * just one member. [SpawnDataIndex] groups member spawns back into per-roster [HerdInfo] pages off
 * this; the spawn page itself only uses it to know "this is a herd spawn" and point at the Herds tab.
 */
data class HerdContext(
    val members: List<HerdMemberRef>,
    val maxHerdSize: Int,
) {
    val hasAlpha: Boolean get() = members.any { it.isAlpha }

    /** Stable identity for a roster, used to merge the biome-variant copies of one herd. */
    fun rosterKey(): String =
        members.map { "${it.species}:${it.formTokens()}" }.sorted().joinToString("-") + "-x$maxHerdSize"
}

data class HerdMemberRef(
    val species: String,
    val formAspects: String,
    val role: HerdRole,
    val isAlpha: Boolean,
    val heldItemId: String?,
    /** The member's own level range within the herd, e.g. "24-43"; null falls back to the herd's. */
    val levelRange: String?,
    /** How many of this species can be in one herd ("up to N"). */
    val maxCount: Int,
    /** Relative roll weight among herd members, for ordering the roster. */
    val weight: Float,
)

/** What sets this member apart as a form. Being an alpha doesn't, and is already [HerdMemberRef.isAlpha]. */
private fun HerdMemberRef.formTokens(): String =
    formAspects.split(" ").filter { it.isNotBlank() && it != "alpha" }.sorted().joinToString(" ")

enum class HerdRole { LEADER, FOLLOWER, ANY }

/**
 * Habitat spawn-pool context (1.8.0+). Habitats run a fixed cycle of [totalPhases] phases (roughly one
 * in-game day each); [phases] is the raw spec (e.g. `"1-3, 5"`) of which phases this species appears in.
 */
data class HabitatContext(
    val habitatNameKey: String,
    val phases: String,
    val phaseCount: Int,
    val totalPhases: Int,
) {
    val habitatName: String
        get() = tr(habitatNameKey).let { if (it == habitatNameKey) titleCase(habitatNameKey.substringAfterLast('.').removeSuffix("_name")) else it }

    fun displayLines(): List<String> {
        val lines = mutableListOf(tr("cobbledex-rei-emi-jei.spawn.habitat.name", habitatName))
        if (phaseCount in 1 until totalPhases) {
            lines.add(tr("cobbledex-rei-emi-jei.spawn.habitat.phases", phaseCount, totalPhases))
        }
        return lines
    }
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
    val moonPhase: String? = null,
    val minLureLevel: Int? = null,
    val maxLureLevel: Int? = null,
    val isSlimeChunk: Boolean? = null,
    val isPokeSnack: Boolean? = null,
) {
    val isEmpty: Boolean
        get() = biomes.isEmpty() && structures.isEmpty() &&
            neededBaseBlocks.isEmpty() && neededNearbyBlocks.isEmpty() &&
            minY == null && maxY == null &&
            timeRange == null && dimensions.isEmpty() &&
            isRaining == null && isThundering == null &&
            minLight == null && maxLight == null && moonPhase == null &&
            minLureLevel == null && maxLureLevel == null && isSlimeChunk == null && isPokeSnack == null
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

    val parts = conditionParts.mapNotNull(::weightPartText)
    if (parts.isNotEmpty()) return parts.joinToString(", ")
    return conditionSummary.ifBlank { tr("cobbledex-rei-emi-jei.weight.conditional") }
}

private fun weightPartText(part: WeightConditionPart): String? {
    if (part.type.startsWith("not_")) {
        return weightPartText(part.copy(type = part.type.removePrefix("not_")))
            ?.let { tr("cobbledex-rei-emi-jei.weight.not", it) }
    }
    fun names(format: (String) -> String): String? {
        if (part.ids.isEmpty()) return null
        val names = part.ids.map(format)
        return if (names.size <= 3) names.joinToString(", ")
        else "${names.take(2).joinToString(", ")} " + tr("cobbledex-rei-emi-jei.weight.and_more", names.size - 2)
    }
    return when (part.type) {
        "always" -> tr("cobbledex-rei-emi-jei.weight.always")
        "thunderstorm" -> tr("cobbledex-rei-emi-jei.weight.thunderstorm")
        "rain" -> tr("cobbledex-rei-emi-jei.weight.rain")
        "clear" -> tr("cobbledex-rei-emi-jei.weight.clear")
        "open_sky" -> tr("cobbledex-rei-emi-jei.weight.open_sky")
        "covered" -> tr("cobbledex-rei-emi-jei.weight.covered")
        "sky_light" -> part.text?.let { tr("cobbledex-rei-emi-jei.weight.sky_light", it) }
        "dimensions" -> names(SpawnDisplayHelper::formatDimension)
        "conditional" -> tr("cobbledex-rei-emi-jei.weight.conditional")
        "time_range" -> part.text?.let { formatSpawnTime(it) }
        "biomes" -> names(::formatBiomeName)
        "structures" -> names(::formatStructureName)
        "nearby_blocks" -> names(::formatBlockName)?.let { tr("cobbledex-rei-emi-jei.weight.near", it) }
        "lure" -> part.text?.let { tr("cobbledex-rei-emi-jei.weight.lure_range", it) }
            ?: part.number?.let { tr("cobbledex-rei-emi-jei.weight.lure", it) }
        "poke_snack" -> tr("cobbledex-rei-emi-jei.weight.poke_snack")
        "slime_chunk" -> tr("cobbledex-rei-emi-jei.weight.slime_chunk")
        "moon" -> part.text?.let { formatMoonPhase(it) }
        "light" -> part.text?.let { tr("cobbledex-rei-emi-jei.weight.light", it) }
        "height" -> part.text?.let { tr("cobbledex-rei-emi-jei.weight.height", it) }
        else -> part.text
    }
}

/**
 * Moon phases are stored as Minecraft's phase numbers, as ranges (`1-3`) or lists (`0,4`), or
 * sometimes by name. `0` is the full moon and they run round to `7`.
 */
fun formatMoonPhase(raw: String): String {
    val phases = raw.split(",").map { it.trim() }.filter { it.isNotBlank() }.flatMap { segment ->
        val ends = segment.split("-").mapNotNull { it.trim().toIntOrNull() }
        when {
            ends.size == 2 && ends[0] <= ends[1] -> (ends[0]..ends[1]).toList()
            ends.size == 1 && '-' !in segment -> ends
            else -> return titleCase(raw)
        }
    }.distinct()
    return phases.joinToString(", ") { tr("cobbledex-rei-emi-jei.moon.${it.mod(8)}") }
}

/** An item id's in-game name, or a readable fallback when there is no such item. */
fun formatItemId(id: String): String {
    val clean = id.removePrefix("#")
    val key = "item.${clean.substringBefore(':', "minecraft")}.${clean.substringAfter(':').replace('/', '.')}"
    val translated = tr(key)
    return if (translated != key) translated else titleCase(stripNamespace(id).replace("/", " "))
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
        // Cobblemon labels some forms with text that names nothing: all of Arceus' are "Arceus" and
        // all of Unown's are "One form", the same as the species' own entry.
        val speciesFormLabel = tr("cobblemon.ui.pokedex.info.form.${info.baseSpeciesName}")
        val translatedForm = tr(specificFormKey).let { specific ->
            val usable = specific != specificFormKey && specific.isNotBlank() &&
                specific != translatedSpecies && specific != speciesFormLabel
            if (usable) specific else tr(genericFormKey).let { generic ->
                if (generic == genericFormKey || generic.isBlank()) titleCase(formNameLower.replace("-", " "))
                else generic
            }
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
                "${tickToTime(start)}-${tickToTime(end)}"
            } else segment.trim()
        } else segment.trim()
    }
}

/** A spawn's time as a player would say it: a tick range covering the night just reads "Night". */
fun formatSpawnTime(raw: String): String {
    if (raw.any { it.isLetter() }) return formatTimeRange(raw)
    return when (SpawnPageModel.normalizeTime(raw)) {
        SpawnPageModel.TimeLabel.DAY -> tr("cobbledex-rei-emi-jei.spawn.time.day")
        SpawnPageModel.TimeLabel.NIGHT -> tr("cobbledex-rei-emi-jei.spawn.time.night")
        SpawnPageModel.TimeLabel.DAWN -> tr("cobbledex-rei-emi-jei.spawn.time.dawn")
        SpawnPageModel.TimeLabel.DUSK -> tr("cobbledex-rei-emi-jei.spawn.time.dusk")
        else -> formatTimeRange(raw)
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
    // Cobblemon's regional evolution tags, e.g. `#cobblemon:evolution/regional/pikachu_alolabiome`.
    REGIONAL_EVOLUTION_BIOME.find(id.lowercase())?.let {
        return tr("cobbledex-rei-emi-jei.biome.regional", titleCase(it.groupValues[1]))
    }
    val cleaned = stripNamespace(id)
        .split("/")
        .joinToString("_") { it.removePrefix("is_").removePrefix("has_") }
    val key = "cobbledex-rei-emi-jei.biome.${cleaned.lowercase()}"
    val translated = tr(key)
    return if (translated != key) translated else titleCase(cleaned)
}

private val REGIONAL_EVOLUTION_BIOME = Regex("evolution/regional/.*_([a-z]+)biome$")

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
        return when {
            key == "region_bias" -> regionFormName(value)
            // A flag feature ("candle-pot=true") names the variant by its key; "True" would say nothing.
            value == "true" -> titleCase(key)
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
