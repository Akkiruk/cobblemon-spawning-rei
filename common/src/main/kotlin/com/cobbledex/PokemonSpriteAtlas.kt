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
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
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
    internal const val ATLAS_VERSION = 4
    // internal for the same reason as ATLAS_VERSION above - the real loader checks both together
    // (see tryLoadBundledAtlas/loadAtlasFromStreams), so the test checking the shipped file has to
    // check both too, against these exact values rather than a copy of them.
    internal const val SPRITE_SIZE = 64
    private const val ATLAS_COLUMNS = 32
    private const val CACHE_DIR = "cobbledex-sprites"
    /** Only ever held a loose PNG of every sprite that nothing read. Removed by the next full bake. */
    private const val LEGACY_SPRITES_DIR = "sprites"
    private const val CAPTURE_OWNER = "sprite-atlas"
    private const val ATLAS_FILE = "pokemon_atlas.png"
    private const val MANIFEST_FILE = "pokemon_atlas.json"
    private val bundledAtlasPath = ResourceLocation.fromNamespaceAndPath(CobbleDexMod.MOD_ID, "sprites/$ATLAS_FILE")
    private val bundledManifestPath = ResourceLocation.fromNamespaceAndPath(CobbleDexMod.MOD_ID, "sprites/$MANIFEST_FILE")

    /** Where the shipped manifest sits on the classpath - same path a plain JUnit test can read. */
    internal const val SHIPPED_MANIFEST_RESOURCE = "assets/${CobbleDexMod.MOD_ID}/sprites/pokemon_atlas.json"

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val ASPECT_JUNK = Regex("[^a-z0-9_=.-]")

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
        /** What the bake saw, see [installFingerprint]. Null in anything older. */
        val fingerprint: String? = null,
        /** Sprites that couldn't be captured, so a join doesn't keep retrying them. */
        val failed: List<String>? = null,
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

    // Called once per session (see CobbleDexMod.tickClient) right after spawn/species data finishes
    // loading, so players never have to know /cobbledex sprites build exists. Bakes everything when
    // the cache is missing, stale or was made for a different set of mods and packs, and just the
    // gaps when the world brought species the cache hasn't seen, like a server's own forms.
    //
    // Reads the manifest only. This used to load the whole atlas to find out if one existed, which
    // decoded a 2048x2816 PNG on the game thread on every world join and leave.
    fun ensureAtlas() {
        // A pack still applying (a server's, say) would change what gets baked partway through.
        if (Minecraft.getInstance().overlay != null) return
        if (!ensureAttempted.compareAndSet(false, true)) return

        val wanted = collectSpriteKeys().mapTo(mutableSetOf()) { it.key.id }
        val fingerprint = installFingerprint()
        val cached = readCacheManifest()
        val toBake = spritesToBake(cached, fingerprint, wanted)
        if (toBake.isEmpty()) return
        val partial = cacheStillApplies(cached, fingerprint)
        DebugLog.info(
            if (partial) "Baking ${toBake.size} Pokemon sprites the cached atlas doesn't have yet"
            else "No up-to-date cached Pokemon sprite atlas found - building automatically in the background"
        )
        startBuild({ msg -> DebugLog.info(msg) }, if (partial) toBake else null)
    }

    private fun cacheStillApplies(cached: Manifest?, fingerprint: String): Boolean =
        cached != null && cached.entries.isNotEmpty() && cached.fingerprint == fingerprint

    /** Ids the next bake has to produce. Everything in [wanted] unless the cache can be added to. */
    internal fun spritesToBake(cached: Manifest?, fingerprint: String, wanted: Set<String>): Set<String> {
        if (!cacheStillApplies(cached, fingerprint)) return wanted
        return wanted - cached!!.entries.mapTo(mutableSetOf()) { it.id } - cached.failed.orEmpty().toSet()
    }

    /**
     * Identifies everything that can change how a Pokemon looks: mods and their versions, and the
     * resource packs in play. CobbleDex itself is left out so updating it doesn't cost a rebake;
     * [ATLAS_VERSION] covers the updates that need one.
     */
    private fun installFingerprint(): String = fingerprintOf(
        PlatformHelper.getLoadedMods(),
        Minecraft.getInstance().resourcePackRepository.selectedIds.toList(),
    )

    internal fun fingerprintOf(mods: Map<String, String>, resourcePacks: List<String>): String {
        val text = (mods - CobbleDexMod.MOD_ID).toSortedMap().entries.joinToString(",") { "${it.key}=${it.value}" } +
            "|" + resourcePacks.joinToString(",")
        return MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    /** Allows ensureAtlas() to check again after reconnecting (e.g. to a different server/pack setup). */
    fun resetEnsureAttempt() {
        ensureAttempted.set(false)
    }

    /**
     * Runs for every visible icon on every frame, and working a key out from scratch means cleaning
     * up the name and every aspect each time. Cleared by [resetResolved] alongside the data it reads.
     */
    private val resolvedKeys = ConcurrentHashMap<Pair<String, Set<String>>, ResolvedSpriteKey>()

    fun resetResolved() = resolvedKeys.clear()

    fun resolve(species: String, explicitAspects: Set<String> = emptySet()): ResolvedSpriteKey =
        resolvedKeys.getOrPut(species to explicitAspects) { resolveKey(species, explicitAspects) }

    private fun resolveKey(species: String, explicitAspects: Set<String>): ResolvedSpriteKey {
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

        // The gender default (see PokemonItemCache.withRenderGender) is kept OUT of the SpriteKey/id
        // itself - it only affects the actual render call - so the atlas/website filenames stay exactly
        // as every other lookup already expects them.
        val renderAspects = PokemonItemCache.withRenderGender(normalized, aspects)

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

    fun reload(): Boolean {
        loadAttempted = false
        loadedAtlases = null
        return getLoadedAtlases().isNotEmpty()
    }

    fun buildAtlas(sender: DiagnosticService.MessageSender): Int = startBuild(sender, onlyIds = null)

    /** Bakes everything, or with [onlyIds] just those sprites, kept alongside the rest of the cache. */
    private fun startBuild(sender: DiagnosticService.MessageSender, onlyIds: Set<String>?): Int {
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
                startAtlasCapture(sender, onlyIds)
            } catch (t: Throwable) {
                sender.send("§cSprite atlas build failed: ${t.message ?: t.javaClass.simpleName}")
                DebugLog.warn("Sprite atlas build failed: ${t.message}")
                t.printStackTrace()
                buildInProgress = false
                IconCapture.cleanup(CAPTURE_OWNER)
            }
        }
        return 1
    }

    // Each captureSpecies() call is a real off-screen 3D model render (Cobblemon's own
    // drawProfilePokemon into a 512x512 FBO) plus a full-frame CPU pixel scan for the content
    // bounds - genuinely expensive per species, not a cheap PNG copy. Doing every enabled species
    // (1000+ on a large modpack) back-to-back in one call, as this used to, was a real multi-second
    // render-thread freeze the first time a player ever loads the mod (or after an ATLAS_VERSION
    // bump ships) - the atlas *load* path already had a modest, unavoidable one-shot cost, but this
    // *build* path was unbounded. Spread across many ticks instead, same pattern as
    // CobbleDexJEIPlugin.continueReload().
    //
    // Budgeted by time and not a species count: renders run from under 10ms to over 40ms on a big
    // pack, and four of the slow ones a tick held the game at single digit frame rates for the
    // whole bake. Always does at least one, so a bake finishes however slow the machine is.
    private const val CAPTURE_BUDGET_NANOS = 12_000_000L

    private fun startAtlasCapture(sender: DiagnosticService.MessageSender, onlyIds: Set<String>?) {
        val allKeys = collectSpriteKeys()
        val keys = if (onlyIds == null) allKeys else allKeys.filter { it.key.id in onlyIds }
        val outputDir = cacheDir()
        Files.createDirectories(outputDir)
        val fingerprint = installFingerprint()

        IconCapture.init(CAPTURE_OWNER)
        val images = linkedMapOf<ResolvedSpriteKey, java.awt.image.BufferedImage>()
        val failed = mutableListOf<String>()
        captureJob = CaptureJob(
            keys = keys,
            outputSize = SPRITE_SIZE,
            sender = sender,
            onEach = { resolved, image ->
                if (image != null) images[resolved] = image else failed += resolved.key.id
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
                    val result = finishAtlasBuild(images, failed, outputDir, fingerprint, partial = onlyIds != null)
                    val colour = if (result.failed > 0) "§e" else "§a"
                    sender.send("$colour Sprite atlas ready: ${result.captured}/${result.requested} captured, ${result.failed} failed".trimStart())
                    sender.send("§7${result.atlasPath.toAbsolutePath()}")
                    reload()
                }
            },
        )
    }

    /** A sprite going into the atlas: freshly captured, or carried over from the existing cache. */
    private class AtlasSprite(
        val id: String,
        val species: String,
        val aspects: List<String>,
        val image: java.awt.image.BufferedImage,
    )

    /** The current cache as individual sprites, so a partial bake can add to it. */
    private fun readCachedSprites(outputDir: Path): List<AtlasSprite> {
        val manifest = readCacheManifest() ?: return emptyList()
        val atlas = try {
            ImageIO.read(outputDir.resolve(ATLAS_FILE).toFile())
        } catch (e: Exception) {
            DebugLog.warn("Couldn't read the cached atlas to add to it: ${e.message}")
            null
        } ?: return emptyList()
        return manifest.entries.map { entry ->
            AtlasSprite(entry.id, entry.species, entry.aspects, atlas.getSubimage(entry.x, entry.y, entry.width, entry.height))
        }
    }

    private fun finishAtlasBuild(
        images: Map<ResolvedSpriteKey, java.awt.image.BufferedImage>,
        failed: List<String>,
        outputDir: Path,
        fingerprint: String,
        partial: Boolean,
    ): BuildResult {
        val fresh = images.map { (resolved, image) ->
            AtlasSprite(resolved.key.id, resolved.renderSpecies, resolved.renderAspects.sorted(), image)
        }
        val freshIds = fresh.mapTo(mutableSetOf()) { it.id }
        val carried = if (partial) readCachedSprites(outputDir).filter { it.id !in freshIds } else emptyList()
        val sprites = (carried + fresh).sortedBy { it.id }
        val allFailed = if (partial) {
            (readCacheManifest()?.failed.orEmpty() + failed).distinct() - freshIds
        } else {
            failed
        }

        val rows = ((sprites.size + ATLAS_COLUMNS - 1) / ATLAS_COLUMNS).coerceAtLeast(1)
        val atlasWidth = ATLAS_COLUMNS * SPRITE_SIZE
        val atlasHeight = rows * SPRITE_SIZE
        val atlasImage = java.awt.image.BufferedImage(atlasWidth, atlasHeight, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        val graphics = atlasImage.createGraphics()
        val entries = mutableListOf<SpriteEntry>()
        sprites.forEachIndexed { index, sprite ->
            val x = (index % ATLAS_COLUMNS) * SPRITE_SIZE
            val y = (index / ATLAS_COLUMNS) * SPRITE_SIZE
            graphics.drawImage(sprite.image, x, y, SPRITE_SIZE, SPRITE_SIZE, null)
            entries.add(SpriteEntry(id = sprite.id, species = sprite.species, aspects = sprite.aspects, x = x, y = y))
        }
        graphics.dispose()

        val atlasPath = outputDir.resolve(ATLAS_FILE)
        val manifestPath = outputDir.resolve(MANIFEST_FILE)
        val manifest = Manifest(ATLAS_VERSION, SPRITE_SIZE, ATLAS_FILE, atlasWidth, atlasHeight, entries, fingerprint, allFailed)
        // The two files only mean anything together: a new atlas read through an old manifest draws
        // every Pokemon with some other Pokemon's sprite. Both are finished before either replaces
        // anything, so a crash here can't leave half of a pair.
        val atlasTemp = outputDir.resolve("$ATLAS_FILE.tmp")
        val manifestTemp = outputDir.resolve("$MANIFEST_FILE.tmp")
        ImageIO.write(atlasImage, "PNG", atlasTemp.toFile())
        Files.writeString(manifestTemp, gson.toJson(manifest))
        Files.move(atlasTemp, atlasPath, StandardCopyOption.REPLACE_EXISTING)
        Files.move(manifestTemp, manifestPath, StandardCopyOption.REPLACE_EXISTING)
        if (!partial) deleteLegacySprites(outputDir)

        return BuildResult(atlasPath, manifestPath, images.size + failed.size, images.size, failed.size)
    }

    private fun deleteLegacySprites(outputDir: Path) {
        val legacy = outputDir.resolve(LEGACY_SPRITES_DIR).toFile()
        if (legacy.isDirectory) legacy.deleteRecursively()
    }

    /** One capture-and-write step per species, plus what to do once every key is processed. */
    private class CaptureJob(
        val keys: List<ResolvedSpriteKey>,
        val outputSize: Int,
        val sender: DiagnosticService.MessageSender,
        val onEach: (ResolvedSpriteKey, java.awt.image.BufferedImage?) -> Unit,
        val onFinish: () -> Unit,
        val progressLabel: String = "Sprites",
    ) {
        var index = 0
    }

    @Volatile private var captureJob: CaptureJob? = null

    /**
     * Advances an in-progress sprite capture (atlas build or website export) by up to
     * [CAPTURE_BUDGET_NANOS] of work. Driven every client tick (see [CobbleDexMod.tickClient]); a
     * no-op call (nothing running) just checks a null reference. Waits out a resource reload, since
     * the models it would be drawing are being replaced underneath it.
     */
    fun continueCapture() {
        val job = captureJob ?: return
        if (Minecraft.getInstance().overlay != null) return
        val deadline = System.nanoTime() + CAPTURE_BUDGET_NANOS
        while (job.index < job.keys.size) {
            val resolved = job.keys[job.index]
            try {
                val image = IconCapture.captureSpecies(resolved.renderSpecies, resolved.renderAspects, job.outputSize)
                job.onEach(resolved, image)
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
                IconCapture.cleanup(CAPTURE_OWNER)
                return
            }
            job.index++
            if (job.index == job.keys.size || job.index % 50 == 0) {
                job.sender.send("§7${job.progressLabel}: ${job.index}/${job.keys.size}")
            }
            if (System.nanoTime() >= deadline) break
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
                IconCapture.cleanup(CAPTURE_OWNER)
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
                IconCapture.init(CAPTURE_OWNER)
                var captured = 0
                var failed = 0
                captureJob = CaptureJob(
                    keys = keys,
                    outputSize = size,
                    sender = sender,
                    onEach = { resolved, image ->
                        if (image != null) {
                            ImageIO.write(image, "PNG", outputDir.resolve("${resolved.key.id}.png").toFile())
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
                IconCapture.cleanup(CAPTURE_OWNER)
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
     * The local bake goes first because it's a picture of what this install actually draws. The
     * shipped atlas is a snapshot of one setup, so with it in front a pack that remodels a Pokemon
     * kept the old sprite, and a Cobblemon version with different models showed the wrong one. It
     * also used to flip: the session that finished a bake showed the bake, and the next launch went
     * back to shipped.
     */
    private fun getLoadedAtlases(): List<LoadedAtlas> {
        loadedAtlases?.let { return it }
        if (loadAttempted) return emptyList()
        loadAttempted = true

        // An empty cache is treated as absent everywhere: it can't contribute a sprite, and letting
        // it into the chain would only cost a pointless lookup on every icon.
        val cached = tryLoadCacheAtlas()?.takeIf { it.entriesById.isNotEmpty() }
        val chain = listOfNotNull(cached, tryLoadBundledAtlas())
        loadedAtlases = chain
        return chain
    }

    /** The cache's manifest, without touching the atlas image. Null unless this version can use it. */
    private fun readCacheManifest(): Manifest? {
        val manifestPath = cacheDir().resolve(MANIFEST_FILE)
        if (!Files.exists(manifestPath) || !Files.exists(cacheDir().resolve(ATLAS_FILE))) return null
        return try {
            gson.fromJson(Files.readString(manifestPath), Manifest::class.java)
                ?.takeIf { it.version == ATLAS_VERSION && it.spriteSize == SPRITE_SIZE }
        } catch (e: Exception) {
            DebugLog.warn("Pokemon sprite manifest unreadable: ${e.message}")
            null
        }
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
        .map { it.lowercase().replace(ASPECT_JUNK, "") }
        .filter { it.isNotBlank() }
        .toSortedSet()
}