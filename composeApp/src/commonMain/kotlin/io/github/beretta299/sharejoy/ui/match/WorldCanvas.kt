package io.github.beretta299.sharejoy.ui.match

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import io.github.beretta299.sharejoy.presentation.match.MatchScene
import io.github.beretta299.sharejoy.presentation.match.camera.Camera
import io.github.beretta299.sharejoy.ui.match.render.Textures
import io.github.beretta299.sharejoy.ui.match.render.drawWorld
import io.github.beretta299.sharejoy.ui.match.render.screenToWorld

/** Touch input on the world, already converted from screen pixels to world metres. */
class WorldPointerHandler(
    /** Returns true if the press grabbed the slingshot. */
    val onDown: (worldX: Float, worldY: Float) -> Boolean,
    val onAimDrag: (worldX: Float, worldY: Float) -> Unit,
    val onAimRelease: () -> Unit,
    val onPan: (worldDx: Float) -> Unit,
    val onTap: () -> Unit,
)

/** A finger that moved less than this many pixels counts as a tap. */
private const val TAP_SLOP_PX = 14f

/**
 * Draws the world through [camera] and reports touches. [seamless] is true when this screen is one of two
 * paired phones sharing a picture; then the camera is not the player's to pan.
 */
@Composable
fun WorldCanvas(
    scene: MatchScene,
    textures: Textures,
    frame: () -> Int,
    camera: () -> Camera,
    seamless: Boolean,
    pointer: WorldPointerHandler,
) {
    Canvas(
        Modifier
            .fillMaxSize()
            .pointerInput(pointer, seamless) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    val start = screenToWorld(camera(), w, h, down.position.x, down.position.y)
                    val grabbed = pointer.onDown(start.x, start.y)
                    var moved = 0f
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val delta = change.positionChange()
                        moved += delta.getDistance()
                        if (grabbed) {
                            val p = screenToWorld(camera(), w, h, change.position.x, change.position.y)
                            pointer.onAimDrag(p.x, p.y)
                        } else if (!seamless) {
                            pointer.onPan(delta.x / (w / camera().viewW))
                        }
                        change.consume()
                    }
                    if (grabbed) pointer.onAimRelease() else if (moved < TAP_SLOP_PX) pointer.onTap()
                }
            },
    ) {
        frame() // Read the frame counter so the canvas redraws every tick.
        drawWorld(scene, textures, camera(), seamless)
    }
}
