package com.cobbledex

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository
import com.cobblemon.mod.common.pokemon.RenderablePokemon
import com.cobblemon.mod.common.pokemon.Species
import com.mojang.blaze3d.pipeline.TextureTarget
import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexSorting
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import org.joml.Matrix4f
import org.joml.Quaternionf
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

object IconCapture {

    const val ICON_SIZE = 32
    private const val RENDER_SIZE = 512

    /** Scale of the first attempt. Most of the roster fits the canvas at this size. */
    private const val BASE_SCALE = 210f

    /** How much a retry shrinks by when a model ran off the canvas, and how many tries it gets. */
    private const val SHRINK_FACTOR = 0.65f
    private const val MAX_RENDER_PASSES = 7

    /** Fainter than this doesn't count as part of the model when finding its bounds. */
    private const val ALPHA_THRESHOLD = 10

    /**
     * Half of the depth the off-screen projection covers.
     *
     * This has to be huge. `drawProfilePokemon` multiplies model depth by `1 / profileScale` on top
     * of our own scale, so depth grows exactly where the model has been shrunk to fit the summary
     * screen, and a poser's `profileTranslation` z (often -20, -100 for G-Max Centiskorch) is
     * multiplied by our scale as well. Cobblemon's own default scale of 20 keeps all that small,
     * ours doesn't: at the old +-10000 Ultra Necrozma lost 47% of its pixels and Dialga its neck,
     * with no error anywhere, because GL just drops whatever falls outside the range.
     */
    private const val DEPTH_RANGE = 1_000_000f

    private var fbo: TextureTarget? = null
    private val owners = mutableSetOf<String>()

    /**
     * Set when the model-drawing call can't be bound at all, rather than a single sprite failing -
     * read by the atlas build so a total wipeout is reported as the one problem it is instead of N
     * identical per-sprite failures.
     */
    @Volatile
    var bindingFailure: String? = null
        private set

    /**
     * Claims the off-screen target for [owner]. The atlas bake and the spreadsheet export can run
     * at the same time and each releases on its own schedule, so the target has to outlive whichever
     * finishes first. Without this, an export finishing mid-bake failed every sprite left in the bake.
     */
    fun init(owner: String) {
        if (owners.isEmpty()) bindingFailure = null
        owners += owner
        if (fbo == null) {
            // setClearColor, not RenderSystem.clearColor: RenderTarget.clear() uses its own colour,
            // which defaults to transparent white, and that bled into everything translucent.
            fbo = TextureTarget(RENDER_SIZE, RENDER_SIZE, true, false).apply { setClearColor(0f, 0f, 0f, 0f) }
        }
    }

    // ── Cobblemon's profile renderer, bound at runtime ───────────────
    //
    // drawProfilePokemon's signature moves between Cobblemon versions, and a direct call compiles
    // against exactly one of them: built against 1.8.0 it throws NoSuchMethodError on every single
    // sprite under 1.7.3, which is a silent 100% bake failure (0/1409 captured) that then writes an
    // empty atlas over the good one. Sprites themselves don't care which version rendered them, so
    // this binds the method at runtime instead of at compile time.
    //
    // It calls Kotlin's `$default` bridge rather than the real function: that bridge takes a
    // trailing bitmask saying which parameters to fill in with their own declared defaults, so only
    // the arguments this actually cares about need supplying and every other parameter - however
    // many a given version has, whatever their types - is defaulted by Cobblemon itself. That's what
    // makes one call work across both signatures without hardcoding either.
    private const val GUI_UTILS = "com.cobblemon.mod.common.client.gui.PokemonGuiUtilsKt"

    /** Argument slots this supplies; everything else is left to Cobblemon's defaults. */
    private val SUPPLIED_SLOTS = setOf(0, 1, 2, 4, 5, 6)

    private val profileMethod: java.lang.reflect.Method? by lazy {
        try {
            val clazz = Class.forName(GUI_UTILS)
            clazz.methods.firstOrNull { method ->
                method.name == "drawProfilePokemon\$default" &&
                    method.parameterTypes.firstOrNull() == RenderablePokemon::class.java
            } ?: run {
                bindingFailure = "Cobblemon's drawProfilePokemon(RenderablePokemon, ...) was not found"
                null
            }
        } catch (t: Throwable) {
            bindingFailure = "Could not load $GUI_UTILS: ${t.javaClass.simpleName}: ${t.message}"
            null
        }
    }

