package com.cobbledex

import com.mojang.blaze3d.pipeline.TextureTarget
import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.world.item.ItemStack
import org.joml.Matrix4f
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import java.awt.image.BufferedImage

/**
 * Captures ItemStack renders to PNG byte arrays via offscreen framebuffer.
 * Used by SpreadsheetExporter to embed Pokémon/item icons in XLSX files.
 * All methods must be called from the render thread (client main thread).
 */
object IconCapture {

    const val ICON_SIZE = 32

    private var fbo: TextureTarget? = null

    private fun ensureFbo(): TextureTarget {
        fbo?.let { return it }
        val target = TextureTarget(ICON_SIZE, ICON_SIZE, true, Minecraft.ON_OSX)
        target.setClearColor(0f, 0f, 0f, 0f)
        fbo = target
        return target
    }

    fun cleanup() {
        fbo?.destroyBuffers()
        fbo = null
    }

    /**
     * Render an ItemStack to a 32x32 PNG with transparent background.
     * Returns null if rendering fails.
     */
    fun captureItemToPng(stack: ItemStack): ByteArray? {
        if (stack.isEmpty) return null
        val mc = Minecraft.getInstance()
        if (mc.window == null) return null

        return try {
            val target = ensureFbo()
            val prevTarget = mc.mainRenderTarget

            // Bind our FBO and clear it
            target.bindWrite(true)
            target.clear(Minecraft.ON_OSX)

            // Orthographic projection: map 16 GUI units → 32 pixels (2x scale)
            val projMatrix = Matrix4f().setOrtho(0f, 16f, 16f, 0f, -150f, 150f)
            RenderSystem.setProjectionMatrix(projMatrix, com.mojang.blaze3d.vertex.VertexSorting.ORTHOGRAPHIC_Z)

            // Use GuiGraphics to render the item
            val bufferSource = mc.renderBuffers().bufferSource()
            val graphics = GuiGraphics(mc, bufferSource)
            graphics.renderItem(stack, 0, 0)
            bufferSource.endBatch()

            // Restore previous render target
            prevTarget.bindWrite(true)

            readFboToPng(target)
        } catch (e: Exception) {
            DebugLog.warn("Icon capture failed for ${stack.item}: ${e.message}")
            null
        }
    }

    private fun readFboToPng(target: TextureTarget): ByteArray? {
        val image = NativeImage(ICON_SIZE, ICON_SIZE, false)
        try {
            RenderSystem.bindTexture(target.colorTextureId)
            image.downloadTexture(0, false)

            // NativeImage has (0,0) at top-left which matches what we want
            // but the FBO may be Y-flipped, so flip vertically
            val buffered = BufferedImage(ICON_SIZE, ICON_SIZE, BufferedImage.TYPE_INT_ARGB)
            for (y in 0 until ICON_SIZE) {
                for (x in 0 until ICON_SIZE) {
                    val pixel = image.getPixelRGBA(x, y)
                    // NativeImage stores ABGR, convert to ARGB
                    val a = (pixel shr 24) and 0xFF
                    val b = (pixel shr 16) and 0xFF
                    val g = (pixel shr 8) and 0xFF
                    val r = pixel and 0xFF
                    buffered.setRGB(x, ICON_SIZE - 1 - y, (a shl 24) or (r shl 16) or (g shl 8) or b)
                }
            }

            val out = ByteArrayOutputStream()
            ImageIO.write(buffered, "PNG", out)
            return out.toByteArray()
        } catch (e: Exception) {
            DebugLog.warn("FBO pixel readback failed: ${e.message}")
            return null
        } finally {
            image.close()
        }
    }
}
