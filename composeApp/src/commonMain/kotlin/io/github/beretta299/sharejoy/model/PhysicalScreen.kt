package io.github.beretta299.sharejoy.model

/** The real size of a phone's screen, in millimetres, as held upright. */
data class PhysicalScreen(val widthMm: Float, val heightMm: Float) {
    val isValid: Boolean get() = widthMm > MIN_SIDE_MM && heightMm > MIN_SIDE_MM && widthMm < MAX_SIDE_MM && heightMm < MAX_SIDE_MM

    private companion object {
        const val MIN_SIDE_MM = 20f
        const val MAX_SIDE_MM = 600f
    }
}
