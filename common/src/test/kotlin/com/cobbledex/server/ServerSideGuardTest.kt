package com.cobbledex.server

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The sync's server half also runs on dedicated servers, where no Minecraft client class exists. A
 * stray import of one, or of any CobbleDex class that pulls the client in, would only show up there as a
 * NoClassDefFoundError at startup - so the source is checked directly.
 */
class ServerSideGuardTest {

    private val serverSafeDirs = listOf(
        "src/main/kotlin/com/cobbledex/server",
        "src/main/kotlin/com/cobbledex/sync",
    )

    /** CobbleDex packages whose classes are safe on a server. Everything else in com.cobbledex touches the client. */
    private val allowedOwnPackages = listOf("com.cobbledex.server", "com.cobbledex.sync", "com.cobbledex.platform")

    private fun File.codeLines(): List<String> =
        readLines().filterNot { val t = it.trim(); t.startsWith("*") || t.startsWith("//") || t.startsWith("/*") }

    private fun sources(): List<File> = serverSafeDirs.flatMap { dir ->
        File(dir).walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    @Test
    fun theGuardActuallyFindsTheFiles() {
        assertTrue(sources().size >= 6, "found ${sources().size} server-safe sources; the paths are probably wrong")
    }

    @Test
    fun noServerSafeFileMentionsAMinecraftClientClass() {
        val offenders = sources().filter { f -> f.codeLines().any { "net.minecraft.client" in it || "com.mojang.blaze3d" in it } }
        assertTrue(offenders.isEmpty(), "client classes referenced from: ${offenders.map { it.name }}")
    }

    @Test
    fun noServerSafeFileImportsARestOfCobbleDexClass() {
        val offenders = sources().flatMap { f ->
            f.codeLines().filter { it.startsWith("import com.cobbledex.") }
                .filter { line -> allowedOwnPackages.none { line.removePrefix("import ").startsWith("$it.") } }
                .map { "${f.name}: $it" }
        }
        assertTrue(offenders.isEmpty(), "server-safe code imports client-side CobbleDex classes: $offenders")
    }

    /** The loader classes that run on a dedicated server. They live in other modules, next to `common`. */
    private val loaderServerFiles = listOf(
        "../fabric/src/main/kotlin/com/cobbledex/fabric/CobbleDexFabric.kt",
        "../neoforge/src/main/kotlin/com/cobbledex/neoforge/CobbleDexNeoForgeServer.kt",
    )

    @Test
    fun theLoaderEntrypointsThatRunOnServersAreFoundAndStayClear() {
        for (path in loaderServerFiles) {
            val f = File(path)
            assertTrue(f.isFile, "expected $path to exist")
            val bad = f.codeLines().filter { "net.minecraft.client" in it || "com.mojang.blaze3d" in it || "ClientPlayNetworking" in it }
            assertTrue(bad.isEmpty(), "$path touches the client: $bad")
        }
    }

    @Test
    fun noServerSafeFileUsesCobbleDexModOrItsLogger() {
        val offenders = sources().filter { f -> f.codeLines().any { "CobbleDexMod" in it || "DebugLog" in it } }
        assertTrue(offenders.isEmpty(), "use a plain slf4j logger instead: ${offenders.map { it.name }}")
    }
}
