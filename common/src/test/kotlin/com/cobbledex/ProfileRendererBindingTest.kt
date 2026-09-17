package com.cobbledex

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * Pins the `$default` call layout [IconCapture] uses to reach Cobblemon's profile renderer across
 * versions.
 *
 * The parameter lists below are the real ones, read out of the shipped jars with `javap`. They are
 * why this is reflective at all: built against one, a direct call throws `NoSuchMethodError` on
 * every sprite under the other - a silent 100% bake failure. An off-by-one in the mask wouldn't
 * throw either; it would quietly render with the wrong arguments, so it gets asserted rather than
 * eyeballed.
 */
class ProfileRendererBindingTest {

    private val F = java.lang.Float.TYPE
    private val I = java.lang.Integer.TYPE
    private val B = java.lang.Boolean.TYPE
    private val OBJ = Any::class.java

    /** Cobblemon 1.7.3: `(..., boolean, boolean, 6 floats)` - 15 real parameters. */
    private val types173: Array<Class<*>> = arrayOf(
        OBJ, OBJ, OBJ, OBJ, OBJ, F, F, B, B, F, F, F, F, F, F,
        I, OBJ, // bridge's mask + marker
    )

    /** Cobblemon 1.8.0: `(..., ProfileTransformType, boolean, 6 floats, int)` - 16 real parameters. */
    private val types180: Array<Class<*>> = arrayOf(
        OBJ, OBJ, OBJ, OBJ, OBJ, F, F, OBJ, B, F, F, F, F, F, F, I,
        I, OBJ, // bridge's mask + marker
    )

    private val renderable = Any()
    private val poseStack = Any()
    private val rotation = Any()
    private val state = Any()

    private fun supplied() = mapOf<Int, Any?>(
        0 to renderable, 1 to poseStack, 2 to rotation, 4 to state, 5 to 0f, 6 to 210f,
    )

    private fun expectedMask(realParamCount: Int): Int {
        var mask = 1 shl 3 // poseType is left to Cobblemon
        for (slot in 7 until realParamCount) mask = mask or (1 shl slot)
        return mask
    }

    private fun assertLayout(types: Array<Class<*>>) {
        val realParamCount = types.size - 2
        val args = IconCapture.defaultedArgs(types, supplied())

        assertEquals(types.size, args.size)

        assertSame(renderable, args[0])
        assertSame(poseStack, args[1])
        assertSame(rotation, args[2])
        assertSame(state, args[4])
        assertEquals(0f, args[5])
        assertEquals(210f, args[6])

        assertEquals(expectedMask(realParamCount), args[realParamCount], "defaults bitmask")
        assertNull(args[realParamCount + 1], "bridge marker")

        // Every defaulted primitive slot still needs a boxed value of the right shape - a null in a
        // primitive slot is an IllegalArgumentException from Method.invoke, mask or no mask.
        for (slot in 0 until realParamCount) {
            if (slot in supplied().keys) continue
            if (types[slot].isPrimitive) {
                assertNotNull(args[slot], "primitive slot $slot must not be null")
            }
        }
    }

    @Test
    fun layoutMatchesCobblemon173Signature() = assertLayout(types173)

    @Test
    fun layoutMatchesCobblemon180Signature() = assertLayout(types180)

    /** The two versions differ in length, so the mask must be derived, never hardcoded. */
    @Test
    fun maskDiffersBetweenTheTwoSignatures() {
        val mask173 = IconCapture.defaultedArgs(types173, supplied())[types173.size - 2]
        val mask180 = IconCapture.defaultedArgs(types180, supplied())[types180.size - 2]
        assertEquals(expectedMask(15), mask173)
        assertEquals(expectedMask(16), mask180)
        assertEquals(true, mask173 != mask180)
    }
}
