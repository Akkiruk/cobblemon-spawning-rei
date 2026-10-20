package com.cobbledex.server

/**
 * The name a pack is shown under in "Added by ...". A resource pack's id can be a server file path
 * (`openloader/C:\servers\main\config\openloader\packs\Fakemon.zip`), which must not leave the server:
 * only the last segment, without its extension, is ever sent.
 */
object PackNames {
    fun display(packId: String, installedMods: Collection<String>): String? {
        val asMod = packId.removePrefix("mod/")
        if (asMod in installedMods) return asMod

        val segment = packId.substringAfterLast('/').substringAfterLast('\\').trim()
        val name = segment.removeSuffix(".zip").removeSuffix(".jar").trim()
        return name.ifEmpty { null }
    }
}
