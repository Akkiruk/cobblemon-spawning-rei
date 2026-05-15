package com.cobbledex

import com.cobbledex.platform.PlatformHelper
import com.google.gson.GsonBuilder
import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.ResourceLocation
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

object PokemonSpriteAtlas {
    private const val ATLAS_VERSION = 2
    private const val SPRITE_SIZE = 64
    private const val ATLAS_COLUMNS = 32
    private const val CACHE_DIR = "cobbledex-sprites"
    private const val SPRITES_DIR = "sprites"
    private const val ATLAS_FILE = "pokemon_atlas.png"
    private const val MANIFEST_FILE = "pokemon_atlas.json"

    private val gson = GsonBuilder().setPrettyPrinting().create()

    data class SpriteKey(val species: String, val aspects: Set<String> = emptySet()) {
        val id: String = buildString {
            append(SpeciesNameNormalizer.normalize(species))
            val normalizedAspects = normalizeAspects(aspects)
            if (normalizedAspects.isNotEmpty()) {
                append("__")
                append(normalizedAspects.joinToString("_"))
            }
        }
    }

    data class ResolvedSpriteKey(
        val key: SpriteKey,
        val renderSpecies: String,
        val renderAspects: Set<String>,
    )

    private data class SpriteEntry(
        val id: String,
        val species: String,
        val aspects: List<String>,
        val x: Int,
        val y: Int,
        val width: Int = SPRITE_SIZE,
        val height: Int = SPRITE_SIZE,
    )

    private data class Manifest(
        val version: Int,
        val spriteSize: Int,
        val atlas: String,
        val width: Int,
        val height: Int,
        val entries: List<SpriteEntry>,
    )

    private data class LoadedAtlas(
        val textureId: ResourceLocation,
        val width: Int,
        val height: Int,
        val entriesById: Map<String, SpriteEntry>,
    )

    data class BuildResult(
        val atlasPath: Path,
        val manifestPath: Path,
        val requested: Int,
        val captured: Int,
        val failed: Int,
    )

    @Volatile private var loadedAtlas: LoadedAtlas? = null
    @Volatile private var loadAttempted = false
    @Volatile private var buildInProgress = false
    @Volatile private var autoBuildDataVersion = -1L

    fun resolve(species: String, explicitAspects: Set<String> = emptySet()): ResolvedSpriteKey {
        val normalized = SpeciesNameNormalizer.normalize(species)
        val info = SpawnDataIndex.getSpeciesInfo(normalized)
        val decomp = SpeciesNameNormalizer.decomposeFormSpecies(normalized)

        val renderSpecies = info?.baseSpeciesName
            ?.let(SpeciesNameNormalizer::normalize)
            ?: decomp.baseName

        val aspects = normalizeAspects(
            explicitAspects.ifEmpty {
                info?.formAspects?.ifEmpty { decomp.cobblemonAspects } ?: decomp.cobblemonAspects
            }
        )

        return ResolvedSpriteKey(SpriteKey(renderSpecies, aspects), renderSpecies, aspects)
    }

    fun renderIfAvailable(
        graphics: GuiGraphics,
        species: String,
        aspects: Set<String>,
        x: Int,
        y: Int,
        size: Int,
    ): Boolean {
        val atlas = getLoadedAtlas() ?: return false
        val resolved = resolve(species, aspects)
        val entry = atlas.entriesById[resolved.key.id] ?: return false
        graphics.blit(
            atlas.textureId,
            x,
            y,
            size,
            size,
            entry.x.toFloat(),
            entry.y.toFloat(),
            entry.width,
            entry.height,
            atlas.width,
            atlas.height,
        )
        return true
    }

    fun reload(): Boolean {
        loadAttempted = false
        loadedAtlas = null
        return getLoadedAtlas() != null
    }

    fun ensureAvailableForCurrentData() {
        if (!SpawnDataIndex.hasData() || buildInProgress) return

        val currentVersion = SpawnDataIndex.dataVersion
        if (autoBuildDataVersion == currentVersion) return

        if (getLoadedAtlas() != null) {
            autoBuildDataVersion = currentVersion
            return
        }

        autoBuildDataVersion = currentVersion
        buildAtlas { }
    }

    fun buildAtlas(sender: DiagnosticService.MessageSender): Int {
        if (buildInProgress) {
            sender.send("§eCobbleDex sprite atlas build is already running.")
            return 0
        }
        if (!SpawnDataIndex.hasData()) {
            sender.send(tr("cobbledex-rei-emi-jei.cmd.no_data_short"))
            return 0
        }

        buildInProgress = true
        sender.send("§7Building CobbleDex Pokemon sprite atlas...")
        Minecraft.getInstance().execute {
            try {
                val result = buildAtlasNow(sender)
                sender.send("§aSprite atlas ready: ${result.captured}/${result.requested} captured, ${result.failed} failed")
                sender.send("§7${result.atlasPath.toAbsolutePath()}")
                reload()
            } catch (t: Throwable) {
                sender.send("§cSprite atlas build failed: ${t.message ?: t.javaClass.simpleName}")
                DebugLog.warn("Sprite atlas build failed: ${t.message}")
                t.printStackTrace()
            } finally {
                buildInProgress = false
                IconCapture.cleanup()
            }
        }
        return 1
    }

