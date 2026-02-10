package com.cobblemonrei

import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import java.util.concurrent.ConcurrentHashMap

object PokemonSpriteManager {

    private const val TEXTURE_PATH = "textures/pokemon"
    private val spriteCache = ConcurrentHashMap<String, ResourceLocation?>()

    // Map species name → filename by building an index from our bundled sprites
    private val speciesFileIndex: Map<String, String> by lazy { buildFileIndex() }

    private fun buildFileIndex(): Map<String, String> {
        val index = mutableMapOf<String, String>()
        try {
            val resourceManager = Minecraft.getInstance().resourceManager
            // List all PNGs in our textures/pokemon directory
            val locations = resourceManager.listResources("$TEXTURE_PATH") { it.path.endsWith(".png") }
            for ((loc, _) in locations) {
                if (loc.namespace != CobblemonSpawningMod.MOD_ID) continue
                val filename = loc.path.substringAfterLast("/").removeSuffix(".png")
                // Format: 0025_pikachu → species = pikachu
                val species = filename.substringAfter("_").lowercase()
                if (species.isNotEmpty()) {
                    index[species] = filename
                }
            }
            DebugLog.debug("Indexed ${index.size} sprite files")
        } catch (e: Exception) {
            DebugLog.warn("Failed to index sprites: ${e.message}")
        }
        return index
    }

    fun getSpriteLocation(species: String): ResourceLocation? {
        val cacheKey = species.lowercase()
        return spriteCache.getOrPut(cacheKey) {
            findSprite(cacheKey)
        }
    }

    private fun findSprite(species: String): ResourceLocation? {
        val normalized = species.lowercase()
        // Direct match
        speciesFileIndex[normalized]?.let {
            return makeResourceLocation(it)
        }
        // Try stripping hyphens/spaces
        val stripped = normalized.replace("-", "").replace(" ", "")
        speciesFileIndex[stripped]?.let {
            return makeResourceLocation(it)
        }
        // Try underscore variants
        val underscored = normalized.replace("-", "_").replace(" ", "_")
        speciesFileIndex[underscored]?.let {
            return makeResourceLocation(it)
        }
        return null
    }

    private fun makeResourceLocation(filename: String): ResourceLocation {
        return ResourceLocation.fromNamespaceAndPath(
            CobblemonSpawningMod.MOD_ID,
            "$TEXTURE_PATH/$filename.png"
        )
    }

    fun hasSpriteFor(species: String): Boolean {
        return getSpriteLocation(species) != null
    }

    // Pre-warm: call during data load to ensure index is built
    fun initialize() {
        val count = speciesFileIndex.size
        DebugLog.debug("Sprite manager ready with $count sprites")
    }
}
