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
            val base = titleCase(fromSpecies)
            return if (fromAspects.isEmpty()) base
            else "$base (${formatAspects(fromAspects)})"
        }

    val displayToName: String
        get() {
            val base = titleCase(toSpecies)
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
                    .replace("_", " ")
                    .split(" ")
                    .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                when {
                    variant.contains("item_interact") -> parts.add("Use $itemName")
                    variant == "trade" -> parts.add("Trade holding $itemName")
                    variant.contains("block_click") -> parts.add("Click $itemName block")
                    itemName.isNotBlank() -> parts.add("Use $itemName")
                }
            }

            if (variant == "trade" && requiredContext == null) {
                parts.add("Trade")
            }

            for (req in requirements) {
                parts.add(req.displayText)
            }

            return if (parts.isEmpty()) {
                when {
                    variant.contains("level") -> "Level up"
                    variant.contains("trade") -> "Trade"
                    variant.contains("item") -> "Use item"
                    else -> "Level up"
                }
            } else parts.joinToString(", ")
        }
}

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
                if (min != null && min > 0) "Lv. $min+" else "Level up"
            }
            "friendship" -> {
                val amount = (data["amount"] as? Number)?.toInt() ?: 160
                "Friendship $amount+"
            }
            "time_range" -> {
                val range = cleanValue(data["range"])?.lowercase() ?: "time"
                when (range) {
                    "day" -> "Daytime"
                    "night" -> "Nighttime"
                    "dusk" -> "Dusk"
                    "dawn" -> "Dawn"
                    "twilight" -> "Twilight"
                    "midnight" -> "Midnight"
                    "time", "" -> "Time-based"
                    else -> titleCase(range)
                }
            }
            "held_item" -> {
                val item = formatItemName(cleanValue(data["itemCondition"]))
                "Hold $item"
            }
            "has_move_type", "move_type" -> {
                val type = cleanValue(data["type"])?.let { titleCase(it) } ?: "type"
                "Know $type move"
            }
            "move_set", "has_move" -> {
                val move = formatMoveName(cleanValue(data["move"]))
                "Know $move"
            }
            "biome" -> {
                val biome = cleanValue(data["biomeCondition"])
                val antibiome = cleanValue(data["biomeAnticondition"])
                when {
                    biome != null -> "In ${formatBiome(biome)}"
                    antibiome != null -> "Not in ${formatBiome(antibiome)}"
                    else -> "Biome-specific"
                }
            }
            "structure" -> {
                val struct = cleanValue(data["structureCondition"])
                val antiStruct = cleanValue(data["structureAnticondition"])
                when {
                    struct != null -> "In ${formatStructure(struct)}"
                    antiStruct != null -> "Not in ${formatStructure(antiStruct)}"
                    else -> "Structure-specific"
                }
            }
            "stat_compare" -> {
                val high = cleanValue(data["highStat"])?.let { titleCase(it) } ?: "Stat"
                val low = cleanValue(data["lowStat"])?.let { titleCase(it) } ?: "Stat"
                "$high > $low"
            }
            "stat_equal" -> {
                val s1 = cleanValue(data["statOne"])?.let { titleCase(it) } ?: "Stat"
                val s2 = cleanValue(data["statTwo"])?.let { titleCase(it) } ?: "Stat"
                "$s1 = $s2"
            }
            "pokemon_properties", "properties" -> {
                val target = cleanValue(data["target"]) ?: ""
                parsePropertiesTarget(target)
            }
            "property_range" -> {
                val feature = cleanValue(data["feature"])?.let { titleCase(it) } ?: ""
                val range = cleanValue(data["range"]) ?: ""
                when {
                    feature.isNotBlank() && range.isNotBlank() -> "$feature: $range"
                    feature.isNotBlank() -> feature
                    else -> "Special condition"
                }
            }
            "blocks_traveled" -> {
                val amount = (data["amount"] as? Number)?.toInt()
                if (amount != null) "Walk $amount blocks" else "Walk distance"
            }
            "use_move" -> {
                val move = formatMoveName(cleanValue(data["move"]))
                val amount = (data["amount"] as? Number)?.toInt()
                if (amount != null) "Use $move ${amount}x" else "Use $move"
            }
            "defeat" -> {
                val target = cleanValue(data["target"]) ?: ""
                val amount = (data["amount"] as? Number)?.toInt()
                val targetName = target.split(" ").firstOrNull()?.let { titleCase(it) } ?: target
                if (amount != null) "Defeat ${amount}x $targetName" else "Defeat $targetName"
            }
            "recoil" -> {
                val amount = (data["amount"] as? Number)?.toInt()
                if (amount != null) "$amount recoil damage" else "Recoil damage"
            }
            "damage_taken" -> {
                val amount = (data["amount"] as? Number)?.toInt()
                if (amount != null) "Take $amount+ damage" else "Take damage"
            }
            "battle_critical_hits" -> {
                val amount = (data["amount"] as? Number)?.toInt()
                if (amount != null) "$amount critical hits" else "Critical hits"
            }
            "party_member", "party" -> {
                val target = cleanValue(data["target"])?.split(" ")?.firstOrNull()?.let { titleCase(it) }
                val contains = data["contains"] as? Boolean ?: true
                if (target != null && target.isNotBlank()) {
                    if (contains) "$target in party" else "No $target in party"
                } else "Party condition"
            }
            "moon_phase" -> {
                val phase = cleanValue(data["moonPhase"])?.let { titleCase(it) } ?: "Full Moon"
                phase
            }
            "weather" -> {
                val raining = data["isRaining"] as? Boolean
                if (raining == true) "Raining" else "Clear weather"
            }
            "advancement" -> {
                val adv = cleanValue(data["requiredAdvancement"])
                    ?.substringAfterLast("/")
                    ?.substringAfterLast(":")
                    ?.let { titleCase(it) }
                adv ?: "Advancement"
            }
            "world" -> {
                val id = cleanValue(data["identifier"])?.substringAfterLast(":")
                    ?.let { titleCase(it) }
                "In ${id ?: "dimension"}"
            }
            "attack_defence_ratio" -> {
                val ratio = cleanValue(data["ratio"])?.lowercase()
                when (ratio) {
                    "attack_higher" -> "Attack > Defense"
                    "defence_higher", "defense_higher" -> "Defense > Attack"
                    "equal" -> "Attack = Defense"
                    else -> ratio?.let { titleCase(it) } ?: "Stat ratio"
                }
            }
            "owner_holds_item" -> {
                val item = formatItemName(cleanValue(data["itemCondition"]))
                "Player holds $item"
            }
            "gender" -> {
                val gender = cleanValue(data["gender"])?.lowercase()
                when (gender) {
                    "male" -> "Male"
                    "female" -> "Female"
                    else -> gender?.let { titleCase(it) } ?: "Gender"
                }
            }
            "shiny" -> "Shiny"
            "nature" -> {
                val nature = cleanValue(data["nature"])?.let { titleCase(it) }
                if (nature != null) "$nature nature" else "Specific nature"
            }
            "max_pokemon_level" -> {
                val max = (data["maxLevel"] as? Number)?.toInt()
                if (max != null) "Max Lv. $max" else "Level cap"
            }
            "walking_steps" -> {
                val amount = (data["amount"] as? Number)?.toInt()
                if (amount != null) "Walk $amount steps" else "Walk distance"
            }
            "damage_dealt" -> {
                val amount = (data["amount"] as? Number)?.toInt()
                if (amount != null) "Deal $amount+ damage" else "Deal damage"
            }
            "status" -> {
                val status = cleanValue(data["status"])?.let { titleCase(it) }
                status ?: "Status condition"
            }
            else -> {
                val fallback = titleCase(variant)
                if (fallback.length > 40 || fallback.contains("@") || (fallback.contains(".") && fallback.length > 30))
                    "Special condition"
                else fallback
            }
        }

    private fun parsePropertiesTarget(target: String): String {
        if (target.isBlank()) return "Special condition"
        if (target.contains("@") || (target.contains(".") && target.length > 40)) return "Special condition"
        val lower = target.lowercase().trim()
        return when {
            lower == "male" || lower.contains("gender=male") -> "Male"
            lower == "female" || lower.contains("gender=female") -> "Female"
            lower.contains("gender=") -> {
                val g = lower.substringAfter("gender=").substringBefore(" ").replaceFirstChar { it.uppercase() }
                g.ifBlank { "Gender condition" }
            }
            lower.contains("form=") -> {
                val form = lower.substringAfter("form=").substringBefore(" ").trim()
                    .replace("_", " ").split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                "Form: $form"
            }
            lower.contains("aspect=") -> {
                val aspect = lower.substringAfter("aspect=").substringBefore(" ").trim()
                    .replace("_", " ").split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                "Form: $aspect"
            }
            lower.contains("shiny") -> "Shiny"
            lower.contains("region_bias=") -> {
                val region = lower.substringAfter("region_bias=").substringBefore(" ")
                    .replace("_", " ").split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                "Region: $region"
            }
            lower.contains("type=") -> {
                val type = lower.substringAfter("type=").substringBefore(" ").replaceFirstChar { it.uppercase() }
                "$type type"
            }
            target.length <= 30 -> titleCase(target)
            else -> "Special condition"
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
