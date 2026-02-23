package com.cobbledex

data class EvolutionInfo(
    val id: String,
    val fromSpecies: String,
    val fromAspects: Set<String> = emptySet(),
    val toSpecies: String,
    val toAspects: Set<String> = emptySet(),
    val variant: String,
    val requirements: List<EvolutionRequirement>,
    val requiredContext: String?,
    val consumeHeldItem: Boolean,
    val collapsedVariantCount: Int = 0
) {
    fun withVariantNote(count: Int) = copy(collapsedVariantCount = count)

    val displayFromName: String
        get() {
            val base = formatSpeciesName(fromSpecies)
            return if (fromAspects.isEmpty()) base
            else "$base (${formatAspects(fromAspects)})"
        }

    val displayToName: String
        get() {
            val base = formatSpeciesName(toSpecies)
            return if (toAspects.isEmpty()) base
            else "$base (${formatAspects(toAspects)})"
        }

    private fun formatAspects(aspects: Set<String>): String {
        return aspects.joinToString(", ") { formatAspect(it) }
    }

    val displayRequirements: String
        get() {
            if (variant == "form_change" && requiredContext != null) return appendVariantNote(requiredContext)

            val parts = mutableListOf<String>()

            if (requiredContext != null && requiredContext.isNotBlank()) {
                val itemName = requiredContext
                    .let { if (it.contains(":")) it.substringAfter(":") else it }
                    .let { if (it.contains("=")) it.substringAfter("=").substringBefore(" ") else it }
                    .replace("_", " ")
                    .split(" ")
                    .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                when {
                    variant.contains("item_interact") -> parts.add(tr("cobbledex-rei-emi-jei.evo.use_item_named", itemName))
                    variant == "trade" -> parts.add(tr("cobbledex-rei-emi-jei.evo.trade_with", itemName))
                    variant.contains("block_click") -> parts.add(tr("cobbledex-rei-emi-jei.evo.click_block", itemName))
                    itemName.isNotBlank() -> parts.add(tr("cobbledex-rei-emi-jei.evo.use_item_named", itemName))
                }
            }

            if (variant == "trade" && requiredContext == null) {
                parts.add(tr("cobbledex-rei-emi-jei.evo.trade"))
            }

            for (req in requirements) {
                parts.add(req.displayText)
            }

            return if (parts.isEmpty()) {
                val base = when {
                    variant.contains("level") -> tr("cobbledex-rei-emi-jei.evo.level_up")
                    variant.contains("trade") -> tr("cobbledex-rei-emi-jei.evo.trade")
                    variant.contains("item") -> tr("cobbledex-rei-emi-jei.evo.use_item")
                    else -> tr("cobbledex-rei-emi-jei.evo.level_up")
                }
                appendVariantNote(base)
            } else appendVariantNote(parts.joinToString(", "))
        }

    val itemRequirements: List<EvolutionItemInfo>
        get() {
            val items = mutableListOf<EvolutionItemInfo>()
            if (requiredContext != null && requiredContext.isNotBlank()) {
                val rawId = requiredContext.trim()
                if (rawId.contains(":") && !rawId.contains("@") && rawId.length < 60) {
                    when {
                        variant.contains("item_interact") -> items.add(EvolutionItemInfo(rawId, tr("cobbledex-rei-emi-jei.evo.item.use")))
                        variant.contains("block_click") -> items.add(EvolutionItemInfo(rawId, tr("cobbledex-rei-emi-jei.evo.item.click")))
                    }
                }
            }
            for (req in requirements) {
                when (req.variant) {
                    "held_item" -> {
                        val id = req.data["itemCondition"]?.toString()
                        if (id != null && id.contains(":") && !id.contains("@") && id.length < 60)
                            items.add(EvolutionItemInfo(id, tr("cobbledex-rei-emi-jei.evo.item.hold")))
                    }
                    "owner_holds_item" -> {
                        val id = req.data["itemCondition"]?.toString()
                        if (id != null && id.contains(":") && !id.contains("@") && id.length < 60)
                            items.add(EvolutionItemInfo(id, tr("cobbledex-rei-emi-jei.evo.item.player_holds")))
                    }
                }
            }
            return items
        }

    val textOnlyRequirements: String
        get() {
            if (variant == "form_change" && requiredContext != null) return appendVariantNote(requiredContext)

            val parts = mutableListOf<String>()
            if (variant == "trade") parts.add(tr("cobbledex-rei-emi-jei.evo.trade"))
            for (req in requirements) {
                if (req.variant == "held_item" || req.variant == "owner_holds_item") continue
                parts.add(req.displayText)
            }
            if (parts.isEmpty() && itemRequirements.isEmpty()) {
                when {
                    variant.contains("level") -> parts.add(tr("cobbledex-rei-emi-jei.evo.level_up"))
                    variant.contains("trade") -> parts.add(tr("cobbledex-rei-emi-jei.evo.trade"))
                    variant.contains("item") -> parts.add(tr("cobbledex-rei-emi-jei.evo.use_item"))
                    else -> parts.add(tr("cobbledex-rei-emi-jei.evo.level_up"))
                }
            }
            return appendVariantNote(parts.joinToString(", "))
        }

    private fun appendVariantNote(text: String): String {
        if (collapsedVariantCount <= 0) return text
        return "$text (+$collapsedVariantCount variants)"
    }
}