    /** A harmless placeholder for a slot the mask tells Kotlin to overwrite with its own default. */
    private fun placeholderFor(type: Class<*>): Any? = when {
        !type.isPrimitive -> null
        type == java.lang.Float.TYPE -> 0f
        type == java.lang.Integer.TYPE -> 0
        type == java.lang.Boolean.TYPE -> false
        type == java.lang.Double.TYPE -> 0.0
        type == java.lang.Long.TYPE -> 0L
        else -> 0
    }

    private fun drawProfile(
        renderable: RenderablePokemon,
        poseStack: PoseStack,
        rotation: Quaternionf,
        state: FloatingState,
        partialTicks: Float,
        scale: Float,
    ): Boolean {
        val method = profileMethod ?: return false
        val args = defaultedArgs(
            method.parameterTypes,
            mapOf(
                0 to renderable,
                1 to poseStack,
                2 to rotation,
                4 to state,
                5 to partialTicks,
                6 to scale,
            ),
        )
        method.invoke(null, *args)
        return true
    }

    /**
     * Lays out a call to a Kotlin `$default` bridge: [supplied] values in their slots, a placeholder
     * in every other slot, and the trailing (bitmask, marker) pair the bridge itself takes.
     *
     * A set mask bit means "ignore what I passed for this slot and use the declared default", which
     * is what lets one call satisfy signatures of different lengths and parameter types. Separated
     * from the reflection so the bit arithmetic - the part that silently produces a wrong render
     * rather than an error if it's off by one - can be tested against real signatures.
     */
    internal fun defaultedArgs(parameterTypes: Array<Class<*>>, supplied: Map<Int, Any?>): Array<Any?> {
        // The bridge's own two trailing parameters: the defaults bitmask and an unused marker.
        val realParamCount = parameterTypes.size - 2
        val args = arrayOfNulls<Any?>(parameterTypes.size)
        var mask = 0
        for (slot in 0 until realParamCount) {
            if (supplied.containsKey(slot)) {
                args[slot] = supplied[slot]
            } else {
                mask = mask or (1 shl slot)
                args[slot] = placeholderFor(parameterTypes[slot])
            }
        }
        args[realParamCount] = mask
        args[realParamCount + 1] = null
        return args
    }

    /** Safe to call more than once for the same [owner]. */
    fun cleanup(owner: String) {
        owners -= owner
        if (owners.isNotEmpty()) return
        fbo?.destroyBuffers()
        fbo = null
    }

    // ── Item icons: read texture PNG from resource packs ─────────────

    fun captureItemToPng(stack: ItemStack): ByteArray? {
        if (stack.isEmpty) return null
        val mc = Minecraft.getInstance()

        return try {
            val model = mc.itemRenderer.getModel(stack, null, null, 0)
            val sprite = model.particleIcon ?: return null
            val spriteId = sprite.contents().name()

            val textureLoc = ResourceLocation.fromNamespaceAndPath(
                spriteId.namespace,
                "textures/${spriteId.path}.png"
            )

            val resource = mc.resourceManager.getResource(textureLoc).orElse(null) ?: run {
                DebugLog.warn("No texture resource for: $textureLoc")
                return null
            }

            val image = resource.open().use { NativeImage.read(it) }
            val fileW = image.getWidth()
            val fileH = image.getHeight()
            if (fileW <= 0 || fileH <= 0) { image.close(); return null }

            // Animated textures: frames stacked vertically - use first frame only
            val frameW = fileW
            val frameH = if (fileH > fileW) fileW else fileH

            if (frameW > 64 || frameH > 64) {
                image.close()
                return null
            }

            val raw = BufferedImage(frameW, frameH, BufferedImage.TYPE_INT_ARGB)
            for (y in 0 until frameH) {
                for (x in 0 until frameW) {
                    val pixel = image.getPixelRGBA(x, y)
                    val r = pixel and 0xFF
                    val g = (pixel shr 8) and 0xFF
                    val b = (pixel shr 16) and 0xFF
                    val a = (pixel shr 24) and 0xFF
                    raw.setRGB(x, y, (a shl 24) or (r shl 16) or (g shl 8) or b)
                }
            }
            image.close()

            val scaled = if (frameW == ICON_SIZE && frameH == ICON_SIZE) raw else {
                val img = BufferedImage(ICON_SIZE, ICON_SIZE, BufferedImage.TYPE_INT_ARGB)
                val g2d = img.createGraphics()
                g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
                g2d.drawImage(raw, 0, 0, ICON_SIZE, ICON_SIZE, null)
                g2d.dispose()
                img
            }

            val out = ByteArrayOutputStream()
            ImageIO.write(scaled, "PNG", out)
            out.toByteArray()
        } catch (e: Exception) {
            DebugLog.warn("Item icon failed for ${stack.item}: ${e.message}")
            null
        }
    }

    // ── Species icons: render 3D model via Cobblemon's API into FBO ──

