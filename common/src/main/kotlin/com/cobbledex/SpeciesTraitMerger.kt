package com.cobbledex

import com.cobblemon.mod.common.api.moves.Moves
import com.cobbledex.EvolutionDataLoader.SpeciesBasicInfo

/**
 * Fills the per-species gaps Cobblemon's network sync leaves behind, from this client's files.
 *
 * Cobblemon's `Species.encode` / `FormData.encode` carry stats, types, abilities, learnsets, drops,
 * forms and riding — but **not** `catchRate`, `eggGroups`, `eggCycles`, `baseFriendship`,
 * `evYield`, `baseExperienceYield` or `labels`. On a dedicated server a client therefore has a
 * species object with real stats and blank breeding data. In singleplayer the runtime has
 * everything, so nothing here fires.
 *
 * Two rules keep this honest:
 *  - Only ever fill a field that is genuinely absent. A value Cobblemon supplied is never replaced.
 *  - A form's own file wins over its base species' file, so a form that defines its own moves or
 *    breeding data never silently inherits the base Pokémon's.
 */
object SpeciesTraitMerger {

    data class Result(
        val speciesInfo: Map<String, SpeciesBasicInfo>,
        val filledSpeciesCount: Int,
        val filledFieldCount: Int,
    )

    /**
     * The values a Cobblemon `Species` carries when the field was never populated — read straight
     * off `Species`' no-arg constructor, which is exactly the state a client is left in for the
     * fields `Species.encode` doesn't write.
     *
     * These are sentinels, not proof of absence: a species whose real catch rate is genuinely 45
     * is indistinguishable, by value alone, from one that was never synced. That ambiguity is
     * real **after a network sync** — a dedicated-server client's `Species` object never had these
     * fields decoded at all, so filling a sentinel-valued field there can only be correct or a
     * harmless no-op (the local file for a genuinely-45 species also resolves to 45).
     *
     * In singleplayer/LAN, though, `Species` is populated straight from the loaded datapacks, not
     * network decoding — a genuinely-45 species has a *real* 45, not an unset one. If the client's
     * separately-cached local files (`JarDataCache`, scanned once at launch) are ever stale versus
     * what actually got loaded this session — a datapack edited without restarting, say — filling
     * on the sentinel match there would silently swap a real value for a stale one. So [fillGaps]
     * only applies these four sentinel-gated fields off a network sync ([trustSentinelDefaults]),
     * never in a local world. The other fields below (`eggGroups`, `evYield`, `labels`, and the
     * move lists) key off genuine absence (null/empty) rather than a magic value, so they carry no
     * such ambiguity and fill in both world types.
     */
    private const val UNSET_CATCH_RATE = 45
    private const val UNSET_BASE_EXPERIENCE_YIELD = 10
    private const val UNSET_EGG_CYCLES = 120
    private const val UNSET_BASE_FRIENDSHIP = 0

