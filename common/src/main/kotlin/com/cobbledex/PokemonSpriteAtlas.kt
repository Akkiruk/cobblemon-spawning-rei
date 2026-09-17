package com.cobbledex

import com.cobbledex.platform.PlatformHelper
import com.google.gson.GsonBuilder
import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.ResourceLocation
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

object PokemonSpriteAtlas {
    // Bump whenever a change would make an already-cached atlas render
    // something wrong (not just add new content) - a mismatched version
    // number is what makes ensureAtlas() below treat an existing player's
    // on-disk cache as stale and silently rebuild it once, instead of them
    // being stuck with an old incorrect render (e.g. Fungalith's Substitute-
    // doll bug) until they think to run /cobbledex sprites build themselves.
    //
    // BUMPING THIS ALSO INVALIDATES THE SHIPPED ATLAS (the one committed at
    // common/src/main/resources/assets/cobbledex-rei-emi-jei/sprites/) - it's checked against this
    // same constant in tryLoadBundledAtlas/loadAtlasFromStreams. Forgetting to re-bake it is exactly
    // what happened 2026-07-07 to 2026-09-16: this got bumped for the Fungalith fix, the shipped
    // atlas didn't, and every release since silently shipped a 3.7MB atlas the mod refused to ever
    // load - no crash, no log line, two months unnoticed. ShippedAtlasTest now fails the build if
    // this happens again, but the manual half is still real. Whenever this or SPRITE_SIZE changes:
    //   1. Launch a profile with a broad, canonical-only roster (megas/gmax/regional forms, not a
    //      fakemon pack - see the shipped manifest's own species list for the current baseline).
    //   2. Run /cobbledex sprites build. Confirm the bake reflects whatever prompted the bump
    //      (e.g. actually check a Fungalith render) before trusting it.
    //   3. Copy that profile's cobbledex-sprites/pokemon_atlas.png + .json over the ones in
    //      common/src/main/resources/assets/cobbledex-rei-emi-jei/sprites/.
    //   4. Run :common:test - ShippedAtlasTest passing is the actual verification this is done.
    //   5. Commit the constant bump and the two regenerated files together. Never merge one
    //      without the other.
    //
    // internal, not private: ShippedAtlasTest reads this directly so the check and the value it's
    // checking against can never drift apart the way the shipped file and this constant already
    // did once.
    internal const val ATLAS_VERSION = 3
    // internal for the same reason as ATLAS_VERSION above - the real loader checks both together
    // (see tryLoadBundledAtlas/loadAtlasFromStreams), so the test checking the shipped file has to
    // check both too, against these exact values rather than a copy of them.
    internal const val SPRITE_SIZE = 64
    private const val ATLAS_COLUMNS = 32
    private const val CACHE_DIR = "cobbledex-sprites"
    private const val SPRITES_DIR = "sprites"
    private const val ATLAS_FILE = "pokemon_atlas.png"
    private const val MANIFEST_FILE = "pokemon_atlas.json"
    private val bundledAtlasPath = ResourceLocation.fromNamespaceAndPath(CobbleDexMod.MOD_ID, "sprites/$ATLAS_FILE")
    private val bundledManifestPath = ResourceLocation.fromNamespaceAndPath(CobbleDexMod.MOD_ID, "sprites/$MANIFEST_FILE")

    /** Where the shipped manifest sits on the classpath - same path a plain JUnit test can read. */
    internal const val SHIPPED_MANIFEST_RESOURCE = "assets/${CobbleDexMod.MOD_ID}/sprites/pokemon_atlas.json"

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

    internal data class SpriteEntry(
        val id: String,
        val species: String,
        val aspects: List<String>,
        val x: Int,
        val y: Int,
        val width: Int = SPRITE_SIZE,
        val height: Int = SPRITE_SIZE,
    )

