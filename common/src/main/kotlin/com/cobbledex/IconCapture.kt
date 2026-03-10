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
    private const val RENDER_SIZE = 128
    private var fbo: TextureTarget? = null

    fun init() {
        fbo = TextureTarget(RENDER_SIZE, RENDER_SIZE, true, false)
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

            // Animated textures: frames stacked vertically — use first frame only
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

    fun captureSpeciesToPng(speciesId: String, aspects: Set<String> = emptySet()): ByteArray? {
        val id = speciesId.lowercase().replace(Regex("[^a-z0-9]"), "")
        val species = PokemonSpecies.getByName(id) ?: return null
        val renderable = RenderablePokemon(species, aspects)
        val state = FloatingState()
        val target = fbo ?: return null
        val mc = Minecraft.getInstance()

        return try {
            val mainTarget = mc.mainRenderTarget

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
            poseStack.translate(RENDER_SIZE / 2.0, RENDER_SIZE * 0.65, -11000.0)

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
                scale = 50f,
            )

            poseStack.popPose()
            mc.renderBuffers().bufferSource().endBatch()

            target.unbindWrite()
            val nativeImage = NativeImage(RENDER_SIZE, RENDER_SIZE, false)
            RenderSystem.bindTexture(target.getColorTextureId())
            nativeImage.downloadTexture(0, true)

            mainTarget.bindWrite(true)

            val raw = BufferedImage(RENDER_SIZE, RENDER_SIZE, BufferedImage.TYPE_INT_ARGB)
            var minX = RENDER_SIZE; var minY = RENDER_SIZE
            var maxX = -1; var maxY = -1
            for (y in 0 until RENDER_SIZE) {
                for (x in 0 until RENDER_SIZE) {
                    val pixel = nativeImage.getPixelRGBA(x, y)
                    val r = pixel and 0xFF
                    val g = (pixel shr 8) and 0xFF
                    val b = (pixel shr 16) and 0xFF
                    val a = (pixel shr 24) and 0xFF
                    if (a > 0) {
                        if (x < minX) minX = x
                        if (x > maxX) maxX = x
                        if (y < minY) minY = y
                        if (y > maxY) maxY = y
                    }
                    raw.setRGB(x, y, (a shl 24) or (r shl 16) or (g shl 8) or b)
                }
            }
            nativeImage.close()

            if (maxX < 0) {
                DebugLog.warn("Species icon blank: $speciesId")
                return null
            }

            // Auto-crop to content bounds with 1px padding, then downscale
            val pad = 1
            val cx0 = (minX - pad).coerceAtLeast(0)
            val cy0 = (minY - pad).coerceAtLeast(0)
            val cx1 = (maxX + pad).coerceAtMost(RENDER_SIZE - 1)
            val cy1 = (maxY + pad).coerceAtMost(RENDER_SIZE - 1)
            val cropped = raw.getSubimage(cx0, cy0, cx1 - cx0 + 1, cy1 - cy0 + 1)

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
}
