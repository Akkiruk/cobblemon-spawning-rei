package com.cobblemonrei

import com.cobblemon.mod.common.api.conditional.RegistryLikeCondition
import com.cobblemon.mod.common.api.conditional.RegistryLikeIdentifierCondition
import com.cobblemon.mod.common.api.conditional.RegistryLikeTagCondition
import com.cobblemon.mod.common.api.drop.ItemDropEntry
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.pokemon.evolution.ContextEvolution
import com.cobblemon.mod.common.api.pokemon.evolution.Evolution
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.pokemon.abilities.HiddenAbility
import com.cobblemon.mod.common.pokemon.evolution.variants.BlockClickEvolution
import com.cobblemon.mod.common.pokemon.evolution.variants.ItemInteractionEvolution
import com.cobblemon.mod.common.pokemon.evolution.variants.TradeEvolution
import net.minecraft.advancements.critereon.ItemPredicate
import net.minecraft.core.Holder
import net.minecraft.core.HolderSet
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.Item

object EvolutionDataLoader {

    fun loadFromRuntime(): Map<String, List<EvolutionInfo>> {
        val implemented = try {
            PokemonSpecies.implemented.toList()
        } catch (e: Exception) {
            DebugLog.warnOnce("evo-species-load") { "Failed to access PokemonSpecies.implemented: ${e.message}" }
            return emptyMap()
        }
        if (implemented.isEmpty()) return emptyMap()

        val result = mutableMapOf<String, MutableList<EvolutionInfo>>()
        var baseEvoCount = 0
        var formEvoCount = 0

        for (species in implemented) {
            val baseName = species.name.lowercase()

            // Base species evolutions
            for (evo in species.evolutions) {
                try {
                    val info = parseEvolution(baseName, null, evo)
                    if (info != null) {
                        result.getOrPut(baseName) { mutableListOf() }.add(info)
                        baseEvoCount++
                    }
                } catch (e: Exception) {
                    DebugLog.once("evo-parse-$baseName") { "Failed to parse base evolution: ${e.message}" }
                }
            }

            // Form-specific evolutions — store under both composite key and base name
            for (form in species.forms) {
                if (form.evolutions.isEmpty()) continue
                val formAspects = form.aspects.toSet()
                val formKey = buildFormKey(baseName, formAspects)

                for (evo in form.evolutions) {
                    try {
                        val info = parseEvolution(baseName, formAspects, evo)
                        if (info != null) {
                            result.getOrPut(formKey) { mutableListOf() }.add(info)
                            if (formKey != baseName) {
                                result.getOrPut(baseName) { mutableListOf() }.add(info)
                            }
                            formEvoCount++
                        }
                    } catch (e: Exception) {
                        DebugLog.once("evo-parse-$formKey") { "Failed to parse form evolution: ${e.message}" }
                    }
                }
            }
        }

        DebugLog.info("Parsed $baseEvoCount base + $formEvoCount form evolutions")
        return result
    }

    private fun buildFormKey(baseName: String, aspects: Set<String>): String {
        return if (aspects.isEmpty()) baseName
        else "$baseName ${aspects.sorted().joinToString(" ")}"
    }

    private fun parseEvolution(fromSpecies: String, fromAspects: Set<String>?, evo: Evolution): EvolutionInfo? {
        val id = evo.id
        val resultSpecies = evo.result.species?.lowercase() ?: return null
        val resultAspects = parseResultAspects(evo.result.aspects)

        val variant = evo.javaClass.simpleName
            .replace("Evolution", "")
            .replace(Regex("([A-Z])"), "_$1")
            .lowercase()
            .trimStart('_')
            .ifEmpty { "level_up" }

        val requiredContext = extractRequiredContext(evo)

        val requirements = mutableListOf<EvolutionRequirement>()
        for (req in evo.requirements) {
            try {
                val parsed = parseRequirement(req)
                requirements.add(parsed)
            } catch (e: Exception) {
                val reqVariant = req.javaClass.simpleName.replace("Requirement", "").lowercase()
                requirements.add(EvolutionRequirement(reqVariant, emptyMap()))
            }
        }

        return EvolutionInfo(
            id = id,
            fromSpecies = fromSpecies,
            fromAspects = fromAspects ?: emptySet(),
            toSpecies = resultSpecies,
            toAspects = resultAspects,
            variant = variant,
            requirements = requirements,
            requiredContext = requiredContext,
            consumeHeldItem = evo.consumeHeldItem
        )
    }

