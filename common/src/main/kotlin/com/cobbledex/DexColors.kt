package com.cobbledex

/**
 * The single source of truth for CobbleDex panel colours.
 *
 * Every CobbleDex page is drawn onto a surface the mod paints itself ([PanelLayout.renderSurface]),
 * not onto whatever background the host recipe viewer (REI / JEI / EMI) happens to use. REI's default
 * theme is light, JEI frames its own dark box, EMI depends on the pack - so text contrast used to
 * change from viewer to viewer (issue #42). Painting our own opaque [SURFACE] first makes contrast a
 * property of the mod: the text roles below are all tuned against that one dark surface and read the
 * same everywhere.
 *
 * Rich, meaning-carrying colours (type colours, stat-bar colours, rarity buckets) still live next to
 * the data they describe in [SpawnDisplayHelper]; this object owns the structural palette - the
 * surface, the text roles, and the hairline dividers.
 */
object DexColors {

    // --- Panel surface (opaque: the whole point of issue #42's fix) ---

    /** Opaque panel fill. Drawn before any content so text never sits on the host viewer's theme. */
    const val SURFACE: Int = 0xFF17171B.toInt()

    /** 1px frame around [SURFACE]. */
    const val BORDER: Int = 0xFF4A4A52.toInt()

    // --- Text roles (all tuned for legibility on SURFACE) ---

    /** Primary heading text - species names, page titles. */
    const val TITLE: Int = 0xFFFFFFFF.toInt()

    /** Default body copy. */
    const val BODY: Int = 0xFFDDDDDD.toInt()

    /** Secondary body copy - supporting detail lines. */
    const val SUBTLE: Int = 0xFFBBBBBB.toInt()

    /** De-emphasised text - footers, counts, source notes. */
    const val MUTED: Int = 0xFF888888.toInt()

    /** In-panel section headings ("Abilities", "Requirement"). */
    const val HEADING: Int = 0xFFEEEEEE.toInt()

    /** Header tag / warm accent ("Drops (1/2)", obtainment method names). */
    const val ACCENT: Int = 0xFFDDCC99.toInt()

    /** Links and informational highlights (abilities, regions, EV yield). */
    const val LINK: Int = 0xFF88CCFF.toInt()

    /** Cautionary text - lava, weather multipliers. */
    const val WARNING: Int = 0xFFCC8866.toInt()

    /** Exclusions and error-ish rows. */
    const val DANGER: Int = 0xFFAA7777.toInt()

    // --- Hairline dividers (translucent white over SURFACE) ---

    const val DIVIDER: Int = 0x50FFFFFF
    const val DIVIDER_FAINT: Int = 0x30FFFFFF
    const val DIVIDER_SUBTLE: Int = 0x20FFFFFF

    /** Transient hover wash over a clickable region. Deliberately translucent - works on any base. */
    const val HOVER: Int = 0x30FFFFFF
}