    /**
     * @param trustSentinelDefaults Whether a sentinel-valued field (see [UNSET_CATCH_RATE] and
     * siblings) is safe to treat as "Cobblemon never synced this" and fill from local files.
     * Defaults to [DataAvailability.isLocalWorld]'s negation — true after a network sync (where
     * that's guaranteed), false in singleplayer/LAN (where it's merely usually true).
     */
    fun fillGaps(
        runtime: Map<String, SpeciesBasicInfo>,
        trustSentinelDefaults: Boolean = !DataAvailability.isLocalWorld(),
    ): Result {
        if (runtime.isEmpty()) return Result(runtime, 0, 0)

        val traits = JarDataCache.getCachedTraits()
        val formTraits = JarDataCache.getCachedFormTraits()
        val moves = JarDataCache.getCachedMoves()
        val formMoves = JarDataCache.getCachedFormMoves()
        if (traits.isEmpty() && formTraits.isEmpty() && moves.isEmpty() && formMoves.isEmpty()) {
            return Result(runtime, 0, 0)
        }

        val merged = LinkedHashMap<String, SpeciesBasicInfo>(runtime.size)
        var filledSpecies = 0
        var filledFields = 0

        for ((key, info) in runtime) {
            val baseKey = info.baseSpeciesName?.let { SpeciesNameNormalizer.normalize(it) } ?: key
            val localTraits = formTraits[key] ?: traits[baseKey]
            val localMoves = formMoves[key] ?: moves[baseKey]

            if (localTraits == null && localMoves == null) {
                merged[key] = info
                continue
            }

            var fieldsForThisSpecies = 0
            var updated = info

            if (localTraits != null) {
                val (withTraits, traitFields) = mergeTraits(updated, localTraits, trustSentinelDefaults)
                updated = withTraits
                fieldsForThisSpecies += traitFields
            }

            // Move lists: Cobblemon does sync a Learnset, but a species_additions-nested form
            // routinely arrives with only its level-up moves, dropping the egg/tutor/TM entries the
            // form's own JSON defines (confirmed via /cobbledex evo: Laser's Fakemon Pack's
            // Fomantis Lunar form defines 102 moves but the runtime exposed only 13 level-up ones).
            if (localMoves != null) {
                if (updated.levelUpMoves == null && localMoves.levelUp.isNotEmpty()) {
                    val resolved = localMoves.levelUp.entries.sortedBy { it.key }.mapNotNull { (level, names) ->
                        names.mapNotNull(::resolveMove).takeIf { it.isNotEmpty() }?.let { LevelUpMove(level, it) }
                    }
                    if (resolved.isNotEmpty()) { updated = updated.copy(levelUpMoves = resolved); fieldsForThisSpecies++ }
                }
                if (updated.eggMoves == null && localMoves.egg.isNotEmpty()) {
                    localMoves.egg.mapNotNull(::resolveMove).takeIf { it.isNotEmpty() }?.let {
                        updated = updated.copy(eggMoves = it); fieldsForThisSpecies++
                    }
                }
                if (updated.tutorMoves == null && localMoves.tutor.isNotEmpty()) {
                    localMoves.tutor.mapNotNull(::resolveMove).takeIf { it.isNotEmpty() }?.let {
                        updated = updated.copy(tutorMoves = it); fieldsForThisSpecies++
                    }
                }
                if (updated.tmMoves == null && localMoves.tm.isNotEmpty()) {
                    localMoves.tm.mapNotNull(::resolveMove).takeIf { it.isNotEmpty() }?.let {
                        updated = updated.copy(tmMoves = it); fieldsForThisSpecies++
                    }
                }
            }

            merged[key] = updated
            if (fieldsForThisSpecies > 0) {
                filledSpecies++
                filledFields += fieldsForThisSpecies
            }
        }

        return Result(merged, filledSpecies, filledFields)
    }

    /**
     * Fills the breeding/dex fields of one species from local files, returning the result and how
     * many fields were actually filled. A field is only filled when Cobblemon left it at its unset
     * default — a value Cobblemon supplied is never replaced.
     */
    fun mergeTraits(
        info: SpeciesBasicInfo,
        local: JarDataCache.JarTraitData,
        trustSentinelDefaults: Boolean = true,
    ): Pair<SpeciesBasicInfo, Int> {
        var updated = info
        var filled = 0

        if (trustSentinelDefaults) {
            if (updated.catchRate == UNSET_CATCH_RATE && local.catchRate != null) {
                updated = updated.copy(catchRate = local.catchRate); filled++
            }
            if ((updated.eggCycles == null || updated.eggCycles == UNSET_EGG_CYCLES) && local.eggCycles != null) {
                updated = updated.copy(eggCycles = local.eggCycles); filled++
            }
            if ((updated.baseFriendship == null || updated.baseFriendship == UNSET_BASE_FRIENDSHIP) &&
                local.baseFriendship != null
            ) {
                updated = updated.copy(baseFriendship = local.baseFriendship); filled++
            }
            if ((updated.baseExperienceYield == null || updated.baseExperienceYield == UNSET_BASE_EXPERIENCE_YIELD) &&
                local.baseExperienceYield != null
            ) {
                updated = updated.copy(baseExperienceYield = local.baseExperienceYield); filled++
            }
        }

        // Unambiguous absence (null/empty), not a magic value — safe to fill regardless of world type.
        if (updated.eggGroups.isNullOrEmpty() && local.eggGroups != null) {
            updated = updated.copy(eggGroups = local.eggGroups); filled++
        }
        if (updated.evYield.isNullOrEmpty() && local.evYield != null) {
            updated = updated.copy(evYield = local.evYield); filled++
        }
        if (updated.labels.isNullOrEmpty() && local.labels != null) {
            updated = updated.copy(labels = local.labels); filled++
        }

        return updated to filled
    }

    /**
     * Move *details* always come from Cobblemon's synced `Moves` registry — the local files only
     * supply which moves a species learns, never their power/accuracy/type.
     */
    private fun resolveMove(name: String): MoveDetail? = try {
        Moves.getByName(name)?.let { template ->
            MoveDetail(
                name = template.name,
                type = try { template.elementalType.name.lowercase() } catch (_: Exception) { "normal" },
                category = try { template.damageCategory.name } catch (_: Exception) { "PHYSICAL" },
                power = try { template.power.toInt() } catch (_: Exception) { 0 },
                accuracy = try { template.accuracy.toInt() } catch (_: Exception) { 0 },
                pp = try { template.pp } catch (_: Exception) { 0 },
            )
        }
    } catch (_: Exception) { null }
}
