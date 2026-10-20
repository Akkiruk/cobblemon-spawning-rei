package com.cobbledex.server

import com.cobbledex.sync.SyncProtocol
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServerSyncConfigTest {

    @Test
    fun valuesAreClampedSoATypoCannotMakeTheSyncUnsafe() {
        val c = ServerSyncConfig(chunkKB = 999_999, cooldownSeconds = 0, maxPayloadMB = 9_999)
        assertEquals(SyncProtocol.MAX_CHUNK_BYTES, c.chunkBytes)
        assertEquals(5_000L, c.cooldownMs)
        assertTrue(c.maxPayloadBytes <= SyncProtocol.MAX_COMPRESSED_BYTES)
        val low = ServerSyncConfig(chunkKB = -5, cooldownSeconds = -1, maxPayloadMB = -1)
        assertEquals(16 * 1024, low.chunkBytes)
        assertEquals(1024 * 1024, low.maxPayloadBytes)
    }

    @Test
    fun fileGlobsMatchWholeIdsOnly() {
        val c = ServerSyncConfig(excludeFiles = listOf("mypack:spawn_pool_world/secret_*", "other:species/exact.json"))
        assertTrue(c.excludesFile("mypack:spawn_pool_world/secret_event.json"))
        assertTrue(c.excludesFile("other:species/exact.json"))
        assertFalse(c.excludesFile("mypack:spawn_pool_world/public.json"))
        assertFalse(c.excludesFile("xother:species/exact.json"))
        assertFalse(c.excludesFile("other:species/exact.json.bak"))
    }

    @Test
    fun regexCharactersInAGlobAreTakenLiterally() {
        val c = ServerSyncConfig(excludeFiles = listOf("a:b/(x)+.json"))
        assertTrue(c.excludesFile("a:b/(x)+.json"))
        assertFalse(c.excludesFile("a:b/xx.json"))
    }

    @Test
    fun speciesMatchIgnoresCaseAndNamespace() {
        val c = ServerSyncConfig(excludeSpecies = listOf("Cobblemon:SecretMon", "plain"))
        assertTrue(c.excludesSpecies("secretmon"))
        assertTrue(c.excludesSpecies("cobblemon:PLAIN"))
        assertFalse(c.excludesSpecies("pikachu"))
        assertFalse(c.excludesSpecies(null))
    }

    @Test
    fun aMissingFileIsCreatedWithTheDefaults() {
        val dir = Files.createTempDirectory("cdx-config")
        val c = ServerSyncConfig.load(dir)
        assertTrue(c.enabled)
        assertTrue(Files.exists(dir.resolve(ServerSyncConfig.FILE_NAME)))
        // and reading it back gives the same thing
        assertEquals(c.chunkBytes, ServerSyncConfig.load(dir).chunkBytes)
    }

    @Test
    fun anEditedFileIsHonouredAndMissingKeysKeepTheirDefaults() {
        val dir = Files.createTempDirectory("cdx-config")
        Files.writeString(dir.resolve(ServerSyncConfig.FILE_NAME), """{"enabled": false, "excludeSpecies": ["x"]}""")
        val c = ServerSyncConfig.load(dir)
        assertFalse(c.enabled)
        assertTrue(c.excludesSpecies("x"))
        assertEquals(60_000L, c.cooldownMs)
    }

    @Test
    fun aBrokenFileGivesTheDefaultsInsteadOfThrowing() {
        val dir = Files.createTempDirectory("cdx-config")
        Files.writeString(dir.resolve(ServerSyncConfig.FILE_NAME), "{ this is {{ not json")
        assertTrue(ServerSyncConfig.load(dir).enabled)
        Files.writeString(dir.resolve(ServerSyncConfig.FILE_NAME), """{"excludeSpecies": null, "excludeFiles": null}""")
        assertTrue(ServerSyncConfig.load(dir).enabled)
    }
}