    fun captureSpeciesToPng(speciesId: String, aspects: Set<String> = emptySet(), outputSize: Int = ICON_SIZE): ByteArray? {
        val image = captureSpecies(speciesId, aspects, outputSize) ?: return null
        val out = ByteArrayOutputStream()
        ImageIO.write(image, "PNG", out)
        return out.toByteArray()
    }

    /** Scale of each render attempt, in order. */
    internal fun renderScales(): Sequence<Float> =
        generateSequence(BASE_SCALE) { it * SHRINK_FACTOR }.take(MAX_RENDER_PASSES)

    /** Where the visible part of a frame sits. */
    internal data class ContentBounds(val minX: Int, val minY: Int, val maxX: Int, val maxY: Int) {
        /** True when the model may carry on past the canvas, so this frame could be cut off. */
        fun touchesEdge(size: Int): Boolean = minX <= 0 || minY <= 0 || maxX >= size - 1 || maxY >= size - 1
    }

    /** Null for a frame with nothing visible in it. */
    internal fun contentBounds(frame: BufferedImage): ContentBounds? {
        var minX = frame.width; var minY = frame.height; var maxX = -1; var maxY = -1
        for (y in 0 until frame.height) {
            for (x in 0 until frame.width) {
                if ((frame.getRGB(x, y) ushr 24) <= ALPHA_THRESHOLD) continue
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }
        return if (maxX < 0) null else ContentBounds(minX, minY, maxX, maxY)
    }

    /**
     * Undoes what blending over a transparent target does to a translucent pixel: the colour comes
     * back already multiplied by its alpha, and PNG wants it straight, or glows and ghosts turn dark.
     */
    internal fun unpremultiply(channel: Int, alpha: Int): Int = when (alpha) {
        0, 255 -> channel
        else -> (channel * 255 / alpha).coerceAtMost(255)
    }

    /**
     * Scales in halves, then a final step. One bilinear jump from a 500px crop to 64px only ever
     * samples four source pixels per target pixel, so thin parts broke into noise. Goes through a
     * premultiplied copy so transparent pixels can't tint the edges.
     */
    internal fun resample(source: BufferedImage, size: Int): BufferedImage {
        var current = BufferedImage(source.width, source.height, BufferedImage.TYPE_INT_ARGB_PRE)
        current.createGraphics().apply { drawImage(source, 0, 0, null); dispose() }
        while (current.width / 2 >= size) {
            current = drawScaled(current, current.width / 2, BufferedImage.TYPE_INT_ARGB_PRE)
        }
        return drawScaled(current, size, BufferedImage.TYPE_INT_ARGB)
    }

    private fun drawScaled(source: BufferedImage, size: Int, type: Int): BufferedImage {
        val scaled = BufferedImage(size, size, type)
        val g2d = scaled.createGraphics()
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g2d.drawImage(source, 0, 0, size, size, null)
        g2d.dispose()
        return scaled
    }

    /** Draws one frame and reads it back, leaving the game's own render state as it found it. */
    private fun renderFrame(target: TextureTarget, renderable: RenderablePokemon, scale: Float): BufferedImage? {
        val mc = Minecraft.getInstance()
        RenderSystem.backupProjectionMatrix()
        try {
            target.clear(false)
            target.bindWrite(true)
            RenderSystem.viewport(0, 0, RENDER_SIZE, RENDER_SIZE)
            val proj = Matrix4f().setOrtho(
                0f, RENDER_SIZE.toFloat(),
                RENDER_SIZE.toFloat(), 0f,
                -DEPTH_RANGE, DEPTH_RANGE
            )
            RenderSystem.setProjectionMatrix(proj, VertexSorting.ORTHOGRAPHIC_Z)

            val poseStack = PoseStack()
            poseStack.pushPose()
            poseStack.translate(RENDER_SIZE / 2.0, RENDER_SIZE * 0.1, 0.0)

            val rotation = Quaternionf().rotationXYZ(
                Math.toRadians(13.0).toFloat(),
                Math.toRadians(35.0).toFloat(),
                0f
            )

            if (!drawProfile(renderable, poseStack, rotation, FloatingState(), partialTicks = 0f, scale = scale)) return null
            poseStack.popPose()
            mc.renderBuffers().bufferSource().endBatch()
            target.unbindWrite()

            val nativeImage = NativeImage(RENDER_SIZE, RENDER_SIZE, false)
            try {
                RenderSystem.bindTexture(target.getColorTextureId())
                // false = don't flip; ortho projection already maps Y for screen coords
                nativeImage.downloadTexture(0, false)
                val frame = BufferedImage(RENDER_SIZE, RENDER_SIZE, BufferedImage.TYPE_INT_ARGB)
                for (y in 0 until RENDER_SIZE) {
                    val flippedY = RENDER_SIZE - 1 - y
                    for (x in 0 until RENDER_SIZE) {
                        val pixel = nativeImage.getPixelRGBA(x, y)
                        val a = (pixel shr 24) and 0xFF
                        val r = unpremultiply(pixel and 0xFF, a)
                        val g = unpremultiply((pixel shr 8) and 0xFF, a)
                        val b = unpremultiply((pixel shr 16) and 0xFF, a)
                        frame.setRGB(x, flippedY, (a shl 24) or (r shl 16) or (g shl 8) or b)
                    }
                }
                return frame
            } finally {
                nativeImage.close()
            }
        } finally {
            // A draw that threw partway leaves its vertices queued and this target bound, and the
            // next thing rendered would land in the wrong place.
            mc.renderBuffers().bufferSource().endBatch()
            target.unbindWrite()
            mc.mainRenderTarget.bindWrite(true)
            RenderSystem.restoreProjectionMatrix()
        }
    }

    /**
     * False when Cobblemon would draw the green Substitute doll in place of this Pokemon: it does
     * that without a word when nothing in the installed resources gives the species a model, the
     * same two ways round as `VaryingModelRepository.getPoser`. A doll in the atlas looks like a real
     * sprite and would hide the shipped sprite of the actual Pokemon behind it.
     */
    private fun hasOwnModel(species: Species, aspects: Set<String>): Boolean {
        val resolver = VaryingModelRepository.variations[species.resourceIdentifier] ?: return false
        val state = FloatingState().apply { currentAspects = aspects }
        return try {
            resolver.getPoser(state)
            true
        } catch (_: IllegalStateException) {
            false
        }
    }

    fun captureSpecies(speciesId: String, aspects: Set<String> = emptySet(), outputSize: Int = ICON_SIZE): BufferedImage? {
        val id = speciesId.lowercase().replace(Regex("[^a-z0-9]"), "")
        val species = PokemonSpecies.getByName(id) ?: return null
        val renderable = RenderablePokemon(species, aspects)
        val target = fbo ?: return null

        return try {
            if (!hasOwnModel(species, aspects)) {
                DebugLog.info("No model for $speciesId $aspects, leaving it out of the atlas")
                return null
            }

            var frame: BufferedImage? = null
            var bounds: ContentBounds? = null
            for (scale in renderScales()) {
                frame = renderFrame(target, renderable, scale) ?: return null
                bounds = contentBounds(frame) ?: break
                if (!bounds.touchesEdge(RENDER_SIZE)) break
            }

            if (frame == null || bounds == null) {
                DebugLog.warn("Species icon blank: $speciesId")
                return null
            }

            // Square crop centered on content
            val side = maxOf(bounds.maxX - bounds.minX + 1, bounds.maxY - bounds.minY + 1) + 4
            val cx = (bounds.minX + bounds.maxX) / 2
            val cy = (bounds.minY + bounds.maxY) / 2
            val cropSide = side.coerceAtMost(RENDER_SIZE)
            val cx0 = (cx - cropSide / 2).coerceIn(0, RENDER_SIZE - cropSide)
            val cy0 = (cy - cropSide / 2).coerceIn(0, RENDER_SIZE - cropSide)
            resample(frame.getSubimage(cx0, cy0, cropSide, cropSide), outputSize)
        } catch (e: Throwable) {
            // Throwable, not Exception: drawProfilePokemon's signature has changed between
            // Cobblemon versions before (a NoSuchMethodError/LinkageError is an Error, not an
            // Exception - catch (e: Exception) silently lets it right past this guard and crashes
            // the game instead of just skipping this one sprite). Test profiles in this session
            // have run Cobblemon versions older than what CobbleDex builds against, so this is a
            // real, reachable case, not just a defensive nicety.
            // Reflection wraps anything the render itself threw; the wrapper tells us nothing.
            val cause = (e as? java.lang.reflect.InvocationTargetException)?.targetException ?: e
            // A LinkageError is never about this one species - it means the Cobblemon we were built
            // against doesn't match the one running, so every remaining sprite will fail the same
            // way. Recorded so the build reports that once, rather than N identical failures.
            if (cause is LinkageError && bindingFailure == null) {
                bindingFailure = "${cause.javaClass.simpleName}: ${cause.message}"
            }
            DebugLog.warnOnce("icon-capture-${cause.javaClass.simpleName}") {
                "Species icon failed for $speciesId: ${cause.javaClass.simpleName}: ${cause.message}"
            }
            null
        }
    }
}
