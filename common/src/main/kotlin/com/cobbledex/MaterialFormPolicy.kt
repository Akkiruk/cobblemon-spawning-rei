package com.cobbledex

object MaterialFormPolicy {
    data class SpeciesSnapshot(
        val name: String,
        val primaryType: String,
        val secondaryType: String?,
        val catchRate: Int,
        val weight: Float,
        val height: Float,
        val baseStats: Map<String, Int> = emptyMap(),
        val baseStatTotal: Int? = null,
        val evYield: Map<String, Int> = emptyMap(),
        val abilities: Set<String> = emptySet(),
        val hiddenAbility: String? = null,
        val eggGroups: Set<String> = emptySet(),
        val labels: Set<String> = emptySet(),
        val drops: Set<String> = emptySet(),
        val levelUpMoves: Set<String> = emptySet(),
        val eggMoves: Set<String> = emptySet(),
        val tutorMoves: Set<String> = emptySet(),
        val tmMoves: Set<String> = emptySet(),
        val shoulderMountable: Boolean = false,
        val maleRatio: Float? = null,
        val eggCycles: Int? = null,
        val experienceGroup: String? = null,
        val baseExperienceYield: Int? = null,
        val baseFriendship: Int? = null,
        val baseSpeciesName: String? = null,
        val formAspects: Set<String> = emptySet()
    )

    data class DomainSignals(
        val hasFormSpecificSpawns: Boolean = false,
        val hasFormSpecificObtainment: Boolean = false,
        val hasFormSpecificEvolutions: Boolean = false,
        val hasFormSpecificFossils: Boolean = false,
        val hasFormSpecificRiding: Boolean = false
    )

    data class Decision(val surface: Boolean, val reasons: List<String>)

    fun decide(base: SpeciesSnapshot?, form: SpeciesSnapshot, signals: DomainSignals = DomainSignals()): Decision {
        val reasons = mutableListOf<String>()
        if (form.baseSpeciesName == null) return Decision(true, listOf("base species"))
        if (base == null) return Decision(true, listOf("base species data missing"))

        addIfDifferent(reasons, "typing", listOf(base.primaryType, base.secondaryType), listOf(form.primaryType, form.secondaryType))
        addIfDifferent(reasons, "abilities", base.abilities + setOfNotNull(base.hiddenAbility), form.abilities + setOfNotNull(form.hiddenAbility))
        addIfDifferent(reasons, "base stats", base.baseStats, form.baseStats)
        addIfDifferent(reasons, "base stat total", base.baseStatTotal, form.baseStatTotal)
        addIfDifferent(reasons, "EV yield", base.evYield, form.evYield)
        addIfDifferent(reasons, "catch rate", base.catchRate, form.catchRate)
        addIfDifferent(reasons, "body metrics", listOf(base.height, base.weight), listOf(form.height, form.weight))
        addIfDifferent(reasons, "egg groups", base.eggGroups, form.eggGroups)
        addIfDifferent(reasons, "gender ratio", base.maleRatio, form.maleRatio)
        addIfDifferent(reasons, "egg cycles", base.eggCycles, form.eggCycles)
        addIfDifferent(reasons, "experience group", base.experienceGroup, form.experienceGroup)
        addIfDifferent(reasons, "base experience", base.baseExperienceYield, form.baseExperienceYield)
        addIfDifferent(reasons, "base friendship", base.baseFriendship, form.baseFriendship)
        addIfDifferent(reasons, "labels", base.labels, form.labels)
        addIfDifferent(reasons, "drops", base.drops, form.drops)
        addIfDifferent(reasons, "level-up moves", base.levelUpMoves, form.levelUpMoves)
        addIfDifferent(reasons, "egg moves", base.eggMoves, form.eggMoves)
        addIfDifferent(reasons, "tutor moves", base.tutorMoves, form.tutorMoves)
        addIfDifferent(reasons, "TM moves", base.tmMoves, form.tmMoves)
        addIfDifferent(reasons, "shoulder mount", base.shoulderMountable, form.shoulderMountable)

        if (signals.hasFormSpecificSpawns) reasons.add("form-specific spawn data")
        if (signals.hasFormSpecificObtainment) reasons.add("form-specific obtainment data")
        if (signals.hasFormSpecificEvolutions) reasons.add("form-specific evolution data")
        if (signals.hasFormSpecificFossils) reasons.add("form-specific fossil data")
        if (signals.hasFormSpecificRiding) reasons.add("form-specific riding data")

        return Decision(reasons.isNotEmpty(), reasons.distinct())
    }