    internal data class Manifest(
        val version: Int,
        val spriteSize: Int,
        val atlas: String,
        val width: Int,
        val height: Int,
        val entries: List<SpriteEntry>,
    )

    /**
     * Parses the shipped manifest straight off the classpath - no [Minecraft]/`ResourceManager`
     * involved, so this works from a plain JUnit test, not just in-game.
     *
     * This is deliberately the same [gson] and [Manifest] the real bundled-atlas load path uses
     * ([tryLoadBundledAtlas]), not a second parser of its own - the point is to check the actual
     * shipped file the game will actually try to load, not a hand-rolled approximation of it that
     * could itself drift from what `loadAtlasFromStreams` expects.
     */
    internal fun readShippedManifestFromClasspath(): Manifest {
        val stream = PokemonSpriteAtlas::class.java.classLoader.getResourceAsStream(SHIPPED_MANIFEST_RESOURCE)
            ?: error("Shipped atlas manifest not found on the classpath: $SHIPPED_MANIFEST_RESOURCE")
        return stream.bufferedReader().use { gson.fromJson(it, Manifest::class.java) }
    }

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

    @Volatile private var loadedAtlases: List<LoadedAtlas>? = null
    @Volatile private var loadAttempted = false
    @Volatile private var buildInProgress = false
    private val ensureAttempted = java.util.concurrent.atomic.AtomicBoolean(false)

    // Called once per session (see CobbleDexMod.tickClient) right after
    // spawn/species data finishes loading, so players never have to know
    // /cobbledex sprites build exists just to see correct icons/renders. If
    // a cached atlas already exists and matches ATLAS_VERSION it's left
    // alone (instant, no rebuild); otherwise a fresh one is built silently
    // in the background - this is the only path that makes an ATLAS_VERSION
    // bump (e.g. after a render-affecting fix) actually reach existing
    // players instead of them staying stuck on their old cached PNG.
    fun ensureAtlas() {
        if (!ensureAttempted.compareAndSet(false, true)) return
        // Checked directly against the cache (not getLoadedAtlas/tryLoadAtlas,
        // which would silently accept the jar-bundled default atlas as "good
        // enough") - the bundled atlas is a fixed snapshot baked at build
        // time that can't reflect this modpack's own fakemons/fixes, so
        // treating it as sufficient here would mean the auto-build never
        // fires and players stay on a wrong/incomplete atlas forever.
        // Emptiness matters as much as absence: a bake that failed partway still writes a valid
        // manifest, and accepting that as "cached" both suppressed this rebuild and (before the
        // atlases were chained) hid every shipped sprite behind it. Nothing is assigned to
        // loadedAtlases here either - getLoadedAtlases builds the shipped-first chain itself.
        val cached = tryLoadCacheAtlas()
        if (cached != null && cached.entriesById.isNotEmpty()) return
        if (cached != null) {
            DebugLog.warn("Cached Pokemon sprite atlas has no sprites in it - rebuilding (shipped sprites are used meanwhile)")
        }
        DebugLog.info("No up-to-date cached Pokemon sprite atlas found - building automatically in the background")
        buildAtlas { msg -> DebugLog.info(msg) }
    }

    /** Allows ensureAtlas() to check again after reconnecting (e.g. to a different server/pack setup). */
    fun resetEnsureAttempt() {
        ensureAttempted.set(false)
    }

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

        // Most species' bedrock resolvers have a model variation that matches
        // with no gender aspect at all, so this normally doesn't matter - but
        // some (e.g. Lively Mons' Fungalith) define ONLY "male"/"female"
        // variations with no genderless fallback. Requesting a render with
        // neither aspect then matches nothing, and Cobblemon silently falls
        // back to the "Substitute" placeholder doll instead of the species'
        // own model. Default to a gender aspect whenever the species isn't
        // genderless (maleRatio == -1), same convention Cobblemon's own box/
        // party UI uses when showing a species with no live Pokemon instance
        // to pull an actual gender from. Kept OUT of the SpriteKey/id itself
        // (only affects the actual render call) so the atlas/website
        // filenames stay exactly as every other lookup already expects them -
        // this is purely "which pose Cobblemon renders", not a new identity.
        val renderAspects = if ("male" in aspects || "female" in aspects) {
            aspects
        } else {
            val maleRatio = info?.maleRatio
            if (maleRatio != null && maleRatio != -1f) {
                aspects + if (maleRatio == 0f) "female" else "male"
            } else aspects
        }

