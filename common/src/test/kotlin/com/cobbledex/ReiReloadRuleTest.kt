package com.cobbledex

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReiReloadRuleTest {

    @Test
    fun reiRegisteredBeforeTheIndexWasBuiltNeedsAReload() {
        assertTrue(RecipeViewerReloader.reiNeedsReload(registered = 0, current = 1128))
        assertTrue(RecipeViewerReloader.reiNeedsReload(registered = -1, current = 1128))
    }

    @Test
    fun reiThatRegisteredTheSameSpeciesIsLeftAlone() {
        assertFalse(RecipeViewerReloader.reiNeedsReload(registered = 1128, current = 1128))
    }

    @Test
    fun aServerAddingSpeciesAfterReiRegisteredNeedsAReload() {
        assertTrue(RecipeViewerReloader.reiNeedsReload(registered = 1026, current = 1128))
        assertTrue(RecipeViewerReloader.reiNeedsReload(registered = 1128, current = 1026))
    }

    @Test
    fun nothingIsDoneWhileTheIndexIsStillEmpty() {
        assertFalse(RecipeViewerReloader.reiNeedsReload(registered = -1, current = 0))
        assertFalse(RecipeViewerReloader.reiNeedsReload(registered = 0, current = 0))
    }
}
