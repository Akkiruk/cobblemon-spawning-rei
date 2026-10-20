package com.cobbledex

import com.cobbledex.sync.ServerOverlay
import com.cobbledex.sync.SyncProtocol
import com.google.gson.JsonObject

/**
 * The data a companion server supplied, as the loaders see it.
 *
 * While a view is active, [JarDataCache] reconstructs the server's own view of the mirrored folders: the
 * client's copies of the mods the server confirmed are identical, plus every file the server sent, and
 * nothing from this client's own `datapacks/` or `resourcepacks/` folders - those packs are not on the server.
 * With no view the loaders behave exactly as they always have.
 */
object ServerDataView {
    @Volatile
    private var overlay: ServerOverlay? = null

    fun set(value: ServerOverlay?) {
        overlay = value
    }

    fun isActive(): Boolean = overlay != null

    /** True when [category] is read from the server instead of this client's own packs. */
    fun mirrors(category: String): Boolean = overlay != null && category in SyncProtocol.CATEGORIES

    /** The server's files for [category], by `namespace:path` id. */
    fun files(category: String): Map<String, JsonObject> = overlay?.categories?.get(category).orEmpty()

    /** Where the server says a species file came from, for the "Added by" line. */
    fun origin(fileId: String): String? = overlay?.origins?.get(fileId)

    /** A mod whose files this client may use: any mod when no view is active, otherwise only ones the server matched. */
    fun modAllowed(modId: String?): Boolean {
        val o = overlay ?: return true
        return modId == null || modId in o.serverMods
    }
}
