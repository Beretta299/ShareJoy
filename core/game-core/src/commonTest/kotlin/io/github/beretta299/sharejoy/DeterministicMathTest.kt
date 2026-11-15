package io.github.beretta299.sharejoy

import io.github.beretta299.sharejoy.physics.DeterministicMath
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals

class DeterministicMathTest {
    @Test
    fun matchesThePlatformFunctionsAcrossManyTurns() {
        var angle = -40f
        while (angle < 40f) {
            assertEquals(sin(angle), DeterministicMath.sin(angle), 2e-5f, "sin($angle)")
            assertEquals(cos(angle), DeterministicMath.cos(angle), 2e-5f, "cos($angle)")
            angle += 0.0137f
        }
    }

    @Test
    fun anUprightBoxIsExactlyUpright() {
        assertEquals(0f, DeterministicMath.sin(0f))
        assertEquals(1f, DeterministicMath.cos(0f))
    }
}