        return ResolvedSpriteKey(SpriteKey(renderSpecies, aspects), renderSpecies, renderAspects)
    }

    fun renderIfAvailable(
        graphics: GuiGraphics,
        species: String,
        aspects: Set<String>,
        x: Int,
        y: Int,
        size: Int,
    ): Boolean {
        val resolved = resolve(species, aspects)
        for (atlas in getLoadedAtlases()) {
            val entry = atlas.entriesById[resolved.key.id] ?: continue
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
        return false
    }

    fun reload(preferCache: Boolean = false): Boolean {
        loadAttempted = false
        loadedAtlases = null
        return getLoadedAtlases(preferCache).isNotEmpty()
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
                startAtlasCapture(sender)
            } catch (t: Throwable) {
                sender.send("§cSprite atlas build failed: ${t.message ?: t.javaClass.simpleName}")
                DebugLog.warn("Sprite atlas build failed: ${t.message}")
                t.printStackTrace()
                buildInProgress = false
                IconCapture.cleanup()
            }
        }
        return 1
    }

    // Each captureSpeciesToPng() call is a real off-screen 3D model render (Cobblemon's own
    // drawProfilePokemon into a 512x512 FBO) plus a full-frame CPU pixel scan for the content
    // bounds - genuinely expensive per species, not a cheap PNG copy. Doing every enabled species
    // (1000+ on a large modpack) back-to-back in one call, as this used to, was a real multi-second
    // render-thread freeze the first time a player ever loads the mod (or after an ATLAS_VERSION
    // bump ships) - the atlas *load* path already had a modest, unavoidable one-shot cost, but this
    // *build* path was unbounded. Spread across many ticks instead, same pattern as
    // CobbleDexJEIPlugin.continueReload().
    private const val SPECIES_PER_TICK = 4

    private fun startAtlasCapture(sender: DiagnosticService.MessageSender) {
        val keys = collectSpriteKeys()
        val outputDir = cacheDir()
        val spriteDir = outputDir.resolve(SPRITES_DIR)
        Files.createDirectories(outputDir)
        Files.createDirectories(spriteDir)

        IconCapture.init()
        val images = linkedMapOf<ResolvedSpriteKey, java.awt.image.BufferedImage>()
        var failed = 0
        captureJob = CaptureJob(
            keys = keys,
            outputSize = SPRITE_SIZE,
            sender = sender,
            onEach = { resolved, png ->
                if (png != null) {
                    val image = ImageIO.read(png.inputStream())
                    images[resolved] = image
                    ImageIO.write(image, "PNG", spriteDir.resolve("${resolved.key.id}.png").toFile())
                } else {
                    failed++
                }
            },
            onFinish = {
                // Writing a zero-sprite atlas is strictly worse than writing nothing: it replaces a
                // good cache with an empty one and, before the atlas chain existed, hid every
                // shipped sprite behind it. A bake that captured nothing has nothing to say.
                if (images.isEmpty()) {
                    sender.send("§cSprite atlas build FAILED: 0/${keys.size} captured - keeping the existing atlas.")
                    IconCapture.bindingFailure?.let {
                        sender.send("§cCause: $it")
                        sender.send("§eThis build of CobbleDex can't call the installed Cobblemon's model renderer.")
                    } ?: sender.send("§7No sprite could be rendered - see the log for the first failure.")
                } else {
                    val result = finishAtlasBuild(images, failed, outputDir)
                    val colour = if (result.failed > 0) "§e" else "§a"
                    sender.send("$colour Sprite atlas ready: ${result.captured}/${result.requested} captured, ${result.failed} failed".trimStart())
                    sender.send("§7${result.atlasPath.toAbsolutePath()}")
                    reload(preferCache = true)
                }
            },
        )
    }

    private fun finishAtlasBuild(
        images: Map<ResolvedSpriteKey, java.awt.image.BufferedImage>,
        failed: Int,
        outputDir: Path,
    ): BuildResult {
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

        return BuildResult(atlasPath, manifestPath, entries.size + failed, images.size, failed)
    }

    /** One capture-and-write step per species, plus what to do once every key is processed. */
    private class CaptureJob(
        val keys: List<ResolvedSpriteKey>,
        val outputSize: Int,
        val sender: DiagnosticService.MessageSender,
        val onEach: (ResolvedSpriteKey, ByteArray?) -> Unit,
        val onFinish: () -> Unit,
        val progressLabel: String = "Sprites",
    ) {
        var index = 0
    }

    @Volatile private var captureJob: CaptureJob? = null

    /**
     * Advances an in-progress sprite capture (atlas build or website export) by up to
     * [SPECIES_PER_TICK] species. Driven every client tick (see [CobbleDexMod.tickClient]); a no-op
     * call (nothing running) just checks a null reference.
     */
    fun continueCapture() {
        val job = captureJob ?: return
        var budget = SPECIES_PER_TICK
        while (budget > 0 && job.index < job.keys.size) {
            val resolved = job.keys[job.index]
            try {
                val png = IconCapture.captureSpeciesToPng(resolved.renderSpecies, resolved.renderAspects, job.outputSize)
                job.onEach(resolved, png)
            } catch (t: Throwable) {
                // Driven straight from the client tick with nothing above it to catch a stray
                // Error/Exception - unlike the old one-shot buildAtlasNow(), which ran inside a
                // Minecraft.execute { try { ... } catch (Throwable) } block. Without this, a single
                // bad species (or a Cobblemon API mismatch - see IconCapture.captureSpeciesToPng)
                // would crash the game instead of just failing this one capture job.
                job.sender.send("§cSprite capture aborted: ${t.javaClass.simpleName}: ${t.message}")
                DebugLog.warn("Sprite capture aborted at ${resolved.key.id}: ${t.javaClass.simpleName}: ${t.message}")
                t.printStackTrace()
                captureJob = null
                buildInProgress = false
                IconCapture.cleanup()
                return
            }
            job.index++
            budget--
            if (job.index == job.keys.size || job.index % 50 == 0) {
                job.sender.send("§7${job.progressLabel}: ${job.index}/${job.keys.size}")
            }
        }
        if (job.index >= job.keys.size) {
            captureJob = null
            try {
                job.onFinish()
            } catch (t: Throwable) {
                job.sender.send("§cSprite capture failed: ${t.message ?: t.javaClass.simpleName}")
                DebugLog.warn("Sprite capture finish failed: ${t.message}")
                t.printStackTrace()
            } finally {
                buildInProgress = false
                IconCapture.cleanup()
            }
        }
    }

    // For the companion Pokedex website: full-size individual PNGs (not the
    // small REI atlas) named exactly by SpriteKey.id, so an external pipeline
    // can match them against its own normalized species+aspects without any
    // coupling to this mod's internal file layout.
    fun exportWebsiteSprites(size: Int, sender: DiagnosticService.MessageSender): Int {
        if (buildInProgress) {
            sender.send("§eA CobbleDex sprite build/export is already running.")
            return 0
        }
        if (!SpawnDataIndex.hasData()) {
            sender.send(tr("cobbledex-rei-emi-jei.cmd.no_data_short"))
            return 0
        }

        buildInProgress = true
        sender.send("§7Exporting $size" + "x" + "$size Pokemon sprites for the website...")
        Minecraft.getInstance().execute {
            try {
                val keys = collectSpriteKeys()
                val outputDir = cacheDir().resolve("website-sprites")
                Files.createDirectories(outputDir)
                IconCapture.init()
                var captured = 0
                var failed = 0
                captureJob = CaptureJob(
                    keys = keys,
                    outputSize = size,
                    sender = sender,
                    onEach = { resolved, png ->
                        if (png != null) {
                            Files.write(outputDir.resolve("${resolved.key.id}.png"), png)
                            captured++
                        } else {
                            failed++
                        }
                    },
                    onFinish = {
                        sender.send("§aWebsite sprite export done: $captured/${keys.size} captured, $failed failed")
                        sender.send("§7${outputDir.toAbsolutePath()}")
                    },
                    progressLabel = "Exported",
                )
            } catch (t: Throwable) {
                sender.send("§cWebsite sprite export failed: ${t.message ?: t.javaClass.simpleName}")
                DebugLog.warn("Website sprite export failed: ${t.message}")
                t.printStackTrace()
                buildInProgress = false
                IconCapture.cleanup()
            }
        }
        return 1
    }

    /** How a given icon will actually be painted - the branches of [PokemonIconRenderer.render]. */
    enum class IconSource {
        /** Blitted from our own baked atlas. */
        ATLAS,

        /** Drawn as Cobblemon's `PokemonItem` - correct, but a 3D item model rather than our sprite. */
        ITEM_FALLBACK,

        /** Neither path can draw it, so the viewers show nothing for it. */
        NOT_DRAWN,
    }

    data class IconCoverage(
        val atlasLoaded: Boolean,
        val atlasEntries: Int,
        val bySource: Map<IconSource, List<String>>,
    )

    /**
     * How every species the viewers surface will be drawn right now.
     *
     * Deliberately asks the same two questions [PokemonIconRenderer.render] asks, in the same order,
     * so this can't report a coverage the panel doesn't actually match. Atlas membership is per
     * entry, not global - an atlas can be loaded and still miss a form baked after it was built.
     */
    fun iconCoverage(): IconCoverage {
        val atlases = getLoadedAtlases()
        val buckets = mutableMapOf<IconSource, MutableList<String>>()
        for (resolved in collectSpriteKeys()) {
            val source = when {
                atlases.any { it.entriesById.containsKey(resolved.key.id) } -> IconSource.ATLAS
                PokemonItemCache.canRender(resolved.renderSpecies, resolved.renderAspects) -> IconSource.ITEM_FALLBACK
                else -> IconSource.NOT_DRAWN
            }
            buckets.getOrPut(source) { mutableListOf() }.add(resolved.key.id)
        }
        val distinctSprites = atlases.flatMap { it.entriesById.keys }.toSet().size
        return IconCoverage(atlases.isNotEmpty(), distinctSprites, buckets)
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

    /**
     * Every atlas available, highest priority first - looked up in order, so a species missing from
     * one simply falls through to the next.
     *
     * This used to pick a single atlas and use it for everything, which meant any cache at all hid
     * the shipped one wholesale: a bake that failed but still wrote a valid manifest left a
     * zero-entry cache shadowing 1372 shipped sprites, and every icon fell back to an item model.
     * Chaining them removes that failure mode entirely - a thin or broken cache can now only fail to
     * *add* sprites, never take the shipped ones away.
     *
     * Shipped first: if we ship a sprite for a species, that sprite is what gets drawn. A local bake
     * covers what the shipped atlas can't know about - this pack's own fakemons and forms - rather
     * than replacing it. (A consequence worth knowing: rebaking locally can no longer override a
     * shipped sprite for a species we already ship, only fill gaps around it.)
     */
    private fun getLoadedAtlases(preferCache: Boolean = false): List<LoadedAtlas> {
        loadedAtlases?.let { return it }
        if (loadAttempted) return emptyList()
        loadAttempted = true

        val bundled = tryLoadBundledAtlas()
        // An empty cache is treated as absent everywhere: it can't contribute a sprite, and letting
        // it into the chain would only cost a pointless lookup on every icon.
        val cached = tryLoadCacheAtlas()?.takeIf { it.entriesById.isNotEmpty() }

        val chain = if (preferCache) listOfNotNull(cached, bundled) else listOfNotNull(bundled, cached)
        loadedAtlases = chain
        return chain
    }

    private fun tryLoadCacheAtlas(): LoadedAtlas? {
        val dir = cacheDir()
        val manifestPath = dir.resolve(MANIFEST_FILE)
        val atlasPath = dir.resolve(ATLAS_FILE)
        if (!Files.exists(manifestPath) || !Files.exists(atlasPath)) return null

        return try {
            Files.newInputStream(atlasPath).use { atlasStream ->
                loadAtlasFromStreams(
                    manifestJson = Files.readString(manifestPath),
                    atlasStream = atlasStream,
                    texturePath = "pokemon_sprite_atlas_cache",
                    sourceDescription = atlasPath.toAbsolutePath().toString(),
                )
            }
        } catch (e: Exception) {
            DebugLog.warn("Pokemon sprite atlas load failed: ${e.message}")
            null
        }
    }

    private fun tryLoadBundledAtlas(): LoadedAtlas? {
        val mc = Minecraft.getInstance()
        val manifestResource = mc.resourceManager.getResource(bundledManifestPath).orElse(null) ?: return null
        val atlasResource = mc.resourceManager.getResource(bundledAtlasPath).orElse(null) ?: return null

        return try {
            val manifestJson = manifestResource.open().bufferedReader().use { it.readText() }
            atlasResource.open().use { atlasStream ->
                loadAtlasFromStreams(
                    manifestJson = manifestJson,
                    atlasStream = atlasStream,
                    texturePath = "pokemon_sprite_atlas_bundled",
                    sourceDescription = "bundled atlas",
                )
            }
        } catch (e: Exception) {
            DebugLog.warn("Bundled Pokemon sprite atlas load failed: ${e.message}")
            null
        }
    }

    private fun loadAtlasFromStreams(
        manifestJson: String,
        atlasStream: InputStream,
        texturePath: String,
        sourceDescription: String,
    ): LoadedAtlas? {
        val manifest = gson.fromJson(manifestJson, Manifest::class.java) ?: return null
        if (manifest.version != ATLAS_VERSION || manifest.spriteSize != SPRITE_SIZE) {
            // This used to fail here with nothing logged at all - which is exactly how a stale
            // *shipped* atlas (manifest version 2, loader requiring 3) went unnoticed in every
            // release for two months: the bundled-atlas path silently returned null and the local
            // per-player cache quietly covered for it, so nothing ever looked broken. ShippedAtlasTest
            // is what actually stops that file from shipping stale again; this is the field-visible
            // half, for the day something bypasses that gate anyway.
            DebugLog.warn(
                "$sourceDescription rejected: manifest is version=${manifest.version}/spriteSize=" +
                    "${manifest.spriteSize}, loader requires version=$ATLAS_VERSION/spriteSize=$SPRITE_SIZE"
            )
            return null
        }

        val image = atlasStream.use { NativeImage.read(it) }
        val texture = DynamicTexture(image)
        val textureId = ResourceLocation.fromNamespaceAndPath(CobbleDexMod.MOD_ID, texturePath)
        Minecraft.getInstance().textureManager.register(textureId, texture)
        val entries = manifest.entries.associateBy { it.id }
        DebugLog.info("Loaded Pokemon sprite atlas from $sourceDescription (${entries.size} sprites, ${manifest.width}x${manifest.height})")
        return LoadedAtlas(textureId, manifest.width, manifest.height, entries)
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