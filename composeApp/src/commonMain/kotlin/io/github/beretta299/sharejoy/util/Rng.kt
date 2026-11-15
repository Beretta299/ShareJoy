package io.github.beretta299.sharejoy.util

/** Small deterministic random generator, so textures and cracks look the same on every device. */
class Rng(seed: Int) {
    private var state = seed * 747796405 + 1442695041
    fun next(): Float {
        state = state * 1664525 + 1013904223
        return ((state ushr 8) and 0xFFFF) / 65536f
    }

    fun range(min: Float, max: Float): Float = min + (max - min) * next()
    /** A whole number from 0 until [bound]. */
    fun nextInt(bound: Int): Int = (next() * bound).toInt().coerceIn(0, bound - 1)
}
