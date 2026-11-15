package io.github.beretta299.sharejoy.ui.match.render

import androidx.compose.ui.geometry.Offset
import io.github.beretta299.sharejoy.presentation.match.camera.Camera
import kotlin.math.floor
import kotlin.math.sin

internal const val TEXELS_PER_METRE = Textures.TEXELS_PER_METRE

/** Maps world metres (y up) to screen pixels (y down) for one frame. */
internal class WorldViewport(
    camera: Camera,
    val screenWidth: Float,
    val screenHeight: Float,
    /** Screen-shake offset in pixels. */
    private val shakeX: Float,
    private val shakeY: Float,
    seamless: Boolean,
) {
    val pixelsPerMetre = screenWidth / camera.viewWidth
    private val centerX = camera.centerX
    private val centerY = camera.centerY

    /**
     * Distant scenery slides slower than the camera, measured from an anchor that stays put. Normally the anchor
     * is the middle of the screen. When two phones share one picture it is the seam between them (world x = 0),
     * so hills and clouds continue from one screen onto the other without a jump.
     */
    private val parallaxAnchorWorldX = if (seamless) 0f else centerX
    private val parallaxAnchorScreenX = if (seamless) screenX(0f) - shakeX else screenWidth / 2f

    /** World x at the left and right edges of the screen. */
    val leftEdgeX: Float get() = centerX - screenWidth / 2f / pixelsPerMetre
    val rightEdgeX: Float get() = centerX + screenWidth / 2f / pixelsPerMetre

    fun screenX(worldX: Float): Float = (worldX - centerX) * pixelsPerMetre + screenWidth / 2f + shakeX
    fun screenY(worldY: Float): Float = screenHeight / 2f - (worldY - centerY) * pixelsPerMetre + shakeY

    fun worldX(screenX: Float): Float = (screenX - screenWidth / 2f - shakeX) / pixelsPerMetre + centerX
    fun worldY(screenY: Float): Float = centerY - (screenY - screenHeight / 2f - shakeY) / pixelsPerMetre

    /** Screen x of scenery at [worldX] that moves [depthFactor] times as fast as the foreground (0 = fixed, 1 = foreground). */
    fun parallaxScreenX(worldX: Float, depthFactor: Float): Float =
        parallaxAnchorScreenX + (worldX - parallaxAnchorWorldX) * pixelsPerMetre * depthFactor
}

/** A repeatable pseudo-random number in [0, 1) for [index], for scenery that must look the same every frame. */
internal fun hash(index: Int): Float {
    val value = sin(index * 127.1f + 311.7f) * 43758.547f
    return value - floor(value)
}

/** Converts a screen position back to world metres; used for touch input. */
fun screenToWorld(camera: Camera, width: Float, height: Float, screenX: Float, screenY: Float): Offset {
    val pixelsPerMetre = width / camera.viewWidth
    return Offset(
        (screenX - width / 2f) / pixelsPerMetre + camera.centerX,
        camera.centerY - (screenY - height / 2f) / pixelsPerMetre,
    )
}
