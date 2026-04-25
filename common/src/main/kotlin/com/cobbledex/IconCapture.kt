package com.cobbledex

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.client.gui.drawProfilePokemon
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
    private const val CONTENT_PADDING_RATIO = 1.3f
    private const val REFERENCE_SPECIES = "pikachu"
    private const val DEFAULT_REFERENCE_FILL_RATIO = 0.72f
    private var fbo: TextureTarget? = null
    private var debugDumped = false
    private var activeUsers = 0
    private val referenceFillRatio by lazy { computeReferenceFillRatio() }

    private data class AlphaBounds(
        val minX: Int,
        val minY: Int,
        val maxX: Int,
        val maxY: Int,
    ) {
        val width: Int get() = maxX - minX + 1
        val height: Int get() = maxY - minY + 1
        val longestSide: Int get() = maxOf(width, height)
    }

    fun init() {
        activeUsers++
        if (fbo == null) {
            debugDumped = false
            fbo = TextureTarget(RENDER_SIZE, RENDER_SIZE, true, false)
        }
    }

    fun cleanup() {
        if (activeUsers > 0) {
            activeUsers--
        }
        if (activeUsers == 0) {
            fbo?.destroyBuffers()
            fbo = null
        }
    }

    // ── Item icons: read texture PNG from resource packs ─────────────

    fun captureItemToPng(stack: ItemStack): ByteArray? {
        if (stack.isEmpty) return null

        return try {
            val raw = captureItemImage(stack) ?: return null
            val scaled = normalizeToReference(raw, referenceFillRatio)

            val out = ByteArrayOutputStream()
            ImageIO.write(scaled, "PNG", out)
            out.toByteArray()
        } catch (e: Exception) {
            DebugLog.warn("Item icon failed for ${stack.item}: ${e.message}")
            null
        }
    }

    // ── Species icons: render 3D model via Cobblemon's API into FBO ──

    fun captureSpeciesToPng(speciesId: String, aspects: Set<String> = emptySet()): ByteArray? {
        val resolved = PokemonIconResolver.resolve(speciesId, aspects)
        PokemonItemCache.getItem(resolved.captureSpecies, resolved.captureAspects)?.let { stack ->
            captureItemToPng(stack)?.let { return it }
        }
        return captureSpeciesModelToPng(resolved.captureSpecies, resolved.captureAspects)
    }

    private fun captureSpeciesModelToPng(speciesId: String, aspects: Set<String> = emptySet()): ByteArray? {
        val species = PokemonSpecies.getByName(speciesId)
            ?: PokemonItemCache.resolveSpecies(speciesId)
            ?: return null
        val renderable = RenderablePokemon(species, aspects)
        val state = FloatingState()
        val target = fbo ?: return null
        val mc = Minecraft.getInstance()

        return try {
            val mainTarget = mc.mainRenderTarget

            // Explicit transparent clear — default clear color may not be (0,0,0,0)
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

            drawProfilePokemon(
                renderablePokemon = renderable,
                matrixStack = poseStack,
                rotation = rotation,
                state = state,
                partialTicks = 0f,
                scale = 180f,
            )

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
            val contentSide = maxOf(maxX - minX + 1, maxY - minY + 1)
            val side = kotlin.math.ceil(contentSide * CONTENT_PADDING_RATIO.toDouble()).toInt()
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

            val scaled = BufferedImage(ICON_SIZE, ICON_SIZE, BufferedImage.TYPE_INT_ARGB)
            val g2d = scaled.createGraphics()
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g2d.drawImage(cropped, 0, 0, ICON_SIZE, ICON_SIZE, null)
            g2d.dispose()

            val out = ByteArrayOutputStream()
            ImageIO.write(scaled, "PNG", out)
            out.toByteArray()
        } catch (e: Exception) {
            DebugLog.warn("Species icon failed for $speciesId: ${e.message}")
            null
        }
    }

    private fun captureItemImage(stack: ItemStack): BufferedImage? {
        val mc = Minecraft.getInstance()
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
        if (fileW <= 0 || fileH <= 0) {
            image.close()
            return null
        }

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
        return raw
    }

    private fun computeReferenceFillRatio(): Float {
        val referenceStack = PokemonItemCache.getItem(REFERENCE_SPECIES) ?: return DEFAULT_REFERENCE_FILL_RATIO
        val raw = captureItemImage(referenceStack) ?: return DEFAULT_REFERENCE_FILL_RATIO
        val bounds = findAlphaBounds(raw) ?: return DEFAULT_REFERENCE_FILL_RATIO
        val frameSide = maxOf(raw.width, raw.height).coerceAtLeast(1)
        return (bounds.longestSide.toFloat() / frameSide.toFloat())
            .coerceIn(0.4f, 0.9f)
    }

    private fun normalizeToReference(raw: BufferedImage, fillRatio: Float): BufferedImage {
        val target = BufferedImage(ICON_SIZE, ICON_SIZE, BufferedImage.TYPE_INT_ARGB)
        val bounds = findAlphaBounds(raw) ?: return target
        val cropped = raw.getSubimage(bounds.minX, bounds.minY, bounds.width, bounds.height)
        val targetSide = (ICON_SIZE * fillRatio).toInt().coerceIn(1, ICON_SIZE)
        val sourceSide = bounds.longestSide.coerceAtLeast(1)
        val scale = targetSide.toDouble() / sourceSide.toDouble()
        val scaledWidth = kotlin.math.max(1, kotlin.math.round(bounds.width * scale).toInt())
        val scaledHeight = kotlin.math.max(1, kotlin.math.round(bounds.height * scale).toInt())
        val drawX = (ICON_SIZE - scaledWidth) / 2
        val drawY = (ICON_SIZE - scaledHeight) / 2

        val g2d = target.createGraphics()
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g2d.drawImage(cropped, drawX, drawY, scaledWidth, scaledHeight, null)
        g2d.dispose()
        return target
    }

    private fun findAlphaBounds(image: BufferedImage): AlphaBounds? {
        var minX = image.width
        var minY = image.height
        var maxX = -1
        var maxY = -1
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val alpha = image.getRGB(x, y) ushr 24
                if (alpha > 10) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }
        return if (maxX >= 0 && maxY >= 0) AlphaBounds(minX, minY, maxX, maxY) else null
    }
}
