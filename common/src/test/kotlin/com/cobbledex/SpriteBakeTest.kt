package com.cobbledex

import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SpriteBakeTest {

    private fun manifest(ids: List<String>, fingerprint: String?, failed: List<String>? = null) =
        PokemonSpriteAtlas.Manifest(
            version = PokemonSpriteAtlas.ATLAS_VERSION,
            spriteSize = PokemonSpriteAtlas.SPRITE_SIZE,
            atlas = "pokemon_atlas.png",
            width = 64,
            height = 64,
            entries = ids.map { PokemonSpriteAtlas.SpriteEntry(it, it, emptyList(), 0, 0) },
            fingerprint = fingerprint,
            failed = failed,
        )

    private fun frameWithPixelAt(x: Int, y: Int, size: Int = 32) =
        BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB).apply { setRGB(x, y, 0xFFFFFFFF.toInt()) }

    @Test
    fun noCacheMeansEverythingGetsBaked() {
        val wanted = setOf("pikachu", "raichu")
        assertEquals(wanted, PokemonSpriteAtlas.spritesToBake(null, "a", wanted))
    }

    @Test
    fun anEmptyCacheCountsAsNoCache() {
        val wanted = setOf("pikachu")
        assertEquals(wanted, PokemonSpriteAtlas.spritesToBake(manifest(emptyList(), "a"), "a", wanted))
    }

    @Test
    fun aCacheFromADifferentInstallIsBakedAgainInFull() {
        val wanted = setOf("pikachu", "raichu")
        assertEquals(wanted, PokemonSpriteAtlas.spritesToBake(manifest(listOf("pikachu"), "a"), "b", wanted))
    }

    @Test
    fun aCacheFromBeforeFingerprintsIsBakedAgainInFull() {
        val wanted = setOf("pikachu")
        assertEquals(wanted, PokemonSpriteAtlas.spritesToBake(manifest(listOf("pikachu"), null), "a", wanted))
    }

    @Test
    fun onlyTheGapsAreBakedWhenTheCacheStillApplies() {
        val toBake = PokemonSpriteAtlas.spritesToBake(manifest(listOf("pikachu"), "a"), "a", setOf("pikachu", "serverform"))
        assertEquals(setOf("serverform"), toBake)
    }

    @Test
    fun aSpriteThatFailedIsNotRetriedOnEveryJoin() {
        val cached = manifest(listOf("pikachu"), "a", failed = listOf("broken"))
        assertTrue(PokemonSpriteAtlas.spritesToBake(cached, "a", setOf("pikachu", "broken")).isEmpty())
    }

    @Test
    fun aCompleteCacheBakesNothing() {
        assertTrue(PokemonSpriteAtlas.spritesToBake(manifest(listOf("pikachu"), "a"), "a", setOf("pikachu")).isEmpty())
    }

    @Test
    fun fingerprintFollowsModsAndPacksButNotCobbleDex() {
        val mods = mapOf("cobblemon" to "1.8.2", CobbleDexMod.MOD_ID to "2.32.12")
        val base = PokemonSpriteAtlas.fingerprintOf(mods, listOf("vanilla"))

        assertEquals(base, PokemonSpriteAtlas.fingerprintOf(mods + (CobbleDexMod.MOD_ID to "9.9.9"), listOf("vanilla")))
        assertNotEquals(base, PokemonSpriteAtlas.fingerprintOf(mods + ("cobblemon" to "1.7.3"), listOf("vanilla")))
        assertNotEquals(base, PokemonSpriteAtlas.fingerprintOf(mods, listOf("vanilla", "file/remodels.zip")))
    }

    @Test
    fun renderScalesShrinkWithEveryRetry() {
        val scales = IconCapture.renderScales().toList()
        assertTrue(scales.size > 1)
        assertEquals(scales.sortedDescending(), scales)
        assertEquals(scales.size, scales.toSet().size)
    }

    @Test
    fun boundsCoverEverythingVisible() {
        val frame = frameWithPixelAt(3, 4).apply { setRGB(20, 25, 0xFFFFFFFF.toInt()) }
        assertEquals(IconCapture.ContentBounds(3, 4, 20, 25), IconCapture.contentBounds(frame))
    }

    @Test
    fun faintPixelsAreNotContent() {
        val frame = BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB).apply { setRGB(5, 5, 0x05FFFFFF) }
        assertNull(IconCapture.contentBounds(frame))
    }

    @Test
    fun anyEdgeCountsAsRunningOffTheCanvas() {
        assertFalse(assertNotNull(IconCapture.contentBounds(frameWithPixelAt(5, 5))).touchesEdge(32))
        for ((x, y) in listOf(0 to 5, 31 to 5, 5 to 0, 5 to 31)) {
            assertTrue(assertNotNull(IconCapture.contentBounds(frameWithPixelAt(x, y))).touchesEdge(32), "$x,$y")
        }
    }

    @Test
    fun translucentColourComesBackAtFullStrength() {
        assertEquals(200, IconCapture.unpremultiply(100, 127))
        assertEquals(255, IconCapture.unpremultiply(250, 100))
        assertEquals(90, IconCapture.unpremultiply(90, 255))
        assertEquals(0, IconCapture.unpremultiply(0, 0))
    }

    @Test
    fun resamplingKeepsThinDetailThatOneBigJumpWouldSkip() {
        // A one pixel line in a 512px frame, which point sampling down to 64px mostly misses.
        val frame = BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until 512) frame.setRGB(101, y, 0xFFFFFFFF.toInt())

        val scaled = IconCapture.resample(frame, 64)

        assertEquals(64, scaled.width)
        val strongest = (0 until 64).maxOf { x -> scaled.getRGB(x, 32) ushr 24 }
        assertTrue(strongest > 0, "the line vanished")
        assertEquals(0, scaled.getRGB(40, 32) ushr 24, "nothing should appear away from the line")
    }
}
