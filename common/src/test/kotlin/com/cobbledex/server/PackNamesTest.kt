package com.cobbledex.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PackNamesTest {
    private val mods = setOf("cobblemon", "mega_showdown")

    @Test
    fun aModIsNamedByItsIdUnderEitherLoadersScheme() {
        assertEquals("cobblemon", PackNames.display("cobblemon", mods))
        assertEquals("mega_showdown", PackNames.display("mod/mega_showdown", mods))
    }

    @Test
    fun aDatapackIsNamedByItsFileNameAloneNeverByItsServerPath() {
        assertEquals("Fakemon Pack", PackNames.display("file/Fakemon Pack.zip", mods))
        assertEquals("lumoria 1.6.7", PackNames.display("""openloader/C:\Users\me\server\config\openloader\packs\lumoria 1.6.7.zip""", mods))
        assertEquals("pack", PackNames.display("openloader//srv/minecraft/config/openloader/packs/pack.zip", mods))
    }

    @Test
    fun noServerDirectoryEverAppearsInTheName() {
        val name = PackNames.display("""openloader/C:\secret\place\x.zip""", mods)!!
        assertFalse(name.contains('\\') || name.contains('/') || name.contains("secret"))
    }

    @Test
    fun aDatapackFolderWithAModsNameIsStillADatapack() {
        assertEquals("cobblemon", PackNames.display("file/cobblemon", mods))   // the name, not the mod: it has a prefix
        assertTrue(PackNames.display("file/cobblemon", mods) != null)
    }

    @Test
    fun nothingNameableGivesNull() {
        assertNull(PackNames.display("", mods))
        assertNull(PackNames.display("file/", mods))
        assertNull(PackNames.display(".zip", mods))
    }
}
