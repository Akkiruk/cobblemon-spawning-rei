package com.cobblemonrei

data class EvolutionInfo(
    val id: String,
    val fromSpecies: String,
    val toSpecies: String,
    val variant: String,
    val requirements: List<EvolutionRequirement>,
    val requiredContext: String?,
    val consumeHeldItem: Boolean
) {
    val displayRequirements: String
        get() {
            val parts = mutableListOf<String>()

            if (requiredContext != null) {
                val itemName = requiredContext
                    .removePrefix("cobblemon:")
                    .removePrefix("minecraft:")
                    .replace("_", " ")
                    .replaceFirstChar { it.uppercase() }
                when (variant) {
                    "item_interact" -> parts.add("Use $itemName")
                    "trade" -> parts.add("Trade")
                    "block_click" -> parts.add("Click $itemName")
                }
            }

            if (variant == "trade" && requiredContext == null) {
                parts.add("Trade")
            }

            for (req in requirements) {
                parts.add(req.displayText)
            }

            return if (parts.isEmpty()) "Level up" else parts.joinToString(", ")
        }
}

data class EvolutionRequirement(
    val variant: String,
    val data: Map<String, Any>
) {
    val displayText: String
        get() = when (variant) {
            "level" -> {
                val min = (data["minLevel"] as? Number)?.toInt() ?: 0
                "Lv. $min+"
            }
            "friendship" -> {
                val amount = (data["amount"] as? Number)?.toInt() ?: 160
                "Friendship $amount+"
            }
            "time_range" -> {
                val range = data["range"]?.toString() ?: "any"
                "Time: ${range.replaceFirstChar { it.uppercase() }}"
            }
            "held_item" -> {
                val item = (data["itemCondition"]?.toString() ?: "")
                    .removePrefix("cobblemon:")
                    .removePrefix("minecraft:")
                    .replace("_", " ")
                    .replaceFirstChar { it.uppercase() }
                "Hold $item"
            }
            "has_move_type" -> {
                val type = data["type"]?.toString() ?: ""
                "Know ${type.replaceFirstChar { it.uppercase() }} move"
            }
            "biome" -> {
                val biome = (data["biomeCondition"]?.toString() ?: "")
                    .removePrefix("#cobblemon:")
                    .removePrefix("#minecraft:")
                    .replace("_", " ")
                    .replaceFirstChar { it.uppercase() }
                "In $biome"
            }
            "stat_compare" -> {
                val high = data["highStat"]?.toString() ?: "?"
                val low = data["lowStat"]?.toString() ?: "?"
                "${high.replaceFirstChar { it.uppercase() }} > ${low.replaceFirstChar { it.uppercase() }}"
            }
            "stat_equal" -> {
                val s1 = data["statOne"]?.toString() ?: "?"
                val s2 = data["statTwo"]?.toString() ?: "?"
                "${s1.replaceFirstChar { it.uppercase() }} = ${s2.replaceFirstChar { it.uppercase() }}"
            }
            "properties" -> {
                val target = data["target"]?.toString() ?: ""
                "Form: $target"
            }
            "property_range" -> {
                val feature = data["feature"]?.toString() ?: ""
                "Property: $feature"
            }
            "blocks_traveled" -> "Walk distance"
            "party" -> "Party condition"
            "damage_taken" -> "Take damage"
            "recoil" -> "Recoil damage"
            else -> variant.replace("_", " ").replaceFirstChar { it.uppercase() }
        }
}
