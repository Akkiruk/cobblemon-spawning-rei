package com.cobbledex

import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/**
 * Extracts item texture PNGs from the resource pack system.
 * No FBO, no GL — reads the source PNG file directly via ResourceManager.
 */
object IconCapture {

    const val ICON_SIZE = 32

    fun cleanup() { /* nothing to clean up */ }

    /**
     * Read an item's texture from resources and scale to ICON_SIZE x ICON_SIZE PNG.
     * Returns null if the texture can't be found.
     */
    fun captureItemToPng(stack: ItemStack): ByteArray? {
        if (stack.isEmpty) return null
        val mc = Minecraft.getInstance()

        return try {
            val model = mc.itemRenderer.getModel(stack, null, null, 0)
            val sprite = model.particleIcon ?: return null
            val spriteId = sprite.contents().name() // e.g. minecraft:item/bone

            // Build resource path: textures/<path>.png
            val textureLoc = ResourceLocation.fromNamespaceAndPath(
                spriteId.namespace,
                "textures/${spriteId.path}.png"
            )

            val resource = mc.resourceManager.getResource(textureLoc).orElse(null) ?: run {
                DebugLog.warn("No texture resource for: $textureLoc")
                return null
            }

            val image = resource.open().use { NativeImage.read(it) }
            val w = image.getWidth()
            val h = image.getHeight()
            if (w <= 0 || h <= 0) { image.close(); return null }

            // Read pixels from NativeImage
            val raw = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val pixel = image.getPixelRGBA(x, y)
                    // NativeImage "RGBA" is actually ABGR in memory order
                    val r = pixel and 0xFF
                    val g = (pixel shr 8) and 0xFF
                    val b = (pixel shr 16) and 0xFF
                    val a = (pixel shr 24) and 0xFF
                    raw.setRGB(x, y, (a shl 24) or (r shl 16) or (g shl 8) or b)
                }
            }
            image.close()

            // Scale to ICON_SIZE with nearest-neighbor (preserves pixel art)
            val scaled = if (w == ICON_SIZE && h == ICON_SIZE) raw else {
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
            DebugLog.warn("Sprite capture failed for ${stack.item}: ${e.message}")
            null
        }
    }
}