    private fun buildAtlasNow(sender: DiagnosticService.MessageSender): BuildResult {
        val keys = collectSpriteKeys()
        val outputDir = cacheDir()
        val spriteDir = outputDir.resolve(SPRITES_DIR)
        Files.createDirectories(outputDir)
        Files.createDirectories(spriteDir)

        IconCapture.init()
        val images = linkedMapOf<ResolvedSpriteKey, java.awt.image.BufferedImage>()
        var failed = 0
        keys.forEachIndexed { index, resolved ->
            val png = IconCapture.captureSpeciesToPng(resolved.renderSpecies, resolved.renderAspects, SPRITE_SIZE)
            if (png != null) {
                val image = ImageIO.read(png.inputStream())
                images[resolved] = image
                ImageIO.write(image, "PNG", spriteDir.resolve("${resolved.key.id}.png").toFile())
            } else {
                failed++
            }
            val completed = index + 1
            if (completed == keys.size || completed % 50 == 0) {
                sender.send("§7Sprites: $completed/${keys.size}")
            }
        }

        val rows = ((images.size + ATLAS_COLUMNS - 1) / ATLAS_COLUMNS).coerceAtLeast(1)
        val atlasWidth = ATLAS_COLUMNS * SPRITE_SIZE
        val atlasHeight = rows * SPRITE_SIZE
        val atlasImage = java.awt.image.BufferedImage(atlasWidth, atlasHeight, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        val graphics = atlasImage.createGraphics()
        val entries = mutableListOf<SpriteEntry>()
        images.entries.forEachIndexed { index, (resolved, image) ->
            val x = (index % ATLAS_COLUMNS) * SPRITE_SIZE
            val y = (index / ATLAS_COLUMNS) * SPRITE_SIZE
            graphics.drawImage(image, x, y, SPRITE_SIZE, SPRITE_SIZE, null)
            entries.add(
                SpriteEntry(
                    id = resolved.key.id,
                    species = resolved.renderSpecies,
                    aspects = resolved.renderAspects.sorted(),
                    x = x,
                    y = y,
                )
            )
        }
        graphics.dispose()

        val atlasPath = outputDir.resolve(ATLAS_FILE)
        val manifestPath = outputDir.resolve(MANIFEST_FILE)
        ImageIO.write(atlasImage, "PNG", atlasPath.toFile())
        Files.writeString(
            manifestPath,
            gson.toJson(Manifest(ATLAS_VERSION, SPRITE_SIZE, ATLAS_FILE, atlasWidth, atlasHeight, entries))
        )

        return BuildResult(atlasPath, manifestPath, keys.size, images.size, failed)
    }

    private fun collectSpriteKeys(): List<ResolvedSpriteKey> {
        val queries = SpawnDataIndex.currentQueries()
        val names = SpawnDataIndex.allSpeciesNames.ifEmpty { SpawnDataIndex.speciesInfo.keys.sorted() }
        return names.asSequence()
            .map(SpeciesNameNormalizer::normalize)
            .filter(queries::shouldSurfaceSpecies)
            .map { resolve(it) }
            .distinctBy { it.key.id }
            .sortedBy { it.key.id }
            .toList()
    }

    private fun getLoadedAtlas(): LoadedAtlas? {
        loadedAtlas?.let { return it }
        if (loadAttempted) return null
        loadAttempted = true
        return tryLoadAtlas().also { loadedAtlas = it }
    }

    private fun tryLoadAtlas(): LoadedAtlas? {
        val dir = cacheDir()
        val manifestPath = dir.resolve(MANIFEST_FILE)
        val atlasPath = dir.resolve(ATLAS_FILE)
        if (!Files.exists(manifestPath) || !Files.exists(atlasPath)) return null

        return try {
            val manifest = gson.fromJson(Files.readString(manifestPath), Manifest::class.java) ?: return null
            if (manifest.version != ATLAS_VERSION || manifest.spriteSize != SPRITE_SIZE) return null

            val image = Files.newInputStream(atlasPath).use { NativeImage.read(it) }
            val texture = DynamicTexture(image)
            val textureId = ResourceLocation.fromNamespaceAndPath(CobbleDexMod.MOD_ID, "pokemon_sprite_atlas")
            Minecraft.getInstance().textureManager.register(textureId, texture)
            val entries = manifest.entries.associateBy { it.id }
            DebugLog.info("Loaded Pokemon sprite atlas (${entries.size} sprites, ${manifest.width}x${manifest.height})")
            LoadedAtlas(textureId, manifest.width, manifest.height, entries)
        } catch (e: Exception) {
            DebugLog.warn("Pokemon sprite atlas load failed: ${e.message}")
            null
        }
    }

    private fun cacheDir(): Path = try {
        PlatformHelper.getGameDir().resolve(CACHE_DIR)
    } catch (_: Exception) {
        java.nio.file.Paths.get(CACHE_DIR)
    }

    private fun normalizeAspects(aspects: Set<String>): Set<String> = aspects.asSequence()
        .map { it.lowercase().replace(Regex("[^a-z0-9_=.-]"), "") }
        .filter { it.isNotBlank() }
        .toSortedSet()
}