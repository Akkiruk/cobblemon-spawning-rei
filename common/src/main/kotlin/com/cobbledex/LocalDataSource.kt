package com.cobbledex

import net.minecraft.client.Minecraft
import net.minecraft.server.MinecraftServer
import net.minecraft.server.packs.resources.ResourceManager

/**
 * Reads datapack files through the game's own resolved [ResourceManager] instead of guessing which
 * folders datapacks live in.
 *
 * [JarDataCache]'s startup scan walks mod jars plus `<gameDir>/datapacks/`, which is every place a
 * pack can live *except* the two that matter most in practice: a world's own
 * `saves/<world>/datapacks/`, and wherever a loader mod (OpenLoader, Globalpacks) keeps the packs it
 * serves. Neither is a folder this could simply add - OpenLoader's location is its own config's
 * business, and the per-world one isn't even decided until a world is picked, long after the startup
 * scan has run.
 *
 * The server that loaded the world already resolved all of them into one manager, in the order it
 * actually applies them. Asking it is therefore both complete and correctly ordered without this
 * having to know where any individual pack came from - including packs served by loader mods that
 * don't exist yet.
 *
 * Only available while this client hosts the world (singleplayer/LAN). On a remote server the packs
 * are on that server's disk and nothing here can reach them; callers keep the folder scan for that.
 */
object LocalDataSource {

    /** One datapack file: its base name, the pack that supplied it, and its contents. */
    data class Entry(val location: String, val fileName: String, val sourcePackId: String, val text: String)

    /** The server this client is hosting, or null when connected to someone else's. */
    fun localServer(): MinecraftServer? = try {
        Minecraft.getInstance().singleplayerServer
    } catch (_: Exception) {
        null
    }

    /**
     * Runs [action] against the hosted world's data [ResourceManager], or does nothing when this
     * client isn't hosting one.
     *
     * Dispatched onto the server thread, which is what owns those packs - and left there rather than
     * copied off it, because a [ResourceManager] stops being safe to read the moment the world
     * unloads and closes its packs. Anything read here must therefore be small enough to not be felt
     * as a tick hitch; a whole-species rescan would need a different approach.
     */
    fun withLocalResourceManager(action: (ResourceManager) -> Unit) {
        val server = localServer() ?: return
        try {
            server.execute {
                try {
                    action(server.resourceManager)
                } catch (e: Exception) {
                    DebugLog.warn("Local datapack read failed: ${e.message}")
                }
            }
        } catch (e: Exception) {
            DebugLog.warn("Local datapack read could not be scheduled: ${e.message}")
        }
    }

    /**
     * Every file under `data/<namespace>/[subPath]` ending in [extension] that the loaded packs
     * supply, narrowed to the winning copy of each path - [ResourceManager] resolves pack overrides
     * the same way the server does, so a pack shadowing another is reflected here without this
     * needing a precedence rule of its own.
     *
     * Sorted by location so that two *different* paths contributing the same file name (the same
     * file under two namespaces, say) are always resolved in the same order rather than in whatever
     * order the map happened to iterate.
     *
     * Read eagerly: the returned text is a copy, so it stays valid after the packs close.
     */
    fun readText(resourceManager: ResourceManager, subPath: String, extension: String): List<Entry> {
        val entries = mutableListOf<Entry>()
        val found = resourceManager.listResources(subPath) { it.path.endsWith(extension) }
        for ((location, resource) in found) {
            try {
                val text = resource.openAsReader().use { it.readText() }
                val fileName = location.path.substringAfterLast('/').removeSuffix(extension)
                entries.add(Entry(location.toString(), fileName, resource.sourcePackId(), text))
            } catch (_: Exception) {}
        }
        entries.sortBy { it.location }
        return entries
    }
}
