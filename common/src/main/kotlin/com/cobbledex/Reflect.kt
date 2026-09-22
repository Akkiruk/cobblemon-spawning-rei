package com.cobbledex

/**
 * The one home for "call this method/field on a Cobblemon/loader runtime object whose shape differs
 * across versions or mod loaders" - every loader that needs that job (see [MarkDataLoader],
 * [NativeTmDataLoader], [ObtainmentDataLoader], [RidingDataLoader], [SpawnDataLoader],
 * [TmItemUtils], [HerdSpawnReader]) should call into this rather than writing its own
 * `getMethod(...).invoke(...)` or `getDeclaredField(...)` try/catch.
 *
 * Both functions swallow every failure (wrong version, missing member, wrong cast) and return
 * `null` - callers that need to know *why* a specific reflective call failed (distinct per-instance
 * logging, telling "class not found" apart from "found but threw") should keep their own
 * try/catch instead of reaching for this; [SpawnDataLoader.findAllModRootsWithIds] is one such
 * case and intentionally isn't routed through here.
 */
object Reflect {
    /** Invokes a no-arg or String-arg public method reflectively, casting the result to [T]. */
    fun <T> call(target: Any, method: String, vararg args: String): T? = try {
        val paramTypes = Array(args.size) { String::class.java }
        @Suppress("UNCHECKED_CAST")
        target.javaClass.getMethod(method, *paramTypes).invoke(target, *args) as? T
    } catch (_: Throwable) {
        null
    }

    /** Reads a declared field (including private ones) reflectively, casting the value to [T]. */
    fun <T> field(target: Any, name: String): T? = try {
        @Suppress("UNCHECKED_CAST")
        target.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(target) as? T
    } catch (_: Throwable) {
        null
    }
}
