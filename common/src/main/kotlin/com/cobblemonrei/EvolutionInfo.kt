package com.cobblemonrei

data class EvolutionInfo(
    val id: String,
    val fromSpecies: String,
    val fromAspects: Set<String> = emptySet(),
    val toSpecies: String,
    val toAspects: Set<String> = emptySet(),
    val variant: String,
    val requirements: List<EvolutionRequirement>,
    val requiredContext: String?,
    val consumeHeldItem: Boolean
) {
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
        return aspects.joinToString(", ") { titleCase(it) }
    }

    val displayRequirements: String
        get() {
            val parts = mutableListOf<String>()

            if (requiredContext != null && requiredContext.isNotBlank()) {
                val itemName = requiredContext
                    .let { if (it.contains(":")) it.substringAfter(":") else it }
                    .let { if (it.contains("=")) it.substringAfter("=").substringBefore(" ") else it }
                    .replace("_", " ")
                    .split(" ")
                    .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                when {
                    variant.contains("item_interact") -> parts.add(tr("cobblemon-spawning-rei.evo.use_item_named", itemName))
                    variant == "trade" -> parts.add(tr("cobblemon-spawning-rei.evo.trade_with", itemName))
                    variant.contains("block_click") -> parts.add(tr("cobblemon-spawning-rei.evo.click_block", itemName))
                    itemName.isNotBlank() -> parts.add(tr("cobblemon-spawning-rei.evo.use_item_named", itemName))
                }
            }

            if (variant == "trade" && requiredContext == null) {
                parts.add(tr("cobblemon-spawning-rei.evo.trade"))
            }

            for (req in requirements) {
                parts.add(req.displayText)
            }

            return if (parts.isEmpty()) {
                when {
                    variant.contains("level") -> tr("cobblemon-spawning-rei.evo.level_up")
                    variant.contains("trade") -> tr("cobblemon-spawning-rei.evo.trade")
                    variant.contains("item") -> tr("cobblemon-spawning-rei.evo.use_item")
                    else -> tr("cobblemon-spawning-rei.evo.level_up")
                }
            } else parts.joinToString(", ")
        }

    val itemRequirements: List<EvolutionItemInfo>
        get() {
            val items = mutableListOf<EvolutionItemInfo>()
            if (requiredContext != null && requiredContext.isNotBlank()) {
                val rawId = requiredContext.trim()
                if (rawId.contains(":") && !rawId.contains("@") && rawId.length < 60) {
                    when {
                        variant.contains("item_interact") -> items.add(EvolutionItemInfo(rawId, tr("cobblemon-spawning-rei.evo.item.use")))
                        variant.contains("block_click") -> items.add(EvolutionItemInfo(rawId, tr("cobblemon-spawning-rei.evo.item.click")))
                    }
                }
            }
            for (req in requirements) {
                when (req.variant) {
                    "held_item" -> {
                        val id = req.data["itemCondition"]?.toString()
                        if (id != null && id.contains(":") && !id.contains("@") && id.length < 60)
                            items.add(EvolutionItemInfo(id, tr("cobblemon-spawning-rei.evo.item.hold")))
                    }
                    "owner_holds_item" -> {
                        val id = req.data["itemCondition"]?.toString()
                        if (id != null && id.contains(":") && !id.contains("@") && id.length < 60)
                            items.add(EvolutionItemInfo(id, tr("cobblemon-spawning-rei.evo.item.player_holds")))
                    }
                }
            }
            return items
        }

    val textOnlyRequirements: String
        get() {
            val parts = mutableListOf<String>()
            if (variant == "trade") parts.add(tr("cobblemon-spawning-rei.evo.trade"))
            for (req in requirements) {
                if (req.variant == "held_item" || req.variant == "owner_holds_item") continue
                parts.add(req.displayText)
            }
            if (parts.isEmpty() && itemRequirements.isEmpty()) {
                when {
                    variant.contains("level") -> parts.add(tr("cobblemon-spawning-rei.evo.level_up"))
                    variant.contains("trade") -> parts.add(tr("cobblemon-spawning-rei.evo.trade"))
                    variant.contains("item") -> parts.add(tr("cobblemon-spawning-rei.evo.use_item"))
                    else -> parts.add(tr("cobblemon-spawning-rei.evo.level_up"))
                }
            }
            return parts.joinToString(", ")
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
                if (min != null && min > 0) tr("cobblemon-spawning-rei.evo.level_min", min) else tr("cobblemon-spawning-rei.evo.level_up")
            }
            "friendship" -> {
                val amount = (data["amount"] as? Number)?.toInt() ?: 160
                tr("cobblemon-spawning-rei.evo.friendship", amount)
            }
            "time_range" -> {
                val range = cleanValue(data["range"])?.lowercase() ?: "time"
                when (range) {
                    "day" -> tr("cobblemon-spawning-rei.evo.time.daytime")
                    "night" -> tr("cobblemon-spawning-rei.evo.time.nighttime")
                    "dusk" -> tr("cobblemon-spawning-rei.evo.time.dusk")
                    "dawn" -> tr("cobblemon-spawning-rei.evo.time.dawn")
                    "twilight" -> tr("cobblemon-spawning-rei.evo.time.twilight")
                    "midnight" -> tr("cobblemon-spawning-rei.evo.time.midnight")
                    "time", "" -> tr("cobblemon-spawning-rei.evo.time.time_based")
                    else -> titleCase(range)
                }
            }
            "held_item" -> {
                val item = formatItemName(cleanValue(data["itemCondition"]))
                tr("cobblemon-spawning-rei.evo.hold", item)
            }
            "has_move_type", "move_type" -> {
                val type = cleanValue(data["type"])?.let { titleCase(it) } ?: "type"
                tr("cobblemon-spawning-rei.evo.know_type_move", type)
            }
            "move_set", "has_move" -> {
                val move = formatMoveName(cleanValue(data["move"]))
                tr("cobblemon-spawning-rei.evo.know_move", move)
            }
            "biome" -> {
                val biome = cleanValue(data["biomeCondition"])
                val antibiome = cleanValue(data["biomeAnticondition"])
                when {
                    biome != null -> tr("cobblemon-spawning-rei.evo.in_biome", formatBiome(biome))
                    antibiome != null -> tr("cobblemon-spawning-rei.evo.not_in_biome", formatBiome(antibiome))
                    else -> tr("cobblemon-spawning-rei.evo.biome_specific")
                }
            }
            "structure" -> {
                val struct = cleanValue(data["structureCondition"])
                val antiStruct = cleanValue(data["structureAnticondition"])
                when {
                    struct != null -> tr("cobblemon-spawning-rei.evo.in_structure", formatStructure(struct))
                    antiStruct != null -> tr("cobblemon-spawning-rei.evo.not_in_structure", formatStructure(antiStruct))
                    else -> tr("cobblemon-spawning-rei.evo.structure_specific")
                }
            }
            "stat_compare" -> {
                val high = cleanValue(data["highStat"])?.let { titleCase(it) } ?: "Stat"
                val low = cleanValue(data["lowStat"])?.let { titleCase(it) } ?: "Stat"
                tr("cobblemon-spawning-rei.evo.stat_higher", high, low)
            }
            "stat_equal" -> {
                val s1 = cleanValue(data["statOne"])?.let { titleCase(it) } ?: "Stat"
                val s2 = cleanValue(data["statTwo"])?.let { titleCase(it) } ?: "Stat"
                tr("cobblemon-spawning-rei.evo.stat_equal", s1, s2)
            }
            "pokemon_properties", "properties" -> {
                val target = cleanValue(data["target"]) ?: ""
                parsePropertiesTarget(target)
            }
            "property_range" -> {
                val feature = cleanValue(data["feature"])?.let { titleCase(it) } ?: ""
                val range = cleanValue(data["range"]) ?: ""
                when {
                    feature.isNotBlank() && range.isNotBlank() -> tr("cobblemon-spawning-rei.evo.feature_range", feature, range)
                    feature.isNotBlank() -> feature
                    else -> tr("cobblemon-spawning-rei.evo.special_condition")
                }
            }
            "blocks_traveled" -> {
                val amount = (data["amount"] as? Number)?.toInt()
                if (amount != null) tr("cobblemon-spawning-rei.evo.walk_blocks", amount) else tr("cobblemon-spawning-rei.evo.walk_distance")
            }
            "use_move" -> {
                val move = formatMoveName(cleanValue(data["move"]))
                val amount = (data["amount"] as? Number)?.toInt()
                if (amount != null) tr("cobblemon-spawning-rei.evo.use_move_times", move, amount) else tr("cobblemon-spawning-rei.evo.use_move", move)
            }
            "defeat" -> {
                val target = cleanValue(data["target"]) ?: ""
                val amount = (data["amount"] as? Number)?.toInt()
                val targetName = target.split(" ").firstOrNull()?.let { titleCase(it) } ?: target
                if (amount != null) tr("cobblemon-spawning-rei.evo.defeat_count", amount, targetName) else tr("cobblemon-spawning-rei.evo.defeat", targetName)
            }
            "recoil" -> {
                val amount = (data["amount"] as? Number)?.toInt()
                if (amount != null) tr("cobblemon-spawning-rei.evo.recoil_amount", amount) else tr("cobblemon-spawning-rei.evo.recoil")
            }
            "damage_taken" -> {
                val amount = (data["amount"] as? Number)?.toInt()
                if (amount != null) tr("cobblemon-spawning-rei.evo.damage_taken_amount", amount) else tr("cobblemon-spawning-rei.evo.damage_taken")
            }
            "battle_critical_hits" -> {
                val amount = (data["amount"] as? Number)?.toInt()
                if (amount != null) tr("cobblemon-spawning-rei.evo.critical_hits_count", amount) else tr("cobblemon-spawning-rei.evo.critical_hits")
            }
            "party_member", "party" -> {
                val target = cleanValue(data["target"])?.split(" ")?.firstOrNull()?.let { titleCase(it) }
                val contains = data["contains"] as? Boolean ?: true
                if (target != null && target.isNotBlank()) {
                    if (contains) tr("cobblemon-spawning-rei.evo.party_has", target) else tr("cobblemon-spawning-rei.evo.party_no", target)
                } else tr("cobblemon-spawning-rei.evo.party_condition")
            }
            "moon_phase" -> {
                val phase = cleanValue(data["moonPhase"])?.let { titleCase(it) } ?: "Full Moon"
                phase
            }
            "weather" -> {
                val raining = data["isRaining"] as? Boolean
                if (raining == true) tr("cobblemon-spawning-rei.evo.raining") else tr("cobblemon-spawning-rei.evo.clear_weather")
            }
            "advancement" -> {
                val adv = cleanValue(data["requiredAdvancement"])
                    ?.substringAfterLast("/")
                    ?.substringAfterLast(":")
                    ?.let { titleCase(it) }
                adv ?: tr("cobblemon-spawning-rei.evo.advancement")
            }
            "world" -> {
                val id = cleanValue(data["identifier"])?.substringAfterLast(":")
                    ?.let { titleCase(it) }
                tr("cobblemon-spawning-rei.evo.in_dimension", id ?: "dimension")
            }
            "attack_defence_ratio" -> {
                val ratio = cleanValue(data["ratio"])?.lowercase()
                when (ratio) {
                    "attack_higher" -> tr("cobblemon-spawning-rei.evo.attack_gt_defense")
                    "defence_higher", "defense_higher" -> tr("cobblemon-spawning-rei.evo.defense_gt_attack")
                    "equal" -> tr("cobblemon-spawning-rei.evo.attack_eq_defense")
                    else -> ratio?.let { titleCase(it) } ?: tr("cobblemon-spawning-rei.evo.stat_ratio")
                }
            }
            "owner_holds_item" -> {
                val item = formatItemName(cleanValue(data["itemCondition"]))
                tr("cobblemon-spawning-rei.evo.player_holds", item)
            }
            "gender" -> {
                val gender = cleanValue(data["gender"])?.lowercase()
                when (gender) {
                    "male" -> tr("cobblemon-spawning-rei.evo.male")
                    "female" -> tr("cobblemon-spawning-rei.evo.female")
                    else -> gender?.let { titleCase(it) } ?: tr("cobblemon-spawning-rei.evo.gender_condition")
                }
            }
            "shiny" -> tr("cobblemon-spawning-rei.evo.shiny")
            "nature" -> {
                val nature = cleanValue(data["nature"])?.let { titleCase(it) }
                if (nature != null) tr("cobblemon-spawning-rei.evo.nature", nature) else tr("cobblemon-spawning-rei.evo.specific_nature")
            }
            "max_pokemon_level" -> {
                val max = (data["maxLevel"] as? Number)?.toInt()
                if (max != null) tr("cobblemon-spawning-rei.evo.max_level", max) else tr("cobblemon-spawning-rei.evo.level_cap")
            }
            "walking_steps" -> {
                val amount = (data["amount"] as? Number)?.toInt()
                if (amount != null) tr("cobblemon-spawning-rei.evo.walk_steps", amount) else tr("cobblemon-spawning-rei.evo.walk_distance")
            }
            "damage_dealt" -> {
                val amount = (data["amount"] as? Number)?.toInt()
                if (amount != null) tr("cobblemon-spawning-rei.evo.deal_damage_amount", amount) else tr("cobblemon-spawning-rei.evo.deal_damage")
            }
            "status" -> {
                val status = cleanValue(data["status"])?.let { titleCase(it) }
                status ?: tr("cobblemon-spawning-rei.evo.status_condition")
            }
            else -> {
                val fallback = titleCase(variant)
                if (fallback.length > 40 || fallback.contains("@") || (fallback.contains(".") && fallback.length > 30))
                    tr("cobblemon-spawning-rei.evo.special_condition")
                else fallback
            }
        }

    private fun parsePropertiesTarget(target: String): String {
        if (target.isBlank()) return tr("cobblemon-spawning-rei.evo.special_condition")
        if (target.contains("@") || (target.contains(".") && target.length > 40)) return tr("cobblemon-spawning-rei.evo.special_condition")
        val lower = target.lowercase().trim()
        return when {
            lower == "male" || lower.contains("gender=male") -> tr("cobblemon-spawning-rei.evo.male")
            lower == "female" || lower.contains("gender=female") -> tr("cobblemon-spawning-rei.evo.female")
            lower.contains("gender=") -> {
                val g = lower.substringAfter("gender=").substringBefore(" ").replaceFirstChar { it.uppercase() }
                g.ifBlank { tr("cobblemon-spawning-rei.evo.gender_condition") }
            }
            lower.contains("form=") -> {
                val form = lower.substringAfter("form=").substringBefore(" ").trim()
                    .replace("_", " ").split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                tr("cobblemon-spawning-rei.evo.form", form)
            }
            lower.contains("aspect=") -> {
                val aspect = lower.substringAfter("aspect=").substringBefore(" ").trim()
                    .replace("_", " ").split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                tr("cobblemon-spawning-rei.evo.form", aspect)
            }
            lower.contains("shiny") -> tr("cobblemon-spawning-rei.evo.shiny")
            lower.contains("region_bias=") -> {
                val region = lower.substringAfter("region_bias=").substringBefore(" ")
                    .replace("_", " ").split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                tr("cobblemon-spawning-rei.evo.region", region)
            }
            lower.contains("type=") -> {
                val type = lower.substringAfter("type=").substringBefore(" ").replaceFirstChar { it.uppercase() }
                tr("cobblemon-spawning-rei.evo.type", type)
            }
            target.length <= 30 -> titleCase(target)
            else -> tr("cobblemon-spawning-rei.evo.special_condition")
        }
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
        val name = if (str.contains(":")) str.substringAfter(":") else str
        return name
            .replace("_", " ")
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
