package io.github.beretta299.sharejoy.ui.match.render

import androidx.compose.ui.geometry.Offset
import io.github.beretta299.sharejoy.presentation.match.camera.Camera
import kotlin.math.floor
import kotlin.math.sin

internal const val TPM = Textures.TPM

/** Maps world metres (y up) to screen pixels (y down). */
internal class WorldView(
    val scale: Float, val cx: Float, val cy: Float, val w: Float, val h: Float, val ox: Float, val oy: Float,
    seamless: Boolean,
) {
    /**
     * Distant scenery slides slower than the camera. It is measured from this world x, drawn at this pixel.
     * Normally that is the centre of the screen. When two phones show one picture it is the seam between them,
     * so hills and clouds continue from one screen onto the other without a jump.
     */
    val ref = if (seamless) 0f else cx
    val ax = if (seamless) (0f - cx) * scale + w / 2f else w / 2f
    fun x(wx: Float) = (wx - cx) * scale + w / 2f + ox
    fun y(wy: Float) = h / 2f - (wy - cy) * scale + oy
    val left get() = cx - w / 2f / scale
    val right get() = cx + w / 2f / scale
}

internal fun hash(i: Int): Float {
    val v = sin(i * 127.1f + 311.7f) * 43758.547f
    return v - floor(v)
}

/** Converts a screen position back to world metres; used for touch input. */
fun screenToWorld(cam: Camera, width: Float, height: Float, sx: Float, sy: Float): Offset {
    val scale = width / cam.viewW
    return Offset((sx - width / 2f) / scale + cam.cx, cam.cy - (sy - height / 2f) / scale)
}
