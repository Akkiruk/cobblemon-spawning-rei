package com.cobbledex

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CobblemonSyncTest {

    @Test
    fun anOlderCobblemonNeverCountsAsSynced() {
        assertFalse(CobblemonSync.decide(installedCobblemonSendsThem = false, localWorld = true) { true })
        assertFalse(CobblemonSync.decide(installedCobblemonSendsThem = false, localWorld = false) { true })
    }

    @Test
    fun aLocalWorldOnANewCobblemonIsSyncedWithoutLookingAtTheRegistry() {
        assertTrue(CobblemonSync.decide(installedCobblemonSendsThem = true, localWorld = true) { error("not needed") })
    }

    @Test
    fun aServerOnlyCountsWhenSomeSyncedLearnsetActuallyHasTmOrEggMoves() {
        assertTrue(CobblemonSync.decide(installedCobblemonSendsThem = true, localWorld = false) { true })
        // A new client joining an old server: the registry is all-empty, so it is not real data.
        assertFalse(CobblemonSync.decide(installedCobblemonSendsThem = true, localWorld = false) { false })
    }

    @Test
    fun onAnOlderCobblemonEveryEmptyFormListStillInherits() {
        assertTrue(CobblemonSync.formInheritsBaseMoves(tmEggSynced = false, formLearnsetEmpty = false))
        assertTrue(CobblemonSync.formInheritsBaseMoves(tmEggSynced = false, formLearnsetEmpty = true))
    }

    @Test
    fun onANewCobblemonOnlyAFormWithNoMovesAtAllInherits() {
        assertFalse(CobblemonSync.formInheritsBaseMoves(tmEggSynced = true, formLearnsetEmpty = false))
        assertTrue(CobblemonSync.formInheritsBaseMoves(tmEggSynced = true, formLearnsetEmpty = true))
    }

    @Test
    fun aSyncedFormIsNeverToppedUpFromTheBaseSpeciesFileButABaseSpeciesStillIs() {
        assertTrue(CobblemonSync.formUsesBaseSpeciesLocalMoves(tmEggSynced = false, isForm = true))
        assertFalse(CobblemonSync.formUsesBaseSpeciesLocalMoves(tmEggSynced = true, isForm = true))
        assertTrue(CobblemonSync.formUsesBaseSpeciesLocalMoves(tmEggSynced = true, isForm = false))
    }
}