data class EvolutionItemInfo(val itemId: String, val label: String)

data class EvolutionRequirement(
    val variant: String,
    val data: Map<String, Any>
) {
    // Helper to clean up potentially garbage strings from reflection
    private fun cleanValue(value: Any?): String? {
        val str = value?.toString() ?: return null
        if (str.contains("@") || (str.contains(".") && str.length > 40)) return null
        return str
    }

    val displayText: String
        get() = when (variant) {
            "level" -> {
                val min = (data["minLevel"] as? Number)?.toInt()
                if (min != null && min > 0) tr("cobbledex-rei-emi-jei.evo.level_min", min) else tr("cobbledex-rei-emi-jei.evo.level_up")
            }
            "friendship" -> {
                val amount = (data["amount"] as? Number)?.toInt() ?: 160
                tr("cobbledex-rei-emi-jei.evo.friendship", amount)
            }
            "time_range" -> {
                val range = cleanValue(data["range"])?.lowercase() ?: "time"
                when (range) {
                    "day" -> tr("cobbledex-rei-emi-jei.evo.time.daytime")
                    "night" -> tr("cobbledex-rei-emi-jei.evo.time.nighttime")
                    "dusk" -> tr("cobbledex-rei-emi-jei.evo.time.dusk")
                    "dawn" -> tr("cobbledex-rei-emi-jei.evo.time.dawn")
                    "twilight" -> tr("cobbledex-rei-emi-jei.evo.time.twilight")
                    "midnight" -> tr("cobbledex-rei-emi-jei.evo.time.midnight")
                    "time", "" -> tr("cobbledex-rei-emi-jei.evo.time.time_based")
                    else -> titleCase(range)
                }
            }
            "held_item" -> {
                val item = formatItemName(cleanValue(data["itemCondition"]))
                tr("cobbledex-rei-emi-jei.evo.hold", item)
            }
            "has_move_type", "move_type" -> {
                val type = cleanValue(data["type"])?.let { titleCase(it) } ?: "type"
                tr("cobbledex-rei-emi-jei.evo.know_type_move", type)
            }
            "move_set", "has_move" -> {
                val move = formatMoveName(cleanValue(data["move"]))
                tr("cobbledex-rei-emi-jei.evo.know_move", move)
            }
            "biome" -> {
                val biome = cleanValue(data["biomeCondition"])
                val antibiome = cleanValue(data["biomeAnticondition"])
                when {
                    biome != null -> tr("cobbledex-rei-emi-jei.evo.in_biome", formatBiome(biome))
                    antibiome != null -> tr("cobbledex-rei-emi-jei.evo.not_in_biome", formatBiome(antibiome))
                    else -> tr("cobbledex-rei-emi-jei.evo.biome_specific")
                }
            }
            "structure" -> {
                val struct = cleanValue(data["structureCondition"])
                val antiStruct = cleanValue(data["structureAnticondition"])
                when {
                    struct != null -> tr("cobbledex-rei-emi-jei.evo.in_structure", formatStructure(struct))
                    antiStruct != null -> tr("cobbledex-rei-emi-jei.evo.not_in_structure", formatStructure(antiStruct))
                    else -> tr("cobbledex-rei-emi-jei.evo.structure_specific")
                }
            }
            "stat_compare" -> {
                val high = cleanValue(data["highStat"])?.let { titleCase(it) } ?: "Stat"
                val low = cleanValue(data["lowStat"])?.let { titleCase(it) } ?: "Stat"
                tr("cobbledex-rei-emi-jei.evo.stat_higher", high, low)
            }
            "stat_equal" -> {
                val s1 = cleanValue(data["statOne"])?.let { titleCase(it) } ?: "Stat"
                val s2 = cleanValue(data["statTwo"])?.let { titleCase(it) } ?: "Stat"
                tr("cobbledex-rei-emi-jei.evo.stat_equal", s1, s2)
            }
            "pokemon_properties", "properties" -> {
                val target = cleanValue(data["target"]) ?: ""
                parsePropertiesTarget(target)
            }
            "property_range" -> {
                val feature = cleanValue(data["feature"])?.let { titleCase(it) } ?: ""
                val range = cleanValue(data["range"]) ?: ""
                when {
                    feature.isNotBlank() && range.isNotBlank() -> tr("cobbledex-rei-emi-jei.evo.feature_range", feature, range)
                    feature.isNotBlank() -> feature
                    else -> tr("cobbledex-rei-emi-jei.evo.special_condition")
                }
            }
            "blocks_traveled" -> {
                val amount = (data["amount"] as? Number)?.toInt()
                if (amount != null) tr("cobbledex-rei-emi-jei.evo.walk_blocks", amount) else tr("cobbledex-rei-emi-jei.evo.walk_distance")
            }
            "use_move" -> {
                val move = formatMoveName(cleanValue(data["move"]))
                val amount = (data["amount"] as? Number)?.toInt()
                if (amount != null) tr("cobbledex-rei-emi-jei.evo.use_move_times", move, amount) else tr("cobbledex-rei-emi-jei.evo.use_move", move)
            }
            "defeat" -> {
                val target = cleanValue(data["target"]) ?: ""
                val amount = (data["amount"] as? Number)?.toInt()
                val targetName = target.split(" ").firstOrNull()?.let { titleCase(it) } ?: target
                if (amount != null) tr("cobbledex-rei-emi-jei.evo.defeat_count", amount, targetName) else tr("cobbledex-rei-emi-jei.evo.defeat", targetName)
            }
            "recoil" -> {
                val amount = (data["amount"] as? Number)?.toInt()
                if (amount != null) tr("cobbledex-rei-emi-jei.evo.recoil_amount", amount) else tr("cobbledex-rei-emi-jei.evo.recoil")
            }
            "damage_taken" -> {
                val amount = (data["amount"] as? Number)?.toInt()
                if (amount != null) tr("cobbledex-rei-emi-jei.evo.damage_taken_amount", amount) else tr("cobbledex-rei-emi-jei.evo.damage_taken")
            }
            "battle_critical_hits" -> {
                val amount = (data["amount"] as? Number)?.toInt()
                if (amount != null) tr("cobbledex-rei-emi-jei.evo.critical_hits_count", amount) else tr("cobbledex-rei-emi-jei.evo.critical_hits")
            }
            "party_member", "party" -> {
                val target = cleanValue(data["target"])?.split(" ")?.firstOrNull()?.let { titleCase(it) }
                val contains = data["contains"] as? Boolean ?: true
                if (target != null && target.isNotBlank()) {
                    if (contains) tr("cobbledex-rei-emi-jei.evo.party_has", target) else tr("cobbledex-rei-emi-jei.evo.party_no", target)
                } else tr("cobbledex-rei-emi-jei.evo.party_condition")
            }
            "moon_phase" -> {
                val phase = cleanValue(data["moonPhase"])?.let { titleCase(it) } ?: "Full Moon"
                phase
            }
            "weather" -> {
                val raining = data["isRaining"] as? Boolean
                if (raining == true) tr("cobbledex-rei-emi-jei.evo.raining") else tr("cobbledex-rei-emi-jei.evo.clear_weather")
            }
            "advancement" -> {
                val adv = cleanValue(data["requiredAdvancement"])
                    ?.substringAfterLast("/")
                    ?.substringAfterLast(":")
                    ?.let { titleCase(it) }
                adv ?: tr("cobbledex-rei-emi-jei.evo.advancement")
            }
            "world" -> {
                val id = cleanValue(data["identifier"])?.substringAfterLast(":")
                    ?.let { titleCase(it) }
                tr("cobbledex-rei-emi-jei.evo.in_dimension", id ?: "dimension")
            }
            "attack_defence_ratio" -> {
                val ratio = cleanValue(data["ratio"])?.lowercase()
                when (ratio) {
                    "attack_higher" -> tr("cobbledex-rei-emi-jei.evo.attack_gt_defense")
                    "defence_higher", "defense_higher" -> tr("cobbledex-rei-emi-jei.evo.defense_gt_attack")
                    "equal" -> tr("cobbledex-rei-emi-jei.evo.attack_eq_defense")
                    else -> ratio?.let { titleCase(it) } ?: tr("cobbledex-rei-emi-jei.evo.stat_ratio")
                }
            }
            "owner_holds_item" -> {
                val item = formatItemName(cleanValue(data["itemCondition"]))
                tr("cobbledex-rei-emi-jei.evo.player_holds", item)
            }
            "gender" -> {
                val gender = cleanValue(data["gender"])?.lowercase()
                when (gender) {
                    "male" -> tr("cobbledex-rei-emi-jei.evo.male")
                    "female" -> tr("cobbledex-rei-emi-jei.evo.female")
                    else -> gender?.let { titleCase(it) } ?: tr("cobbledex-rei-emi-jei.evo.gender_condition")
                }
            }
            "shiny" -> tr("cobbledex-rei-emi-jei.evo.shiny")
            "nature" -> {
                val nature = cleanValue(data["nature"])?.let { titleCase(it) }
                if (nature != null) tr("cobbledex-rei-emi-jei.evo.nature", nature) else tr("cobbledex-rei-emi-jei.evo.specific_nature")
            }
            "max_pokemon_level" -> {
                val max = (data["maxLevel"] as? Number)?.toInt()
                if (max != null) tr("cobbledex-rei-emi-jei.evo.max_level", max) else tr("cobbledex-rei-emi-jei.evo.level_cap")
            }
            "walking_steps" -> {
                val amount = (data["amount"] as? Number)?.toInt()
                if (amount != null) tr("cobbledex-rei-emi-jei.evo.walk_steps", amount) else tr("cobbledex-rei-emi-jei.evo.walk_distance")
            }
            "damage_dealt" -> {
                val amount = (data["amount"] as? Number)?.toInt()
                if (amount != null) tr("cobbledex-rei-emi-jei.evo.deal_damage_amount", amount) else tr("cobbledex-rei-emi-jei.evo.deal_damage")
            }
            "status" -> {
                val status = cleanValue(data["status"])?.let { titleCase(it) }
                status ?: tr("cobbledex-rei-emi-jei.evo.status_condition")
            }
            else -> {
                val fallback = titleCase(variant)
                if (fallback.length > 40 || fallback.contains("@") || (fallback.contains(".") && fallback.length > 30))
                    tr("cobbledex-rei-emi-jei.evo.special_condition")
                else fallback
            }
        }

    private fun parsePropertiesTarget(target: String): String {
        if (target.isBlank()) return tr("cobbledex-rei-emi-jei.evo.special_condition")
        if (target.contains("@") || (target.contains(".") && target.length > 40)) return tr("cobbledex-rei-emi-jei.evo.special_condition")
        val lower = target.lowercase().trim()
        return when {
            lower == "male" || lower.contains("gender=male") -> tr("cobbledex-rei-emi-jei.evo.male")
            lower == "female" || lower.contains("gender=female") -> tr("cobbledex-rei-emi-jei.evo.female")
            lower.contains("gender=") -> {
                val g = lower.substringAfter("gender=").substringBefore(" ").replaceFirstChar { it.uppercase() }
                g.ifBlank { tr("cobbledex-rei-emi-jei.evo.gender_condition") }
            }
            lower.contains("form=") -> {
                val form = lower.substringAfter("form=").substringBefore(" ").trim()
                    .replace("_", " ").split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                tr("cobbledex-rei-emi-jei.evo.form", form)
            }
            lower.contains("aspect=") -> {
                val aspect = lower.substringAfter("aspect=").substringBefore(" ").trim()
                    .replace("_", " ").split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                tr("cobbledex-rei-emi-jei.evo.form", aspect)
            }
            lower.contains("shiny") -> tr("cobbledex-rei-emi-jei.evo.shiny")
            lower.contains("region_bias=") -> {
                val region = lower.substringAfter("region_bias=").substringBefore(" ")
                    .replace("_", " ").split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                tr("cobbledex-rei-emi-jei.evo.region", region)
            }
            lower.contains("type=") -> {
                val type = lower.substringAfter("type=").substringBefore(" ").replaceFirstChar { it.uppercase() }
                tr("cobbledex-rei-emi-jei.evo.type", type)
            }
            else -> {
                // Parse generic feature=value properties (e.g., "gimmighoul gimmighoul_coins=999")
                val featureMatch = parseFeatureProperties(lower)
                if (featureMatch != null) featureMatch
                else if (target.length <= 30) titleCase(target)
                else tr("cobbledex-rei-emi-jei.evo.special_condition")
            }
        }
    }

    /**
     * Parse "species feature_name=numeric_value" patterns from properties targets.
     * e.g. "gimmighoul gimmighoul_coins=999" → "Collect 999 Gimmighoul Coins"
     */
    private fun parseFeatureProperties(target: String): String? {
        val parts = target.trim().split(" ").filter { it.isNotBlank() }
        val featureParts = parts.filter { it.contains("=") }
        if (featureParts.isEmpty()) return null

        val results = featureParts.mapNotNull { part ->
            val key = part.substringBefore("=").trim()
            val value = part.substringAfter("=").trim()
            if (key.isBlank() || value.isBlank()) return@mapNotNull null
            // Strip the species name prefix from the feature key if present
            val speciesParts = parts.filter { !it.contains("=") }
            val cleanKey = speciesParts.fold(key) { k, sp -> k.removePrefix("${sp}_") }
            val featureName = titleCase(cleanKey)
            val numericValue = value.toIntOrNull()
            if (numericValue != null) {
                tr("cobbledex-rei-emi-jei.evo.collect_feature", numericValue, featureName)
            } else {
                tr("cobbledex-rei-emi-jei.evo.feature_value", featureName, titleCase(value))
            }
        }
        return results.joinToString(", ").ifBlank { null }
    }

    private fun formatItemName(item: String?): String {
        if (item == null) return "item"
        val str = item.trim()
        if (str.isBlank() || str.contains("@") || (str.contains(".") && str.length > 40)) return "item"
        val name = if (str.contains(":")) str.substringAfter(":") else str
        return name
            .replace("_", " ")
            .split(" ")
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
            .ifBlank { "item" }
    }

    private fun formatMoveName(move: String?): String {
        if (move == null) return "move"
        val str = move.trim()
        if (str.isBlank() || str.contains("@")) return "move"
        val id = if (str.contains(":")) str.substringAfter(":") else str
        val key = "cobblemon.move.${id.lowercase()}"
        val translated = tr(key)
        if (translated != key) return translated
        return id.replace("_", " ")
            .split(" ")
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
            .ifBlank { "move" }
    }

    private fun formatBiome(biome: String): String {
        return formatBiomeName(biome)
    }

    private fun formatStructure(structure: String): String {
        return titleCase(stripNamespace(structure))
    }
}