    private fun extractRequiredContext(evo: Evolution): String? {
        return when (evo) {
            is TradeEvolution -> {
                val props = evo.requiredContext
                val str = props.asString(" ")
                str.ifBlank { null }
            }
            is ItemInteractionEvolution -> {
                extractItemIdFromAny(evo.requiredContext)
            }
            is BlockClickEvolution -> {
                formatRegistryCondition(evo.requiredContext)
            }
            is ContextEvolution<*, *> -> {
                try {
                    val field = findField(evo.javaClass, "requiredContext")
                    field?.isAccessible = true
                    val value = field?.get(evo) ?: return null
                    when {
                        value is ResourceLocation -> value.toString()
                        value is PokemonProperties -> value.asString(" ").ifBlank { null }
                        value is RegistryLikeCondition<*> -> formatRegistryCondition(value)
                        else -> extractItemIdFromAny(value)
                            ?: value.toString().takeIf { !it.contains("@") && it.length < 60 }
                    }
                } catch (_: Exception) { null }
            }
            else -> null
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractItemIdFromAny(obj: Any?): String? {
        if (obj == null) return null
        // Typed access to vanilla ItemPredicate (Loom-remapped, no reflection strings)
        if (obj is ItemPredicate) {
            val holderSet = obj.items().orElse(null) ?: return null
            val first = holderSet.stream().findFirst().orElse(null) ?: return null
            if (first is Holder<*>) {
                val key = first.unwrapKey().orElse(null)
                if (key != null) return key.location().toString()
                val value = first.value()
                if (value is Item) return BuiltInRegistries.ITEM.getKey(value).toString()
            }
            return null
        }
        // RegistryLikeCondition wrapper (Cobblemon class, not obfuscated)
        if (obj is RegistryLikeCondition<*>) return formatRegistryCondition(obj)
        // Nested 'item' field (legacy Cobblemon fields, not obfuscated)
        val item = extractField(obj, "item") ?: extractField(obj, "items")
        if (item is RegistryLikeCondition<*>) return formatRegistryCondition(item)
        if (item != null) {
            val nested = extractItemIdFromAny(item)
            if (nested != null) return nested
        }
        return null
    }

    private fun parseResultAspects(aspects: Set<String>): Set<String> {
        return aspects.map { it.lowercase() }.toSet()
    }

    private fun parseRequirement(req: Any): EvolutionRequirement {
        val className = req.javaClass.simpleName
        val variant = className.replace("Requirement", "")
            .replace(Regex("([A-Z])"), "_$1")
            .lowercase()
            .trimStart('_')

        val data = mutableMapOf<String, Any>()

        when {
            className == "LevelRequirement" -> {
                extractField(req, "minLevel")?.let { data["minLevel"] = it }
            }
            className == "FriendshipRequirement" -> {
                extractField(req, "amount")?.let { data["amount"] = it }
            }
            className == "TimeRangeRequirement" -> {
                val range = extractField(req, "range")
                resolveTimeRange(range)?.let { data["range"] = it }
            }
            className.contains("HeldItem") || className.contains("OwnerHoldsItem") -> {
                val id = extractItemFromRequirement(req)
                if (id != null) data["itemCondition"] = id
            }
            className == "MoveTypeRequirement" -> {
                extractField(req, "type")?.let { data["type"] = extractReadableValue(it) ?: "unknown" }
            }
            className == "MoveSetRequirement" -> {
                extractField(req, "move")?.let { data["move"] = extractReadableValue(it) ?: "unknown" }
            }
            className.contains("Biome") -> {
                val cond = extractField(req, "biomeCondition")
                if (cond is RegistryLikeCondition<*>) data["biomeCondition"] = formatRegistryCondition(cond) ?: cond.toString()
                else if (cond != null) data["biomeCondition"] = cond.toString()
                val anti = extractField(req, "biomeAnticondition")
                if (anti is RegistryLikeCondition<*>) data["biomeAnticondition"] = formatRegistryCondition(anti) ?: anti.toString()
                else if (anti != null) data["biomeAnticondition"] = anti.toString()
            }
            className.contains("Structure") -> {
                val cond = extractField(req, "structureCondition")
                if (cond is RegistryLikeCondition<*>) data["structureCondition"] = formatRegistryCondition(cond) ?: cond.toString()
                else if (cond != null) data["structureCondition"] = cond.toString()
                val anti = extractField(req, "structureAnticondition")
                if (anti is RegistryLikeCondition<*>) data["structureAnticondition"] = formatRegistryCondition(anti) ?: anti.toString()
                else if (anti != null) data["structureAnticondition"] = anti.toString()
            }
            className.contains("StatCompare") -> {
                extractField(req, "highStat")?.let { data["highStat"] = extractReadableValue(it) ?: "stat" }
                extractField(req, "lowStat")?.let { data["lowStat"] = extractReadableValue(it) ?: "stat" }
            }
            className.contains("StatEqual") -> {
                extractField(req, "statOne")?.let { data["statOne"] = extractReadableValue(it) ?: "stat" }
                extractField(req, "statTwo")?.let { data["statTwo"] = extractReadableValue(it) ?: "stat" }
            }
            className.contains("PokemonProperties") -> {
                val target = extractField(req, "target")
                if (target is PokemonProperties) {
                    val str = target.asString(" ")
                    if (str.isNotBlank()) data["target"] = str
                }
            }
            className.contains("PropertyRange") -> {
                extractField(req, "range")?.let { data["range"] = it.toString() }
                extractField(req, "feature")?.let { data["feature"] = it.toString() }
            }
            className.contains("BlocksTraveled") -> {
                extractField(req, "amount")?.let { data["amount"] = it }
            }
            className.contains("UseMove") -> {
                extractField(req, "move")?.let { data["move"] = it.toString() }
                extractField(req, "amount")?.let { data["amount"] = it }
            }
            className.contains("Defeat") -> {
                val target = extractField(req, "target")
                if (target is PokemonProperties) {
                    val str = target.asString(" ")
                    if (str.isNotBlank()) data["target"] = str
                }
                extractField(req, "amount")?.let { data["amount"] = it }
            }
            className.contains("Recoil") || className.contains("DamageTaken") ||
                className.contains("BattleCriticalHits") || className.contains("WalkingSteps") ||
                className.contains("DamageDealt") -> {
                extractField(req, "amount")?.let { data["amount"] = it }
            }
            className.contains("PartyMember") -> {
                val target = extractField(req, "target")
                if (target is PokemonProperties) {
                    val str = target.asString(" ")
                    if (str.isNotBlank()) data["target"] = str
                }
                extractField(req, "contains")?.let { data["contains"] = it }
            }
            className.contains("MoonPhase") -> {
                extractField(req, "moonPhase")?.let { data["moonPhase"] = extractReadableValue(it) ?: "moon" }
            }
            className.contains("Weather") -> {
                extractField(req, "isRaining")?.let { data["isRaining"] = it }
            }
            className.contains("PlayerHasAdvancement") -> {
                extractField(req, "requiredAdvancement")?.let { data["requiredAdvancement"] = it.toString() }
            }
            className.contains("World") -> {
                extractField(req, "identifier")?.let { data["identifier"] = it.toString() }
            }
            className.contains("AttackDefenceRatio") -> {
                extractField(req, "ratio")?.let { data["ratio"] = extractReadableValue(it) ?: "equal" }
            }
            className.contains("Gender") -> {
                extractField(req, "gender")?.let { data["gender"] = extractReadableValue(it) ?: "unknown" }
            }
            className.contains("Nature") -> {
                extractField(req, "nature")?.let { data["nature"] = extractReadableValue(it) ?: "unknown" }
            }
            className.contains("MaxPokemonLevel") -> {
                extractField(req, "maxLevel")?.let { data["maxLevel"] = it }
            }
            else -> {
                extractField(req, "amount")?.let { data["amount"] = it }
            }
        }

        if (data.isEmpty()) {
            DebugLog.warnOnce("empty-req-$variant-$className") {
                "No data extracted for $variant requirement ($className) — Cobblemon API may have changed"
            }
        }

        return EvolutionRequirement(variant, data)
    }

    private fun formatRegistryCondition(condition: RegistryLikeCondition<*>?): String? {
        if (condition == null) return null
        return when (condition) {
            is RegistryLikeIdentifierCondition<*> -> condition.identifier.toString()
            is RegistryLikeTagCondition<*> -> "#${condition.tag.location()}"
            else -> {
                val id = extractField(condition, "identifier")
                if (id is ResourceLocation) id.toString() else null
            }
        }
    }

    private fun findField(clazz: Class<*>, fieldName: String): java.lang.reflect.Field? {
        var c: Class<*>? = clazz
        while (c != null && c != Any::class.java) {
            try {
                return c.getDeclaredField(fieldName)
            } catch (_: NoSuchFieldException) {
                c = c.superclass
            }
        }
        return null
    }

    private fun resolveTimeRange(range: Any?): String? {
        if (range == null) return null
        if (range is IntRange) {
            return when {
                range.isEmpty() -> null
                range.first <= 0 && range.last >= 12999 -> "day"
                range.first >= 12000 -> "night"
                else -> range.toString()
            }
        }
        val str = range.toString().uppercase()
        return when {
            str.contains("DAY") && !str.contains("NIGHT") -> "day"
            str.contains("NIGHT") -> "night"
            str.contains("DUSK") -> "dusk"
            str.contains("DAWN") -> "dawn"
            str.contains("@") -> null
            else -> range.toString().lowercase()
        }
    }

    private fun extractField(obj: Any, fieldName: String): Any? {
        var clazz: Class<*>? = obj.javaClass
        while (clazz != null && clazz != Any::class.java) {
            try {
                val field = clazz.getDeclaredField(fieldName)
                field.isAccessible = true
                return field.get(obj)
            } catch (_: NoSuchFieldException) {
                clazz = clazz.superclass
            } catch (e: Exception) {
                DebugLog.once("extract-${obj.javaClass.simpleName}-$fieldName") { "Field access failed on ${clazz?.simpleName}: ${e.message}" }
                return null
            }
        }
        // Fallback: try Kotlin-style getter method
        return try {
            val getter = obj.javaClass.getMethod("get${fieldName.replaceFirstChar { it.uppercase() }}")
            getter.invoke(obj)
        } catch (_: Exception) {
            try {
                obj.javaClass.getMethod(fieldName).invoke(obj)
            } catch (_: Exception) { null }
        }
    }

    private fun extractItemFromRequirement(req: Any): String? {
        // Try 'itemCondition' field first (may be ItemPredicate or wrapper)
        val itemCondition = extractField(req, "itemCondition")
        if (itemCondition != null) {
            val fromCondition = extractItemIdFromAny(itemCondition)
            if (fromCondition != null) return fromCondition
        }
        // Try direct 'item' field
        val itemField = extractField(req, "item")
        if (itemField != null) {
            val fromItem = extractItemIdFromAny(itemField)
            if (fromItem != null) return fromItem
        }
        // Scan all fields for anything that looks like an ItemPredicate or RegistryLikeCondition
        for (field in req.javaClass.declaredFields) {
            try {
                field.isAccessible = true
                val value = field.get(req) ?: continue
                if (value is RegistryLikeCondition<*>) {
                    val id = formatRegistryCondition(value)
                    if (id != null) return id
                }
                val predResult = extractItemIdFromAny(value)
                if (predResult != null) return predResult
            } catch (_: Exception) {}
        }
        return null
    }

    private fun extractReadableValue(obj: Any?): String? {
        if (obj == null) return null
        if (obj is String) return obj.takeIf { !it.contains("@") }
        if (obj is Number) return obj.toString()
        if (obj is Boolean) return obj.toString()
        if (obj.javaClass.isEnum) return (obj as Enum<*>).name.lowercase()
        if (obj is ResourceLocation) return obj.toString()
        if (obj is PokemonProperties) return obj.asString(" ").ifBlank { null }
        if (obj is RegistryLikeCondition<*>) return formatRegistryCondition(obj)

        val name = extractField(obj, "name")
        if (name is String && !name.contains("@")) return name
        val path = extractField(obj, "path")
        if (path is String && !path.contains("@")) return path

        val str = obj.toString()
        return if (str.contains("@") || str.length > 50) {
            obj.javaClass.simpleName.lowercase().replace("_", " ")
        } else str
    }



    data class SpeciesBasicInfo(
        val name: String,
        val nationalDexNumber: Int,
        val primaryType: String,
        val secondaryType: String?,
        val catchRate: Int,
        val weight: Float,
        val height: Float,
        val baseStats: Map<String, Int>? = null,
        val baseStatTotal: Int? = null,
        val abilities: List<String>? = null,
        val hiddenAbility: String? = null,
        val eggGroups: List<String>? = null,
        val labels: Set<String>? = null,
        val preEvolution: String? = null,
        val description: String? = null,
        val drops: List<DropEntryInfo>? = null,
        val maleRatio: Float? = null,
        val eggCycles: Int? = null,
        val experienceGroup: String? = null,
        val baseExperienceYield: Int? = null,
        val baseFriendship: Int? = null
    )

    fun loadSpeciesBasicInfoFromRuntime(): Map<String, SpeciesBasicInfo> {
        val implemented = try {
            PokemonSpecies.implemented.toList()
        } catch (e: Exception) {
            DebugLog.warnOnce("species-info-load") { "Failed to access PokemonSpecies.implemented: ${e.message}" }
            return emptyMap()
        }
        if (implemented.isEmpty()) return emptyMap()

        val result = mutableMapOf<String, SpeciesBasicInfo>()
        var dropSpeciesCount = 0

        for (species in implemented) {
            try {
                val name = species.name.lowercase()
                val form = species.standardForm

                val stats = try {
                    val statMap = mutableMapOf<String, Int>()
                    for (stat in Stats.PERMANENT) {
                        val value = form.baseStats[stat] ?: species.baseStats[stat] ?: 0
                        statMap[stat.showdownId] = value
                    }
                    statMap.ifEmpty { null }
                } catch (_: Exception) { null }

                val bst = stats?.values?.sum()

                val abilityNames = try {
                    val common = mutableListOf<String>()
                    var hidden: String? = null
                    for (ability in form.abilities) {
                        val abilityName = titleCase(ability.template.name)
                        if (ability is HiddenAbility) {
                            hidden = abilityName
                        } else {
                            common.add(abilityName)
                        }
                    }
                    Pair(common.ifEmpty { null }, hidden)
                } catch (_: Exception) { Pair(null, null) }

                val eggGroups = try {
                    val groups = form.eggGroups.ifEmpty { species.eggGroups }
                    groups.map { it.showdownID }.ifEmpty { null }
                } catch (_: Exception) { null }

                val labels = try {
                    species.labels.ifEmpty { null }
                } catch (_: Exception) { null }

                val preEvolution = try {
                    species.preEvolution?.species?.name?.lowercase()
                } catch (_: Exception) { null }

                val description = try {
                    species.pokedex.firstOrNull()
                } catch (_: Exception) { null }

                val drops = try {
                    val entries = form.drops.entries
                        .filterIsInstance<ItemDropEntry>()
                        .map { entry ->
                            DropEntryInfo(
                                itemId = entry.item.toString(),
                                percentage = entry.percentage,
                                quantity = entry.quantity,
                                quantityRange = entry.quantityRange?.let { "${it.first}-${it.last}" }
                            )
                        }
                    if (entries.isNotEmpty()) dropSpeciesCount++
                    entries.ifEmpty { null }
                } catch (_: Exception) { null }

                val maleRatio = try { species.maleRatio } catch (_: Exception) { null }
                val eggCycles = try { species.eggCycles } catch (_: Exception) { null }
                val expGroup = try { species.experienceGroup.name } catch (_: Exception) { null }
                val baseExpYield = try { species.baseExperienceYield } catch (_: Exception) { null }
                val friendship = try { species.baseFriendship } catch (_: Exception) { null }

                result[name] = SpeciesBasicInfo(
                    name = name,
                    nationalDexNumber = species.nationalPokedexNumber,
                    primaryType = species.primaryType.name.lowercase(),
                    secondaryType = species.secondaryType?.name?.lowercase(),
                    catchRate = species.catchRate,
                    weight = species.weight,
                    height = species.height,
                    baseStats = stats,
                    baseStatTotal = bst,
                    abilities = abilityNames.first,
                    hiddenAbility = abilityNames.second,
                    eggGroups = eggGroups,
                    labels = labels,
                    preEvolution = preEvolution,
                    description = description,
                    drops = drops,
                    maleRatio = maleRatio,
                    eggCycles = eggCycles,
                    experienceGroup = expGroup,
                    baseExperienceYield = baseExpYield,
                    baseFriendship = friendship
                )
            } catch (e: Exception) {
                DebugLog.once("species-info-${species.name}") { "Failed to load species info for ${species.name}: ${e.message}" }
            }
        }

        DebugLog.info("Loaded ${result.size} species from runtime API ($dropSpeciesCount with drops)")
        return result
    }
}
