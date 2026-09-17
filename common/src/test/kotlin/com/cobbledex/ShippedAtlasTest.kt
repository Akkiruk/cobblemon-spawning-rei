package com.cobbledex

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards the exact failure that shipped silently for two months (2026-07-07 to 2026-09-16): a
 * player-cache invalidation bump (`ATLAS_VERSION` 2 -> 3, for the Fungalith Substitute-doll fix)
 * also invalidated the *shipped* atlas without anyone re-baking and re-committing it, because both
 * are gated by the same version check and nothing ever asked whether they still agreed.
 *
 * `tryLoadBundledAtlas` fails that check silently and falls through to a per-player local bake, so
 * there was no crash, no red text, nothing in a log to notice - every release since then quietly
 * shipped 3.7MB of an atlas the mod refused to ever load. This is what should have caught it on the
 * first build that shipped it stale, and is what stops the next version bump from doing the same
 * thing: it fails the build, not a log line someone has to go looking for.
 *
 * If this test fails after a deliberate `ATLAS_VERSION` or `SPRITE_SIZE` bump, that means what it
 * always means: re-bake (`/cobbledex sprites build` on a broad, canonical-only setup - see
 * MANUAL_TASKS.md) and commit the fresh `pokemon_atlas.png` + `pokemon_atlas.json` in the same PR
 * as the bump. Never "fix" this test by changing its expectations to match a stale file.
 */
class ShippedAtlasTest {

    @Test
    fun shippedManifestMatchesTheVersionTheLoaderRequires() {
        val manifest = PokemonSpriteAtlas.readShippedManifestFromClasspath()
        assertEquals(
            PokemonSpriteAtlas.ATLAS_VERSION,
            manifest.version,
            "Shipped atlas is at manifest version ${manifest.version} but the loader requires " +
                "ATLAS_VERSION=${PokemonSpriteAtlas.ATLAS_VERSION} - tryLoadBundledAtlas silently " +
                "rejects it and every player falls back to their own local bake. Re-bake and " +
                "recommit pokemon_atlas.png + pokemon_atlas.json before merging this version bump.",
        )
        assertEquals(
            PokemonSpriteAtlas.SPRITE_SIZE,
            manifest.spriteSize,
            "Shipped atlas sprite size doesn't match SPRITE_SIZE - same silent rejection as a " +
                "version mismatch, via the same check in loadAtlasFromStreams.",
        )
    }

    /**
     * Catches the *other* half of the same failure mode: a manifest that matches version and
     * sprite size but is empty or near-empty - exactly what a bake that failed almost completely
     * (e.g. every capture throwing before this was made version-tolerant) still writes to disk.
     * That file is real, parses fine, and is exactly as useless as no atlas at all.
     *
     * The floor is generous on purpose - it only needs to catch "the bake produced basically
     * nothing," not track the exact species count release to release.
     */
    @Test
    fun shippedManifestHasSubstantiallyMoreThanAHandfulOfSprites() {
        val manifest = PokemonSpriteAtlas.readShippedManifestFromClasspath()
        assertTrue(
            manifest.entries.size > 500,
            "Shipped atlas only has ${manifest.entries.size} sprites - that's a near-total bake " +
                "failure baked in as though it were fine, not a real atlas. Re-bake from a working " +
                "Cobblemon install and recommit.",
        )
    }

    /** Every entry needs a unique id, or two species silently share one atlas slot. */
    @Test
    fun shippedManifestHasNoDuplicateEntryIds() {
        val manifest = PokemonSpriteAtlas.readShippedManifestFromClasspath()
        val ids = manifest.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "Shipped atlas manifest has duplicate entry ids")
    }
}
