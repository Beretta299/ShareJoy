package io.github.beretta299.sharejoy.physics

import kotlin.math.PI
import kotlin.math.floor

/**
 * Sine and cosine built only from float addition and multiplication, so they give bit-identical results on
 * every device.
 *
 * Two paired phones each run the same simulation and must stay identical shot after shot. The platform's
 * `sin` and `cos` may differ in the last bit between devices, and in a physics simulation such tiny differences
 * grow until the castles fall differently. Basic float arithmetic is exact per IEEE 754, so these do not.
 */
object DeterministicMath {
    private const val TWO_PI = (2.0 * PI).toFloat()
    private const val INVERSE_TWO_PI = (1.0 / (2.0 * PI)).toFloat()
    private const val PI_FLOAT = PI.toFloat()
    private const val HALF_PI = (PI / 2.0).toFloat()

    fun sin(angle: Float): Float {
        var reduced = reduceToHalfTurn(angle)
        // sin(pi - x) = sin(x) folds the range into [-pi/2, pi/2], where the series converges quickly.
        if (reduced > HALF_PI) reduced = PI_FLOAT - reduced
        if (reduced < -HALF_PI) reduced = -PI_FLOAT - reduced
        return sinSeries(reduced)
    }

    fun cos(angle: Float): Float {
        val reduced = reduceToHalfTurn(angle)
        val magnitude = if (reduced < 0f) -reduced else reduced
        // cos(x) = -cos(pi - x) folds [pi/2, pi] onto [0, pi/2].
        return if (magnitude > HALF_PI) -cosSeries(PI_FLOAT - magnitude) else cosSeries(magnitude)
    }

    /** The same angle, moved into [-pi, pi]. */
    private fun reduceToHalfTurn(angle: Float): Float {
        val turns = floor(angle * INVERSE_TWO_PI + 0.5f)
        return angle - turns * TWO_PI
    }

    /** Taylor series of sin, accurate to about 1e-9 on [-pi/2, pi/2]. */
    private fun sinSeries(x: Float): Float {
        val x2 = x * x
        return x * (1f + x2 * (-1f / 6f + x2 * (1f / 120f + x2 * (-1f / 5040f + x2 * (1f / 362880f +
            x2 * (-1f / 39916800f + x2 * (1f / 6227020800f)))))))
    }

    /** Taylor series of cos, accurate to about 1e-9 on [-pi/2, pi/2]. */
    private fun cosSeries(x: Float): Float {
        val x2 = x * x
        return 1f + x2 * (-0.5f + x2 * (1f / 24f + x2 * (-1f / 720f + x2 * (1f / 40320f +
            x2 * (-1f / 3628800f + x2 * (1f / 479001600f + x2 * (-1f / 87178291200f)))))))
    }
}
