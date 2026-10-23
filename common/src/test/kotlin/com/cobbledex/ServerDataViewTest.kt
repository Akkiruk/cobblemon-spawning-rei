package com.cobbledex

import com.cobbledex.sync.ServerOverlay
import com.google.gson.JsonParser
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * With a companion server's data active the loaders must see the server's world and not this client's own
 * packs; with none, they must behave exactly as they always have.
 */
class ServerDataViewTest {

    private lateinit var gameDir: Path
    private lateinit var modsDir: Path

    private fun write(path: Path, text: String) {
        Files.createDirectories(path.parent)
        Files.writeString(path, text)
    }

    private fun json(text: String) = JsonParser.parseString(text).asJsonObject

    private val spawnPool = """{"enabled":true,"spawns":[{"id":"s1","pokemon":"%s","bucket":"common","level":"5-10","weight":2.0,"condition":{}}]}"""

    @BeforeTest
    fun setUp() {
        gameDir = Files.createTempDirectory("cdx-game")
        modsDir = Files.createTempDirectory("cdx-mods")
        JarDataCache.gameDirProvider = { gameDir }
        ServerDataView.set(null)

        // This client's own datapack: it does not exist on the server.
        write(gameDir.resolve("datapacks/localpack/data/local/species/clientmon.json"), """{"name":"Clientmon","moves":["1:tackle","tutor:slash"]}""")
        write(gameDir.resolve("datapacks/localpack/data/local/spawn_pool_world/c.json"), spawnPool.format("clientmon"))

        // Two mod jars: the server has "goodmod" at this version, it has never heard of "badmod".
        write(modsDir.resolve("goodmod/data/goodmod/species/goodmon.json"), """{"name":"Goodmon","moves":["tutor:bite"]}""")
        write(modsDir.resolve("badmod/data/badmod/species/badmon.json"), """{"name":"Badmon","moves":["tutor:crunch"]}""")
    }

    @AfterTest
    fun tearDown() {
        ServerDataView.set(null)
        JarDataCache.gameDirProvider = { error("no game dir in tests") }
    }

    private fun roots() = listOf(
        SpawnDataLoader.ModRoot("goodmod", modsDir.resolve("goodmod")),
        SpawnDataLoader.ModRoot("badmod", modsDir.resolve("badmod")),
    )

    private fun serverOverlay() = ServerOverlay(
        hash = 1L,
        categories = mapOf(
            "species" to mapOf(
                "pack:species/fakemon.json" to json("""{"name":"Fakemon","moves":["1:tackle","tutor:bite","egg:splash"],"catchRate":45}"""),
            ),
            "species_additions" to emptyMap(),
            "spawn_pool_world" to mapOf("pack:spawn_pool_world/f.json" to json(spawnPool.format("fakemon"))),
            "spawn_detail_presets" to emptyMap(),
            "habitat_pools" to emptyMap(),
        ),
        serverMods = setOf("goodmod"),
        origins = mapOf("pack:species/fakemon.json" to "Fake Pack"),
    )

    @Test
    fun withoutServerDataTheClientsOwnPacksAndAllModsAreUsed() {
        JarDataCache.rebuildWith(roots())
        assertContains(JarDataCache.getCachedMoves(), "clientmon")
        assertContains(JarDataCache.getCachedMoves(), "goodmon")
        assertContains(JarDataCache.getCachedMoves(), "badmon")
        assertContains(JarDataCache.getCachedSpawns(), "clientmon")
    }

    @Test
    fun withServerDataTheServersWorldReplacesTheClientsOwnPacks() {
        JarDataCache.rebuildWith(roots())
        ServerDataView.set(serverOverlay())
        JarDataCache.rebuildForDataView()

        val moves = JarDataCache.getCachedMoves()
        assertEquals(listOf("bite"), moves["fakemon"]?.tutor)
        assertEquals(listOf("splash"), moves["fakemon"]?.egg)
        assertNull(moves["clientmon"], "a datapack only this client has must not leak into the server's data")
        assertNotNull(JarDataCache.getCachedSpawns()["fakemon"])
        assertNull(JarDataCache.getCachedSpawns()["clientmon"])
        assertEquals(45, JarDataCache.getCachedTraits()["fakemon"]?.catchRate)
        assertEquals("Fake Pack", JarDataCache.getCachedSpeciesProvenance()["fakemon"])
    }

    @Test
    fun legacyMovesInASpeciesFileAreKeptNotDropped() {
        val overlay = serverOverlay()
        val species = mapOf("pack:species/legacymon.json" to json("""{"name":"Legacymon","moves":["1:tackle","legacy:toxic","legacy:swagger","tm:rest"]}"""))
        ServerDataView.set(ServerOverlay(overlay.hash, overlay.categories + ("species" to species), overlay.serverMods, overlay.origins))
        JarDataCache.rebuildWith(roots())
        JarDataCache.rebuildForDataView()

        val moves = JarDataCache.getCachedMoves()["legacymon"]
        assertEquals(listOf("toxic", "swagger"), moves?.legacy)
        assertEquals(listOf("rest"), moves?.tm)
    }

    @Test
    fun onlyModsTheServerConfirmedKeepTheirLocalFiles() {
        JarDataCache.rebuildWith(roots())
        ServerDataView.set(serverOverlay())
        JarDataCache.rebuildForDataView()

        assertContains(JarDataCache.getCachedMoves(), "goodmon")
        assertFalse("badmon" in JarDataCache.getCachedMoves(), "a mod the server does not have must not contribute")
    }

    @Test
    fun leavingTheServerRestoresTheClientsOwnData() {
        JarDataCache.rebuildWith(roots())
        ServerDataView.set(serverOverlay())
        JarDataCache.rebuildForDataView()
        ServerDataView.set(null)
        JarDataCache.rebuildForDataView()

        assertContains(JarDataCache.getCachedMoves(), "clientmon")
        assertContains(JarDataCache.getCachedMoves(), "badmon")
        assertFalse("fakemon" in JarDataCache.getCachedMoves())
        assertContains(JarDataCache.getCachedSpawns(), "clientmon")
    }

    @Test
    fun theViewReportsWhatItMirrors() {
        assertFalse(ServerDataView.isActive())
        assertFalse(ServerDataView.mirrors("species"))
        assertTrue(ServerDataView.modAllowed("anything"))
        ServerDataView.set(serverOverlay())
        assertTrue(ServerDataView.mirrors("species"))
        assertTrue(ServerDataView.mirrors("spawn_pool_world"))
        assertFalse(ServerDataView.mirrors("fossils"), "fossils, marks and TMs are not mirrored in this version")
        assertTrue(ServerDataView.modAllowed("goodmod"))
        assertFalse(ServerDataView.modAllowed("badmod"))
        assertEquals("Fake Pack", ServerDataView.origin("pack:species/fakemon.json"))
    }
}
