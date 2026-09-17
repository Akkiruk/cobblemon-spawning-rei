package com.cobbledex

import net.minecraft.network.chat.Component
import net.minecraft.server.packs.PackLocationInfo
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.PathPackResources
import net.minecraft.server.packs.repository.PackSource
import net.minecraft.server.packs.resources.MultiPackResourceManager
import java.nio.file.Files
import java.nio.file.Path
import java.util.Optional
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the [MultiPackResourceManager] behaviour [LocalDataSource] depends on.
 *
 * These are assumptions about someone else's code, and both of them fail silently rather than
 * loudly if they're wrong - which is exactly why they're asserted here instead of reasoned about.
 */
class ResourceManagerScanTest {

    private val tempRoots = mutableListOf<Path>()

    @AfterTest
    fun cleanUp() {
        for (root in tempRoots) {
            root.toFile().deleteRecursively()
        }
        tempRoots.clear()
    }

    private fun pack(name: String, files: Map<String, String>): PathPackResources {
        val root = Files.createTempDirectory("cobbledex-$name")
        tempRoots.add(root)
        for ((relativePath, contents) in files) {
            val file = root.resolve(relativePath)
            file.parent.createDirectories()
            file.writeText(contents)
        }
        val info = PackLocationInfo(name, Component.literal(name), PackSource.BUILT_IN, Optional.empty())
        return PathPackResources(info, root)
    }

    private fun managerOf(vararg packs: PathPackResources) =
        MultiPackResourceManager(PackType.SERVER_DATA, packs.toList())

    /**
     * The one that would corrupt data if it went the other way.
     *
     * `species` and `species_additions` are siblings whose names share a prefix, and they mean
     * opposite things - an addition patches an existing species and carries a `target` instead of a
     * `name`. If the listing matched on a raw string prefix rather than a directory, every addition
     * would be handed to the parser as though it were a base species declaration, and would be
     * dropped or misfiled with nothing thrown and nothing logged.
     */
    @Test
    fun listingADirectoryDoesNotLeakSiblingsSharingItsNamePrefix() {
        val manager = managerOf(
            pack(
                "base",
                mapOf(
                    "data/test/species/bulbasaur.json" to "{}",
                    "data/test/species_additions/eevee.json" to "{}",
                ),
            ),
        )

        val found = manager.listResources("species") { it.path.endsWith(".json") }

        assertEquals(listOf("species/bulbasaur.json"), found.keys.map { it.path }.sorted())
    }

    /**
     * That a later pack shadows an earlier one, and which end of a resource *stack* the winner sits
     * at - [LocalDataSource] takes the winner straight from `listResources`, so if that returned the
     * shadowed copy instead, an override would silently apply the wrong chart.
     */
    @Test
    fun laterPackWinsAndSitsAtTheEndOfTheStack() {
        val subPath = "mega_showdown/showdown/typecharts"
        val manager = managerOf(
            pack("base", mapOf("data/test/$subPath/water.js" to "base")),
            pack("override", mapOf("data/test/$subPath/water.js" to "override")),
        )

        val winners = manager.listResources(subPath) { it.path.endsWith(".js") }
        assertEquals(1, winners.size)
        val winner = winners.values.single()
        assertEquals("override", winner.openAsReader().use { it.readText() })
        assertEquals("override", winner.sourcePackId())

        val stack = manager.listResourceStacks(subPath) { it.path.endsWith(".js") }.values.single()
        assertEquals(listOf("base", "override"), stack.map { it.sourcePackId() })
    }

    /** Namespaces are enumerated for us - the old scan walked `data/<ns>/` itself to do this. */
    @Test
    fun listingSpansEveryNamespaceInOneCall() {
        val manager = managerOf(
            pack(
                "multi",
                mapOf(
                    "data/alpha/marks/one.json" to "{}",
                    "data/beta/marks/two.json" to "{}",
                ),
            ),
        )

        val found = manager.listResources("marks") { it.path.endsWith(".json") }

        assertEquals(
            listOf("alpha:marks/one.json", "beta:marks/two.json"),
            found.keys.map { it.toString() }.sorted(),
        )
    }
}
