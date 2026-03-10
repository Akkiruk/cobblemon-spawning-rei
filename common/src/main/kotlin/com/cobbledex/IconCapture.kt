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
    private var fbo: TextureTarget? = null
    private var debugDumped = false

    fun init() {
        debugDumped = false
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
                scale = 70f,
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
