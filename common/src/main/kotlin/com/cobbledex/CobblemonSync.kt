package com.cobbledex

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.pokemon.moves.Learnset

/**
 * What this Cobblemon build actually sends a client, and what that changes.
 *
 * Two things vary by version: TM/egg moves (1.8.0+) and a display-only evolution list (1.8.2+).
 * Spawn pools are never sent by any version.
 *
 * ## TM and egg moves
 *
 * Cobblemon 1.7.x writes only level-up moves to the client, so on a server every form's TM/egg list
 * is empty no matter what its datapack says. From 1.8.0 `Learnset.encode` also writes TM and egg
 * moves, so a form's lists are exactly what the server loaded. Tutor and legacy moves are never
 * sent by any version.
 *
 * The 1.7.x behaviour (an empty form list means "inherit the base form's", topped up from the base
 * species' local file) is the only workable guess without the data, so it stays untouched wherever
 * [tmEggSynced] is false.
 */
object CobblemonSync {

    /**
     * Cobblemon 1.8.2 added `DexEvolutionDisplaySyncPacket`: every species' and form's evolutions,
     * computed on the server and sent to each player as from/to species, form, aspects and
     * tooltip-only requirements. `Species.encode` still carries no evolutions of its own, so on a
     * server this list is the only evolution data a client gets from Cobblemon itself.
     */
    val evolutionDisplaysSynced: Boolean by lazy {
        try {
            Class.forName("com.cobblemon.mod.common.net.messages.client.data.DexEvolutionDisplaySyncPacket")
            true
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * 1.8.0+ gave `Learnset` a private `encodeMoveList` helper, used for the TM and egg lists.
     * Absent on 1.7.x. Any failure reads as "not supported" so an unrecognised build keeps the old
     * behaviour.
     */
    private val cobblemonSendsTmEgg: Boolean by lazy {
        try {
            Learnset::class.java.declaredMethods.any { it.name == "encodeMoveList" }
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * True when this session's species registry carries real TM/egg lists: the installed Cobblemon
     * sends them, and either the world is local (the registry came straight from the datapacks) or
     * at least one synced learnset actually has some. The last check covers a 1.8 client joining a
     * 1.7 server, where the registry is all-empty and treating that as truth would blank every page.
     */
    fun tmEggSynced(): Boolean =
        decide(cobblemonSendsTmEgg, DataAvailability.isLocalWorld()) { registryHasAnyTmOrEgg() }

    internal fun decide(
        installedCobblemonSendsThem: Boolean,
        localWorld: Boolean,
        registryHasAny: () -> Boolean,
    ): Boolean = installedCobblemonSendsThem && (localWorld || registryHasAny())

    /**
     * May a form take the base form's list when its own is empty? On 1.7.x yes, always. Once TM and
     * egg lists are synced an empty list is real data, so only a form that defines no moves of any
     * kind inherits (Cobblemon's `"moves": []` convention).
     */
    fun formInheritsBaseMoves(tmEggSynced: Boolean, formLearnsetEmpty: Boolean): Boolean =
        !tmEggSynced || formLearnsetEmpty

    /**
     * May a form with no local file of its own be topped up from its base species' file? Only when
     * the form's lists aren't synced; otherwise that file describes the base Pokémon, not the form.
     */
    fun formUsesBaseSpeciesLocalMoves(tmEggSynced: Boolean, isForm: Boolean): Boolean =
        !tmEggSynced || !isForm

    private fun registryHasAnyTmOrEgg(): Boolean = try {
        fun Learnset.hasTmOrEgg() = tmMoves.isNotEmpty() || eggMoves.isNotEmpty()
        PokemonSpecies.implemented.any { species ->
            species.moves.hasTmOrEgg() || species.forms.any { it.moves.hasTmOrEgg() }
        }
    } catch (_: Throwable) {
        false
    }
}
