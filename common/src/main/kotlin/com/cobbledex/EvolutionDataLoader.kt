package com.cobbledex

import com.cobblemon.mod.common.api.conditional.RegistryLikeCondition
import com.cobblemon.mod.common.api.conditional.RegistryLikeIdentifierCondition
import com.cobblemon.mod.common.api.conditional.RegistryLikeTagCondition
import com.cobblemon.mod.common.api.drop.ItemDropEntry
import com.cobblemon.mod.common.api.moves.MoveTemplate
import com.cobblemon.mod.common.api.moves.Moves
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
                // Cobblemon registers every species' own baseline look as a form
                // in its own right (typically named "Normal", with no aspect tag
                // of its own) alongside genuinely distinct forms like Gmax/Alolan.
                // Without this check that "Normal" form was being treated as a
                // real alternate form - creating a bogus "<species>_normal" entry
                // for essentially every species with any registered form, and
                // double-counting the base species' own evolutions (added once
                // from species.evolutions above, then again here).
                if (formAspects.isEmpty()) continue
                val formKey = buildFormEntryKey(baseName, form, species)

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



    private fun toMoveDetail(template: MoveTemplate): MoveDetail {
        return MoveDetail(
            name = template.name,
            type = template.elementalType.name.lowercase(),
            category = template.damageCategory.name,
            power = template.power.toInt(),
            accuracy = template.accuracy.toInt(),
            pp = template.pp
        )
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
        val eggMoves: List<MoveDetail>? = null,
        val tutorMoves: List<MoveDetail>? = null,
        val tmMoves: List<MoveDetail>? = null,
        val shoulderMountable: Boolean = false,
        val formName: String? = null,
        val baseSpeciesName: String? = null,
        val formAspects: Set<String> = emptySet(),
        val source: String? = null
    ) {
        val isForm: Boolean get() = baseSpeciesName != null
    }

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
                val form = try { species.standardForm } catch (_: Exception) { null }
                if (form == null) {
                    DebugLog.once("species-no-form-$name") { "Species '$name' has no standard form, using species-level data" }
                }

                val stats = try {
                    val statMap = mutableMapOf<String, Int>()
                    for (stat in Stats.PERMANENT) {
                        val value = form?.baseStats?.get(stat) ?: species.baseStats[stat] ?: 0
                        statMap[stat.showdownId] = value
                    }
                    statMap.ifEmpty { null }
                } catch (_: Exception) { null }

                val bst = stats?.values?.sum()

                val abilityNames = try {
                    val common = mutableListOf<String>()
                    var hidden: String? = null
                    val abilities = form?.abilities ?: emptyList()
                    for (ability in abilities) {
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
                    val groups = (form?.eggGroups ?: emptySet()).ifEmpty { species.eggGroups }
                    groups.map { it.showdownID }.ifEmpty { null }
                } catch (_: Exception) { null }

                val labels = try {
                    species.labels.ifEmpty { null }
                } catch (_: Exception) { null }

                val preEvolution = try {
                    species.preEvolution?.species?.name?.lowercase()
                } catch (_: Exception) { null }

                val description = try {
                    val key = species.pokedex.firstOrNull()
                    if (key != null && key.isNotBlank()) key else null
                } catch (_: Exception) { null }

                val drops = try {
                    val entries = (form?.drops?.entries ?: emptyList())
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
                        val value = form?.evYield?.get(stat) ?: 0
                        if (value > 0) yieldMap[stat.showdownId] = value
                    }
                    yieldMap.ifEmpty { null }
                } catch (_: Exception) { null }

                val levelUpMoves = try {
                    val moves = form?.moves?.levelUpMoves ?: emptyMap()
                    val grouped = mutableMapOf<Int, MutableList<MoveDetail>>()
                    for ((level, moveList) in moves) {
                        for (move in moveList) {
                            grouped.getOrPut(level) { mutableListOf() }.add(toMoveDetail(move))
                        }
                    }
                    grouped.entries.sortedBy { it.key }
                        .map { LevelUpMove(it.key, it.value) }
                        .ifEmpty { null }
                } catch (_: Exception) { null }

                val eggMoves = try {
                    form?.moves?.eggMoves?.map { toMoveDetail(it) }?.ifEmpty { null }
                } catch (_: Exception) { null }

                val tutorMoves = try {
                    form?.moves?.tutorMoves?.map { toMoveDetail(it) }?.ifEmpty { null }
                } catch (_: Exception) { null }

                val tmMoves = try {
                    form?.moves?.tmMoves?.map { toMoveDetail(it) }?.ifEmpty { null }
                } catch (_: Exception) { null }

                val shoulderMount = try { species.shoulderMountable } catch (_: Exception) { false }
                val source = try { species.resourceIdentifier?.namespace } catch (_: Exception) { null }

                result[name] = SpeciesBasicInfo(
                    name = name,
                    nationalDexNumber = try { species.nationalPokedexNumber } catch (_: Exception) { 0 },
                    primaryType = try { species.primaryType.name.lowercase() } catch (_: Exception) { "normal" },
                    secondaryType = try { species.secondaryType?.name?.lowercase() } catch (_: Exception) { null },
                    catchRate = try { species.catchRate } catch (_: Exception) { 45 },
                    weight = try { species.weight } catch (_: Exception) { 0f },
                    height = try { species.height } catch (_: Exception) { 0f },
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
                    tmMoves = tmMoves,
                    shoulderMountable = shoulderMount,
                    source = source
                )
            } catch (e: Exception) {
                DebugLog.once("species-info-${species.name}") { "Failed to load species info for ${species.name}: ${e.message}" }
            }
        }

        // Load alternate forms as separate entries
        if (com.cobbledex.config.CobbleDexConfig.get().showAlternateForms) {
            var formCount = 0
            var skippedDuplicateAspectCount = 0
            for (species in implemented) {
                val baseName = species.name.lowercase()
                val baseForm = try { species.standardForm } catch (_: Exception) { null }
                // shouldIncludeForm already drops cosmetic "-costume" forms (see
                // its comment). This grouping is just a safety net in case two
                // eligible forms still end up sharing one aspect set for some
                // other reason - keep the shortest/cleanest name in that case.
                val eligibleForms = try { species.forms } catch (_: Exception) { emptyList() }
                    .filter { form -> shouldIncludeForm(species, form, baseForm) }
                val winningForms = eligibleForms
                    .groupBy { form -> form.aspects.map { it.lowercase() }.toSet() }
                    .flatMap { (aspectKey, candidates) ->
                        if (aspectKey.isEmpty() || candidates.size <= 1) candidates
                        else listOf(candidates.minBy { it.name.length })
                    }
                skippedDuplicateAspectCount += eligibleForms.size - winningForms.size

                for (form in winningForms) {
                    try {
                        val formKey = buildFormEntryKey(baseName, form, species)
                        val info = buildFormSpeciesInfo(formKey, species, form, baseForm)
                        val existing = result[formKey]
                        if (existing != null) {
                            // Merge: form data fills in gaps (O3 regional dedup)
                            result[formKey] = mergeFormInfo(existing, info)
                        } else {
                            result[formKey] = info
                        }
                        formCount++
                    } catch (e: Exception) {
                        DebugLog.once("form-${species.name}-${form.name}") { "Failed to load form: ${e.message}" }
                    }
                }
            }
            DebugLog.info("Loaded ${result.size} species from runtime API ($dropSpeciesCount with drops, $formCount alternate forms, $skippedDuplicateAspectCount cosmetic-skin duplicates skipped)")
        } else {
            DebugLog.info("Loaded ${result.size} species from runtime API ($dropSpeciesCount with drops, alternate forms disabled)")
        }

        return result
    }

    private val SIGNIFICANT_LABELS = setOf(
        "mega", "primal", "ultra_burst", "gmax",
        "alolan_form", "galarian_form", "hisuian_form", "paldean_form"
    )

    // Same idea as SIGNIFICANT_LABELS, but matched against the form's own
    // ASPECT instead of its labels - some packs (or a species_additions patch
    // from a different mod) ship a form whose labels don't carry the
    // "gmax"/"mega"/etc marker at all (e.g. this modpack's live Eevee Gmax
    // form reports labels=[gen1], not [gen8, gmax], even though its aspect is
    // still plainly "gmax") - relying on labels alone silently dropped these.
    // Aspects are the more reliable signal since Cobblemon itself keys pose/
    // texture resolution off them, so a mod overriding flavor labels is much
    // less likely to also break the aspect string.
    private val SIGNIFICANT_ASPECT_MARKERS = setOf(
        "mega", "primal", "ultra_burst", "gmax", "gigantamax",
        "alolan", "galarian", "hisuian", "paldean", "alola", "galar", "hisui", "paldea"
    )

    private fun hasSignificantAspect(aspects: Set<String>): Boolean = aspects.any { aspect ->
        val lower = aspect.lowercase()
        SIGNIFICANT_ASPECT_MARKERS.any { marker -> lower == marker || lower.startsWith("${marker}_") || lower.startsWith("${marker}-") }
    }

    private val REGIONAL_LABEL_TO_SUFFIX = mapOf(
        "alolan_form" to "alolan",
        "galarian_form" to "galarian",
        "hisuian_form" to "hisuian",
        "paldean_form" to "paldean"
    )

    // Not private: DiagnosticService.showRawForms calls this directly to show
    // exactly why a given form was or wasn't surfaced as an alternate form.
    fun shouldIncludeForm(species: com.cobblemon.mod.common.pokemon.Species, form: com.cobblemon.mod.common.pokemon.FormData, baseForm: com.cobblemon.mod.common.pokemon.FormData?): Boolean {
        if (baseForm != null && form == baseForm) return false
        if (form.name.isBlank()) return false

        // Cobblemon's cosmetic-item system (wardrobe costumes granted via
        // consumable items, e.g. data/cobblemon/cosmetic_items/*.json) tags
        // every aspect it contributes with a "-costume" suffix (confirmed via
        // /cobbledex forms: Lucario has real forms like "Mega" aspects=[mega]
        // alongside cosmetic ones like "Mega-Chef-Costume" aspects=[chef-costume, mega]
        // and standalone "Cafe-Costume" aspects=[cafe-costume]). These ARE genuine
        // FormData entries registered by Cobblemon at runtime - not a bug in
        // another mod - but they're purely cosmetic reskins, not gameplay forms,
        // so they must never surface as alternate forms or evolution outcomes.
        if (form.aspects.any { it.endsWith("-costume", ignoreCase = true) }) return false

        if (form.labels.any { it in SIGNIFICANT_LABELS }) return true
        if (hasSignificantAspect(form.aspects.toSet())) return true

        // A form with no distinguishing aspect can't meaningfully differ from
        // the species' own look (same "empty aspects = same as base" convention
        // used elsewhere for evolution data). Some species_additions patches
        // register their own copy of the base/"Normal" form that isn't
        // reference-equal to species.standardForm, so the check above alone
        // doesn't catch it - this is what was surfacing as a bogus
        // "<species> Normal" alternate form for most of the dex.
        if (form.aspects.isEmpty()) return false

        val base = baseForm ?: return false
        val typeDiffers = form.primaryType != base.primaryType || form.secondaryType != base.secondaryType
        val statsDiffer = form.baseStats != base.baseStats && form.baseStats.isNotEmpty()
        val abilitiesDiffer = try {
            val formAbilityList = form.abilities.toList()
            val baseAbilityList = base.abilities.toList()
            formAbilityList != baseAbilityList && formAbilityList.isNotEmpty()
        } catch (_: Exception) { false }

        return typeDiffers || statsDiffer || abilitiesDiffer
    }

    // Not private: DiagnosticService.showRawForms uses the real key so its
    // "surfaced"/"in speciesInfo" checks match production exactly.
    fun buildFormEntryKey(baseName: String, form: com.cobblemon.mod.common.pokemon.FormData, species: com.cobblemon.mod.common.pokemon.Species): String {
        // Regional forms reuse SpeciesNameNormalizer's pattern for dedup with spawn data (O3)
        val regionalLabel = form.labels.firstOrNull { it in REGIONAL_LABEL_TO_SUFFIX }
        if (regionalLabel != null) {
            val suffix = REGIONAL_LABEL_TO_SUFFIX[regionalLabel]!!
            return "${SpeciesNameNormalizer.normalize(baseName)}$suffix"
        }
        // Non-regional: underscore-separated normalized key (O12)
        val normalizedFormName = form.name.lowercase().replace(Regex("[^a-z0-9]"), "")
        return "${SpeciesNameNormalizer.normalize(baseName)}_$normalizedFormName"
    }

    private fun buildFormSpeciesInfo(
        formKey: String,
        species: com.cobblemon.mod.common.pokemon.Species,
        form: com.cobblemon.mod.common.pokemon.FormData,
        baseForm: com.cobblemon.mod.common.pokemon.FormData?
    ): SpeciesBasicInfo {
        val baseName = species.name.lowercase()

        val stats = try {
            val statMap = mutableMapOf<String, Int>()
            for (stat in Stats.PERMANENT) {
                val value = if (form.baseStats.isNotEmpty()) {
                    form.baseStats[stat] ?: species.baseStats[stat] ?: 0
                } else {
                    species.baseStats[stat] ?: 0
                }
                statMap[stat.showdownId] = value
            }
            statMap.ifEmpty { null }
        } catch (_: Exception) { null }

        val bst = stats?.values?.sum()

        val abilityNames = try {
            val common = mutableListOf<String>()
            var hidden: String? = null
            val abilities = form.abilities.toList().let { if (it.isNotEmpty()) it else baseForm?.abilities?.toList() ?: emptyList() }
            for (ability in abilities) {
                val abilityName = titleCase(ability.template.name)
                if (ability is HiddenAbility) hidden = abilityName
                else common.add(abilityName)
            }
            Pair(common.ifEmpty { null }, hidden)
        } catch (_: Exception) { Pair(null, null) }

        val primaryType = try { form.primaryType?.name?.lowercase() ?: species.primaryType?.name?.lowercase() ?: "normal" } catch (_: Exception) { "normal" }
        val secondaryType = try { form.secondaryType?.name?.lowercase() ?: species.secondaryType?.name?.lowercase() } catch (_: Exception) { null }

        val eggGroups = try {
            val groups = form.eggGroups.ifEmpty { species.eggGroups }
            groups.map { it.showdownID }.ifEmpty { null }
        } catch (_: Exception) { null }

        val description = try {
            val key = form.pokedex.firstOrNull() ?: species.pokedex.firstOrNull()
            if (key != null && key.isNotBlank()) key else null
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
            entries.ifEmpty { null }
        } catch (_: Exception) { null }

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
            if (moves.isNotEmpty()) {
                val grouped = mutableMapOf<Int, MutableList<MoveDetail>>()
                for ((level, moveList) in moves) {
                    for (move in moveList) {
                        grouped.getOrPut(level) { mutableListOf() }.add(toMoveDetail(move))
                    }
                }
                grouped.entries.sortedBy { it.key }
                    .map { LevelUpMove(it.key, it.value) }
                    .ifEmpty { null }
            } else null
        } catch (_: Exception) { null }

        val eggMoves = try {
            form.moves.eggMoves.map { toMoveDetail(it) }.ifEmpty { null }
        } catch (_: Exception) { null }

        val tutorMoves = try {
            form.moves.tutorMoves.map { toMoveDetail(it) }.ifEmpty { null }
        } catch (_: Exception) { null }

        val tmMoves = try {
            form.moves.tmMoves.map { toMoveDetail(it) }.ifEmpty { null }
        } catch (_: Exception) { null }

        // Build the form name for i18n: preserve original casing with hyphens (e.g. "mega-x", "therian")
        val rawFormName = form.name.lowercase()

        // Build formAspects for rendering
        val formAspects = form.aspects.toSet()

        return SpeciesBasicInfo(
            name = formKey,
            nationalDexNumber = try { species.nationalPokedexNumber } catch (_: Exception) { 0 },
            primaryType = primaryType,
            secondaryType = secondaryType,
            catchRate = try { form.catchRate } catch (_: Exception) { try { species.catchRate } catch (_: Exception) { 45 } },
            weight = try { form.weight } catch (_: Exception) { try { species.weight } catch (_: Exception) { 0f } },
            height = try { form.height } catch (_: Exception) { try { species.height } catch (_: Exception) { 0f } },
            baseStats = stats,
            baseStatTotal = bst,
            evYield = evYield,
            abilities = abilityNames.first,
            hiddenAbility = abilityNames.second,
            eggGroups = eggGroups,
            labels = form.labels.ifEmpty { null },
            preEvolution = try { species.preEvolution?.species?.name?.lowercase() } catch (_: Exception) { null },
            description = description,
            drops = drops,
            maleRatio = try { species.maleRatio } catch (_: Exception) { null },
            eggCycles = try { species.eggCycles } catch (_: Exception) { null },
            experienceGroup = try { species.experienceGroup.name } catch (_: Exception) { null },
            baseExperienceYield = try { species.baseExperienceYield } catch (_: Exception) { null },
            baseFriendship = try { species.baseFriendship } catch (_: Exception) { null },
            levelUpMoves = levelUpMoves,
            eggMoves = eggMoves,
            tutorMoves = tutorMoves,
            tmMoves = tmMoves,
            shoulderMountable = try { species.shoulderMountable } catch (_: Exception) { false },
            formName = rawFormName,
            baseSpeciesName = baseName,
            formAspects = formAspects,
            source = try { species.resourceIdentifier?.namespace } catch (_: Exception) { null }
        )
    }

    /** Merge a form entry into an existing one (e.g. regional form dedup — O3) */
    private fun mergeFormInfo(existing: SpeciesBasicInfo, incoming: SpeciesBasicInfo): SpeciesBasicInfo {
        return existing.copy(
            primaryType = incoming.primaryType,
            secondaryType = incoming.secondaryType,
            baseStats = incoming.baseStats ?: existing.baseStats,
            baseStatTotal = incoming.baseStatTotal ?: existing.baseStatTotal,
            evYield = incoming.evYield ?: existing.evYield,
            abilities = incoming.abilities ?: existing.abilities,
            hiddenAbility = incoming.hiddenAbility ?: existing.hiddenAbility,
            eggGroups = incoming.eggGroups ?: existing.eggGroups,
            labels = incoming.labels ?: existing.labels,
            description = incoming.description ?: existing.description,
            drops = incoming.drops ?: existing.drops,
            levelUpMoves = incoming.levelUpMoves ?: existing.levelUpMoves,
            eggMoves = incoming.eggMoves ?: existing.eggMoves,
            tutorMoves = incoming.tutorMoves ?: existing.tutorMoves,
            tmMoves = incoming.tmMoves ?: existing.tmMoves,
            formName = incoming.formName ?: existing.formName,
            baseSpeciesName = incoming.baseSpeciesName ?: existing.baseSpeciesName,
            formAspects = incoming.formAspects.ifEmpty { existing.formAspects }
        )
    }
}
