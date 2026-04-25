package com.cobbledex

import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.network.chat.Component
import java.nio.file.Files
import java.nio.file.Path
import java.util.LinkedHashSet
import java.util.concurrent.ConcurrentHashMap

object PokemonSpriteService {

    private const val SPRITES_PER_TICK = 10
    private const val PLACEHOLDER_BG = 0x7F14161A
    private const val PLACEHOLDER_BORDER = 0xAA30343A.toInt()

    private data class SpriteTexture(
        val location: net.minecraft.resources.ResourceLocation,
        val texture: DynamicTexture,
    )

    private val loadedSprites = ConcurrentHashMap<PokemonSpriteKey, SpriteTexture>()
    private val queuedSprites = LinkedHashSet<PokemonSpriteKey>()
    private val failedSprites = mutableSetOf<PokemonSpriteKey>()

    @Volatile
    private var knownDataVersion = -1L

    fun canRender(species: String, explicitAspects: Set<String> = emptySet()): Boolean {
        return PokemonItemCache.canRender(species, explicitAspects)
    }

    fun tick() {
        refreshForDataVersion()
        if (queuedSprites.isEmpty()) {
            IconCapture.cleanup()
            return
        }

        IconCapture.init()

        repeat(SPRITES_PER_TICK) {
            val key = synchronized(queuedSprites) {
                queuedSprites.firstOrNull()?.also { queuedSprites.remove(it) }
            } ?: return@repeat

            if (loadedSprites.containsKey(key)) {
                return@repeat
            }

            val png = try {
                IconCapture.captureSpeciesToPng(key.species, key.formAspects)
            } catch (e: Exception) {
                DebugLog.once("sprite-capture-${key.idPath}") { "Sprite capture failed for ${key.species}: ${e.message}" }
                null
            }

            if (png == null) {
                failedSprites += key
                return@repeat
            }

            writeCacheFile(key, png)
            registerFromPng(key, png)
        }

        if (queuedSprites.isEmpty()) {
            IconCapture.cleanup()
        }
    }

    fun render(graphics: GuiGraphics, species: String, explicitAspects: Set<String> = emptySet(), x: Int, y: Int, size: Int) {
        render(graphics, PokemonSpriteKey.from(species, explicitAspects), x, y, size)
    }

    fun render(graphics: GuiGraphics, key: PokemonSpriteKey, x: Int, y: Int, size: Int) {
        val sprite = resolveSprite(key)
        if (sprite != null) {
            RenderSystem.enableBlend()
            graphics.blit(sprite.location, x, y, 0f, 0f, size, size, IconCapture.ICON_SIZE, IconCapture.ICON_SIZE)
            return
        }
        drawPlaceholder(graphics, key, x, y, size)
    }

    fun reset() {
        synchronized(queuedSprites) {
            queuedSprites.clear()
        }
        failedSprites.clear()
        loadedSprites.values.forEach { sprite ->
            Minecraft.getInstance().textureManager.release(sprite.location)
            sprite.texture.close()
        }
        loadedSprites.clear()
        knownDataVersion = SpawnDataIndex.dataVersion
        IconCapture.cleanup()
    }

    private fun resolveSprite(key: PokemonSpriteKey): SpriteTexture? {
        refreshForDataVersion()

        loadedSprites[key]?.let { return it }

        if (loadFromDisk(key) != null) {
            return loadedSprites[key]
        }

        if (key !in failedSprites && canRender(key.species, key.formAspects)) {
            synchronized(queuedSprites) {
                queuedSprites.add(key)
            }
        }

        return null
    }

    private fun refreshForDataVersion() {
        val currentVersion = SpawnDataIndex.dataVersion
        if (knownDataVersion == currentVersion) return
        knownDataVersion = currentVersion
        failedSprites.clear()
    }

    private fun loadFromDisk(key: PokemonSpriteKey): SpriteTexture? {
        val path = cachePathFor(key)
        if (!Files.isRegularFile(path)) {
            return null
        }

        return try {
            Files.newInputStream(path).use { input ->
                val image = com.mojang.blaze3d.platform.NativeImage.read(input)
                registerTexture(key, image)
            }
        } catch (e: Exception) {
            DebugLog.once("sprite-load-${key.idPath}") { "Sprite cache load failed for ${key.species}: ${e.message}" }
            try {
                Files.deleteIfExists(path)
            } catch (_: Exception) {
            }
            null
        }
    }

    private fun registerFromPng(key: PokemonSpriteKey, png: ByteArray): SpriteTexture? {
        return try {
            png.inputStream().use { input ->
                val image = com.mojang.blaze3d.platform.NativeImage.read(input)
                registerTexture(key, image)
            }
        } catch (e: Exception) {
            DebugLog.once("sprite-register-${key.idPath}") { "Sprite registration failed for ${key.species}: ${e.message}" }
            null
        }
    }

    private fun registerTexture(key: PokemonSpriteKey, image: com.mojang.blaze3d.platform.NativeImage): SpriteTexture {
        val texture = DynamicTexture(image)
        val location = Minecraft.getInstance().textureManager.register(key.idPath, texture)
        val sprite = SpriteTexture(location, texture)
        val previous = loadedSprites.put(key, sprite)
        if (previous != null) {
            Minecraft.getInstance().textureManager.release(previous.location)
            previous.texture.close()
        }
        return sprite
    }

    private fun writeCacheFile(key: PokemonSpriteKey, png: ByteArray) {
        try {
            val path = cachePathFor(key)
            Files.createDirectories(path.parent)
            Files.write(path, png)
        } catch (e: Exception) {
            DebugLog.once("sprite-cache-write-${key.idPath}") { "Sprite cache write failed for ${key.species}: ${e.message}" }
        }
    }

    private fun cachePathFor(key: PokemonSpriteKey): Path {
        val gameDir = try {
            com.cobbledex.platform.PlatformHelper.getGameDir()
        } catch (_: Exception) {
            java.nio.file.Paths.get(".")
        }
        return gameDir.resolve("cobbledex-cache").resolve("pokemon-sprites").resolve(key.cacheFile)
    }

    private fun drawPlaceholder(graphics: GuiGraphics, key: PokemonSpriteKey, x: Int, y: Int, size: Int) {
        val x2 = x + size
        val y2 = y + size
        graphics.fill(x, y, x2, y2, PLACEHOLDER_BG)
        graphics.fill(x, y, x2, y + 1, PLACEHOLDER_BORDER)
        graphics.fill(x, y2 - 1, x2, y2, PLACEHOLDER_BORDER)
        graphics.fill(x, y, x + 1, y2, PLACEHOLDER_BORDER)
        graphics.fill(x2 - 1, y, x2, y2, PLACEHOLDER_BORDER)

        val name = key.displayName.ifBlank { key.species }
        val symbol = name.firstOrNull()?.uppercase() ?: "?"
        val font = Minecraft.getInstance().font
        val textX = x + (size - font.width(symbol)) / 2
        val textY = y + (size - font.lineHeight) / 2
        graphics.drawString(font, symbol, textX, textY, 0xFFE7EAF0.toInt(), false)
    }
}