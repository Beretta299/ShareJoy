package io.github.beretta299.sharejoy.util

/** Small deterministic random generator, so textures and cracks look the same on every device. */
class Rng(seed: Int) {
    private var s = seed * 747796405 + 1442695041
    fun next(): Float {
        s = s * 1664525 + 1013904223
        return ((s ushr 8) and 0xFFFF) / 65536f
    }

    fun range(a: Float, b: Float): Float = a + (b - a) * next()
    fun int(n: Int): Int = (next() * n).toInt().coerceIn(0, n - 1)
}
