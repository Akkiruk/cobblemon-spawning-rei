package com.cobbledex

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblemon.mod.common.pokemon.RenderablePokemon
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
    private var fbo: TextureTarget? = null
    private var debugDumped = false

    /**
     * Set when the model-drawing call can't be bound at all, rather than a single sprite failing -
     * read by the atlas build so a total wipeout is reported as the one problem it is instead of N
     * identical per-sprite failures.
     */
    @Volatile
    var bindingFailure: String? = null
        private set

    fun init() {
        debugDumped = false
        bindingFailure = null
        fbo = TextureTarget(RENDER_SIZE, RENDER_SIZE, true, false)
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

    fun cleanup() {
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
        val id = speciesId.lowercase().replace(Regex("[^a-z0-9]"), "")
        val species = PokemonSpecies.getByName(id) ?: return null
        val renderable = RenderablePokemon(species, aspects)
        val state = FloatingState()
        val target = fbo ?: return null
        val mc = Minecraft.getInstance()

        return try {
            val mainTarget = mc.mainRenderTarget

            // Explicit transparent clear - default clear color may not be (0,0,0,0)
            RenderSystem.clearColor(0f, 0f, 0f, 0f)
            target.clear(false)
            target.bindWrite(true)
            RenderSystem.viewport(0, 0, RENDER_SIZE, RENDER_SIZE)

            val proj = Matrix4f().setOrtho(
                0f, RENDER_SIZE.toFloat(),
                RENDER_SIZE.toFloat(), 0f,
                1000f, 21000f
            )
            RenderSystem.setProjectionMatrix(proj, VertexSorting.ORTHOGRAPHIC_Z)

            val poseStack = PoseStack()
            poseStack.pushPose()
            poseStack.translate(RENDER_SIZE / 2.0, RENDER_SIZE * 0.1, -11000.0)

            val rotation = Quaternionf().rotationXYZ(
                Math.toRadians(13.0).toFloat(),
                Math.toRadians(35.0).toFloat(),
                0f
            )

            if (!drawProfile(renderable, poseStack, rotation, state, partialTicks = 0f, scale = 210f)) {
                poseStack.popPose()
                target.unbindWrite()
                mc.mainRenderTarget.bindWrite(true)
                return null
            }

            poseStack.popPose()
            mc.renderBuffers().bufferSource().endBatch()

            target.unbindWrite()
            val nativeImage = NativeImage(RENDER_SIZE, RENDER_SIZE, false)
            RenderSystem.bindTexture(target.getColorTextureId())
            // false = don't flip; ortho projection already maps Y for screen coords
            nativeImage.downloadTexture(0, false)

            mainTarget.bindWrite(true)

            // Read pixels with vertical flip and find content bounds
            val raw = BufferedImage(RENDER_SIZE, RENDER_SIZE, BufferedImage.TYPE_INT_ARGB)
            var minX = RENDER_SIZE; var minY = RENDER_SIZE; var maxX = -1; var maxY = -1
            for (y in 0 until RENDER_SIZE) {
                val flippedY = RENDER_SIZE - 1 - y
                for (x in 0 until RENDER_SIZE) {
                    val pixel = nativeImage.getPixelRGBA(x, y)
                    val r = pixel and 0xFF
                    val g = (pixel shr 8) and 0xFF
                    val b = (pixel shr 16) and 0xFF
                    val a = (pixel shr 24) and 0xFF
                    if (a > 10) {
                        if (x < minX) minX = x; if (x > maxX) maxX = x
                        if (flippedY < minY) minY = flippedY; if (flippedY > maxY) maxY = flippedY
                    }
                    raw.setRGB(x, flippedY, (a shl 24) or (r shl 16) or (g shl 8) or b)
                }
            }
            nativeImage.close()

            if (maxX < 0) {
                DebugLog.warn("Species icon blank: $speciesId")
                return null
            }

            // Square crop centered on content
            val side = maxOf(maxX - minX + 1, maxY - minY + 1) + 4
            val cx = (minX + maxX) / 2
            val cy = (minY + maxY) / 2
            val cropSide = side.coerceAtMost(RENDER_SIZE)
            val cx0 = (cx - cropSide / 2).coerceIn(0, RENDER_SIZE - cropSide)
            val cy0 = (cy - cropSide / 2).coerceIn(0, RENDER_SIZE - cropSide)
            val cropped = raw.getSubimage(cx0, cy0, cropSide, cropSide)

            // Debug: dump first species to verify rendering pipeline
            if (!debugDumped) {
                debugDumped = true
                try {
                    val gameDir = com.cobbledex.platform.PlatformHelper.getGameDir()
                    val debugDir = gameDir.resolve("cobbledex-export").also { java.nio.file.Files.createDirectories(it) }
                    ImageIO.write(raw, "PNG", debugDir.resolve("debug_fbo_raw.png").toFile())
                    val croppedCopy = BufferedImage(cropSide, cropSide, BufferedImage.TYPE_INT_ARGB)
                    croppedCopy.createGraphics().apply { drawImage(cropped, 0, 0, null); dispose() }
                    ImageIO.write(croppedCopy, "PNG", debugDir.resolve("debug_fbo_cropped.png").toFile())
                    DebugLog.info("Icon debug [$speciesId]: bounds=($minX,$minY)-($maxX,$maxY) crop=($cx0,$cy0)+$cropSide → saved to $debugDir")
                } catch (e: Exception) {
                    DebugLog.warn("Debug dump failed: ${e.message}")
                }
            }

            val scaled = BufferedImage(outputSize, outputSize, BufferedImage.TYPE_INT_ARGB)
            val g2d = scaled.createGraphics()
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g2d.drawImage(cropped, 0, 0, outputSize, outputSize, null)
            g2d.dispose()

            val out = ByteArrayOutputStream()
            ImageIO.write(scaled, "PNG", out)
            out.toByteArray()
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