    fun decisionFor(
        formKey: String,
        speciesInfo: Map<String, EvolutionDataLoader.SpeciesBasicInfo>,
        spawnsBySpecies: Map<String, List<SpawnInfo>>,
        obtainmentBySpecies: Map<String, List<ObtainmentInfo>>,
        evolutionsBySpecies: Map<String, List<EvolutionInfo>>,
        fossilsBySpecies: Map<String, List<FossilCombo>>,
        ridingBySpecies: Map<String, RidingInfo>
    ): Decision {
        val normalizedForm = SpeciesNameNormalizer.normalize(formKey)
        val formInfo = speciesInfo[normalizedForm] ?: return Decision(false, emptyList())
        val baseKey = formInfo.baseSpeciesName?.let(SpeciesNameNormalizer::normalize)
        val baseInfo = baseKey?.let { speciesInfo[it] }
        val formSnapshot = snapshotOf(normalizedForm, formInfo)
        val baseSnapshot = if (baseKey != null && baseInfo != null) snapshotOf(baseKey, baseInfo) else null
        val formAspects = normalizedAspectTokens(formInfo.formAspects)

        val signals = DomainSignals(
            hasFormSpecificSpawns = spawnsBySpecies[normalizedForm].orEmpty().isNotEmpty() ||
                spawnsBySpecies[baseKey].orEmpty().any { matchesFormAspects(formAspects, it.formAspects) },
            hasFormSpecificObtainment = obtainmentBySpecies[normalizedForm].orEmpty().isNotEmpty() ||
                obtainmentBySpecies[baseKey].orEmpty().any { matchesFormAspects(formAspects, it.formAspects) },
            hasFormSpecificEvolutions = evolutionsBySpecies[normalizedForm].orEmpty().isNotEmpty() ||
                evolutionsBySpecies.values.flatten().any { evo ->
                    SpeciesNameNormalizer.normalize(evo.fromSpecies) == normalizedForm ||
                        SpeciesNameNormalizer.normalize(evo.toSpecies) == normalizedForm ||
                        evo.fromAspects.any { normalizedAspectToken(it) in formAspects } ||
                        evo.toAspects.any { normalizedAspectToken(it) in formAspects }
                },
            hasFormSpecificFossils = fossilsBySpecies[normalizedForm].orEmpty().isNotEmpty(),
            hasFormSpecificRiding = ridingBySpecies.containsKey(normalizedForm)
        )

        return decide(baseSnapshot, formSnapshot, signals)
    }

    private fun snapshotOf(key: String, info: EvolutionDataLoader.SpeciesBasicInfo) = SpeciesSnapshot(
        name = key,
        primaryType = info.primaryType.lowercase(),
        secondaryType = info.secondaryType?.lowercase(),
        catchRate = info.catchRate,
        weight = info.weight,
        height = info.height,
        baseStats = info.baseStats ?: emptyMap(),
        baseStatTotal = info.baseStatTotal,
        evYield = info.evYield ?: emptyMap(),
        abilities = info.abilities.orEmpty().map { it.lowercase() }.toSet(),
        hiddenAbility = info.hiddenAbility?.lowercase(),
        eggGroups = info.eggGroups.orEmpty().map { it.lowercase() }.toSet(),
        labels = info.labels.orEmpty().map { it.lowercase() }.toSet(),
        drops = info.drops.orEmpty().map { drop ->
            listOf(drop.itemId.lowercase(), drop.percentage.toString(), drop.quantity.toString(), drop.quantityRange.orEmpty()).joinToString("|")
        }.toSet(),
        levelUpMoves = info.levelUpMoves.orEmpty().flatMap { entry ->
            entry.moves.map { move -> "${entry.level}:${move.name.lowercase()}" }
        }.toSet(),
        eggMoves = info.eggMoves.orEmpty().map { it.name.lowercase() }.toSet(),
        tutorMoves = info.tutorMoves.orEmpty().map { it.name.lowercase() }.toSet(),
        tmMoves = info.tmMoves.orEmpty().map { it.name.lowercase() }.toSet(),
        shoulderMountable = info.shoulderMountable,
        maleRatio = info.maleRatio,
        eggCycles = info.eggCycles,
        experienceGroup = info.experienceGroup?.lowercase(),
        baseExperienceYield = info.baseExperienceYield,
        baseFriendship = info.baseFriendship,
        baseSpeciesName = info.baseSpeciesName?.let(SpeciesNameNormalizer::normalize),
        formAspects = info.formAspects.map(::normalizedAspectToken).filter { it.isNotBlank() }.toSet()
    )

    private fun normalizedAspectTokens(aspects: Set<String>): Set<String> =
        aspects.map(::normalizedAspectToken).filter { it.isNotBlank() }.toSet()

    private fun normalizedAspectTokens(raw: String): Set<String> =
        raw.split(Regex("[,;|\\s]+"))
            .map(::normalizedAspectToken)
            .filter { it.isNotBlank() }
            .toSet()

    private fun normalizedAspectToken(raw: String): String =
        raw.lowercase().replace(Regex("[^a-z0-9]"), "")

    private fun matchesFormAspects(formAspects: Set<String>, rawAspects: String): Boolean {
        if (formAspects.isEmpty() || rawAspects.isBlank()) return false
        val candidate = normalizedAspectTokens(rawAspects)
        return candidate.any { it in formAspects }
    }

    private fun <T> addIfDifferent(reasons: MutableList<String>, reason: String, base: T, form: T) {
        if (base != form) reasons.add(reason)
    }
}