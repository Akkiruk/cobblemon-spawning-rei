package com.cobbledex

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
import com.cobblemon.mod.common.pokemon.requirements.*
import net.minecraft.advancements.critereon.ItemPredicate
import net.minecraft.core.Holder
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

        var speciesWithEvoAccess = 0
        var speciesEvoAccessFailed = 0

        for (species in implemented) {
            val baseName = species.name.lowercase()

            val baseEvos = try {
                species.evolutions.also { speciesWithEvoAccess++ }
            } catch (e: Exception) {
                speciesEvoAccessFailed++
                DebugLog.once("evo-access-$baseName") { "Cannot access evolutions for $baseName: ${e.message}" }
                null
            }

            if (baseEvos != null) {
                for (evo in baseEvos) {
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
            }

            val forms = try { species.forms } catch (_: Exception) { emptyList() }
            for (form in forms) {
                val formEvos = try { form.evolutions } catch (_: Exception) { continue }
                if (formEvos.isEmpty()) continue
                val formAspects = form.aspects.toSet()
                val formKey = buildFormKey(baseName, formAspects)

                for (evo in formEvos) {
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

        if (speciesEvoAccessFailed > 0) {
            DebugLog.warn("Evolution access failed for $speciesEvoAccessFailed/$speciesWithEvoAccess species (evolutions may not be available client-side)")
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
        val resultAspects = evo.result.aspects.map { it.lowercase() }.toSet()

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
                requirements.add(parseRequirement(req))
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
            is TradeEvolution -> evo.requiredContext.asString(" ").ifBlank { null }
            is ItemInteractionEvolution -> extractItemIdFromPredicate(evo.requiredContext)
            is BlockClickEvolution -> formatRegistryCondition(evo.requiredContext)
            is ContextEvolution<*, *> -> {
                val context = evo.requiredContext ?: return null
                when (context) {
                    is ResourceLocation -> context.toString()
                    is PokemonProperties -> context.asString(" ").ifBlank { null }
                    is RegistryLikeCondition<*> -> formatRegistryCondition(context)
                    is ItemPredicate -> extractItemIdFromPredicate(context)
                    else -> context.toString().takeIf { !it.contains("@") && it.length < 60 }
                }
            }
            else -> null
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractItemIdFromPredicate(obj: Any?): String? {
        if (obj == null) return null
        if (obj is RegistryLikeCondition<*>) return formatRegistryCondition(obj)
        if (obj !is ItemPredicate) return null
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

    private fun parseRequirement(req: Any): EvolutionRequirement {
        val data = mutableMapOf<String, Any>()

        val variant: String = when (req) {
            is LevelRequirement -> {
                data["minLevel"] = req.minLevel
                "level"
            }
            is FriendshipRequirement -> {
                data["amount"] = req.amount
                "friendship"
            }
            is TimeRangeRequirement -> {
                resolveTimeRange(req.range)?.let { data["range"] = it }
                "time_range"
            }
            is HeldItemRequirement -> {
                extractItemIdFromPredicate(req.itemCondition)?.let { data["itemCondition"] = it }
                "held_item"
            }
            is OwnerHoldsItemRequirement -> {
                extractItemIdFromPredicate(req.itemCondition)?.let { data["itemCondition"] = it }
                "owner_holds_item"
            }
            is MoveTypeRequirement -> {
                data["type"] = req.type.name
                "move_type"
            }
            is MoveSetRequirement -> {
                data["move"] = req.move.name
                "has_move"
            }
            is BiomeRequirement -> {
                formatRegistryCondition(req.biomeCondition)?.let { data["biomeCondition"] = it }
                formatRegistryCondition(req.biomeAnticondition)?.let { data["biomeAnticondition"] = it }
                "biome"
            }
            is StructureRequirement -> {
                formatRegistryCondition(req.structureCondition)?.let { data["structureCondition"] = it }
                formatRegistryCondition(req.structureAnticondition)?.let { data["structureAnticondition"] = it }
                "structure"
            }
            is StatCompareRequirement -> {
                data["highStat"] = req.highStat
                data["lowStat"] = req.lowStat
                "stat_compare"
            }
            is StatEqualRequirement -> {
                data["statOne"] = req.statOne
                data["statTwo"] = req.statTwo
                "stat_equal"
            }
            is PokemonPropertiesRequirement -> {
                req.target.asString(" ").takeIf { it.isNotBlank() }?.let { data["target"] = it }
                "pokemon_properties"
            }
            is PropertyRangeRequirement -> {
                data["range"] = req.range.toString()
                data["feature"] = req.feature
                "property_range"
            }
            is BlocksTraveledRequirement -> {
                data["amount"] = req.amount
                "blocks_traveled"
            }
            is UseMoveRequirement -> {
                data["move"] = req.move.name
                data["amount"] = req.amount
                "use_move"
            }
            is DefeatRequirement -> {
                req.target.asString(" ").takeIf { it.isNotBlank() }?.let { data["target"] = it }
                data["amount"] = req.amount
                "defeat"
            }
            is RecoilRequirement -> {
                data["amount"] = req.amount
                "recoil"
            }
            is DamageTakenRequirement -> {
                data["amount"] = req.amount
                "damage_taken"
            }
            is BattleCriticalHitsRequirement -> {
                data["amount"] = req.amount
                "battle_critical_hits"
            }
            is PartyMemberRequirement -> {
                req.target.asString(" ").takeIf { it.isNotBlank() }?.let { data["target"] = it }
                data["contains"] = req.contains
                "party_member"
            }
            is MoonPhaseRequirement -> {
                data["moonPhase"] = req.moonPhase.name.lowercase()
                "moon_phase"
            }
            is WeatherRequirement -> {
                req.isRaining?.let { data["isRaining"] = it }
                "weather"
            }
            is AdvancementRequirement -> {
                data["requiredAdvancement"] = req.requiredAdvancement.toString()
                "advancement"
            }
            is WorldRequirement -> {
                data["identifier"] = req.identifier.toString()
                "world"
            }
            is AttackDefenceRatioRequirement -> {
                data["ratio"] = req.ratio.name.lowercase()
                "attack_defence_ratio"
            }
            is AnyRequirement -> {
                val first = req.possibilities.firstOrNull()
                if (first != null) return parseRequirement(first)
                "any"
            }
            else -> {
                req.javaClass.simpleName
                    .replace("Requirement", "")
                    .replace(Regex("([A-Z])"), "_$1")
                    .lowercase()
                    .trimStart('_')
                    .ifEmpty { "unknown" }
            }
        }

        return EvolutionRequirement(variant, data)
    }

    private fun formatRegistryCondition(condition: RegistryLikeCondition<*>?): String? {
        if (condition == null) return null
        return when (condition) {
            is RegistryLikeIdentifierCondition<*> -> condition.identifier.toString()
            is RegistryLikeTagCondition<*> -> "#${condition.tag.location()}"
            else -> null
        }
    }

    private fun resolveTimeRange(range: Any?): String? {
        if (range == null) return null
        if (range.javaClass.isEnum) return (range as Enum<*>).name.lowercase()
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
        val evYield: Map<String, Int>? = null,
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
        val baseFriendship: Int? = null,
        val levelUpMoves: List<LevelUpMove>? = null,
        val eggMoves: List<String>? = null,
        val tutorMoves: List<String>? = null,
        val shoulderMountable: Boolean = false
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

                val evYield = try {
                    val yieldMap = mutableMapOf<String, Int>()
                    for (stat in Stats.PERMANENT) {
                        val value = form.evYield[stat] ?: 0
                        if (value > 0) yieldMap[stat.showdownId] = value
                    }
                    yieldMap.ifEmpty { null }
                } catch (_: Exception) { null }

                val levelUpMoves = try {
                    val moves = form.moves.levelUpMoves
                    val grouped = mutableMapOf<Int, MutableList<String>>()
                    for ((level, moveList) in moves) {
                        for (move in moveList) {
                            grouped.getOrPut(level) { mutableListOf() }.add(titleCase(move.name))
                        }
                    }
                    grouped.entries.sortedBy { it.key }
                        .map { LevelUpMove(it.key, it.value) }
                        .ifEmpty { null }
                } catch (_: Exception) { null }

                val eggMoves = try {
                    form.moves.eggMoves.map { titleCase(it.name) }.ifEmpty { null }
                } catch (_: Exception) { null }

                val tutorMoves = try {
                    form.moves.tutorMoves.map { titleCase(it.name) }.ifEmpty { null }
                } catch (_: Exception) { null }

                val shoulderMount = try { species.shoulderMountable } catch (_: Exception) { false }

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
                    evYield = evYield,
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
                    baseFriendship = friendship,
                    levelUpMoves = levelUpMoves,
                    eggMoves = eggMoves,
                    tutorMoves = tutorMoves,
                    shoulderMountable = shoulderMount
                )
            } catch (e: Exception) {
                DebugLog.once("species-info-${species.name}") { "Failed to load species info for ${species.name}: ${e.message}" }
            }
        }

        // Load mega forms as separate entries
        var megaCount = 0
        for (species in implemented) {
            for (megaForm in species.forms) {
                if (!megaForm.labels.contains("mega")) continue
                try {
                    val megaKey = "${species.name.lowercase()}-${megaForm.name.lowercase()}"

                    val megaStats = try {
                        val statMap = mutableMapOf<String, Int>()
                        for (stat in Stats.PERMANENT) {
                            val value = megaForm.baseStats[stat] ?: species.baseStats[stat] ?: 0
                            statMap[stat.showdownId] = value
                        }
                        statMap.ifEmpty { null }
                    } catch (_: Exception) { null }

                    val megaBst = megaStats?.values?.sum()

                    val megaAbilities = try {
                        val common = mutableListOf<String>()
                        var hidden: String? = null
                        for (ability in megaForm.abilities) {
                            val abilityName = titleCase(ability.template.name)
                            if (ability is HiddenAbility) hidden = abilityName
                            else common.add(abilityName)
                        }
                        Pair(common.ifEmpty { null }, hidden)
                    } catch (_: Exception) { Pair(null, null) }

                    val megaPrimary = try { megaForm.primaryType?.name?.lowercase() ?: species.primaryType.name.lowercase() } catch (_: Exception) { species.primaryType.name.lowercase() }
                    val megaSecondary = try { megaForm.secondaryType?.name?.lowercase() ?: species.secondaryType?.name?.lowercase() } catch (_: Exception) { null }

                    result[megaKey] = SpeciesBasicInfo(
                        name = megaKey,
                        nationalDexNumber = species.nationalPokedexNumber,
                        primaryType = megaPrimary,
                        secondaryType = megaSecondary,
                        catchRate = species.catchRate,
                        weight = species.weight,
                        height = species.height,
                        baseStats = megaStats,
                        baseStatTotal = megaBst,
                        abilities = megaAbilities.first,
                        hiddenAbility = megaAbilities.second,
                        labels = megaForm.labels.ifEmpty { null }
                    )
                    megaCount++
                } catch (e: Exception) {
                    DebugLog.once("mega-${species.name}-${megaForm.name}") { "Failed to load mega form: ${e.message}" }
                }
            }
        }

        DebugLog.info("Loaded ${result.size} species from runtime API ($dropSpeciesCount with drops, $megaCount mega forms)")
        return result
    }
}
