package io.github.beretta299.sharejoy

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import io.github.beretta299.sharejoy.render.Textures
import io.github.beretta299.sharejoy.render.drawWorld
import io.github.beretta299.sharejoy.render.screenToWorld
import io.github.beretta299.sharejoy.ui.Hud

@Composable
fun App(session: GameSession = remember { GameSession() }) {
    MaterialTheme(colorScheme = darkColorScheme()) {
        GameScreen(session)
    }
}

/**
 * A portrait screen is one phone: it shows one half and the camera slides to whoever is up. A wide screen
 * previews two paired phones side by side, each with a fixed view of its half and its own HUD.
 */
@Composable
fun GameScreen(session: GameSession) {
    val textures = remember { Textures() }
    var frame by remember { mutableIntStateOf(0) }
    var canvasSize by remember { mutableStateOf(IntSize(1, 2)) }
    var rootSize by remember { mutableStateOf(IntSize(1, 2)) }
    // A wide screen stands in for two paired phones.
    val paired = rootSize.width > rootSize.height * GameSession.WIDE_ASPECT

    LaunchedEffect(session) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else (now - last) / 1_000_000_000f
                last = now
                session.advance(dt, canvasSize.width.toFloat() / canvasSize.height.coerceAtLeast(1))
                frame++
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black).onSizeChanged { rootSize = it }) {
        if (paired) {
            // Two phones standing side by side. Each rests on its owner's home and travels with a shot in the air.
            Row(Modifier.fillMaxSize()) {
                for (player in 0..1) {
                    if (player == 1) Spacer(Modifier.width(SEAM))
                    Box(Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(18.dp)).onSizeChanged { if (player == 0) canvasSize = it }) {
                        WorldCanvas(session, textures, { frame }, seamless = true) { _, _ -> session.pairCameras[player] }
                        Hud(session, viewer = player)
                    }
                }
            }
        } else {
            Box(Modifier.fillMaxSize().onSizeChanged { canvasSize = it }) {
                WorldCanvas(session, textures, { frame }, seamless = false) { _, _ -> session.camera }
                Hud(session)
            }
        }
    }
}

private val SEAM = 10.dp

@Composable
private fun WorldCanvas(
    session: GameSession, textures: Textures, frame: () -> Int, seamless: Boolean, camera: (w: Float, h: Float) -> Camera,
) {
    Canvas(
        Modifier
            .fillMaxSize()
            .pointerInput(session) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    val cam = camera(w, h)
                    val start = screenToWorld(cam, w, h, down.position.x, down.position.y)
                    val grabbed = session.press(start.x, start.y)
                    var moved = 0f
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val delta = change.positionChange()
                        moved += delta.getDistance()
                        if (grabbed) {
                            val p = screenToWorld(cam, w, h, change.position.x, change.position.y)
                            session.drag(p.x, p.y)
                        } else if (!seamless) {
                            // Dragging anywhere else slides the camera, to peek at the enemy castle.
                            session.pan(delta.x / (w / cam.viewW))
                        }
                        change.consume()
                    }
                    if (grabbed) session.release() else if (moved < 14f) session.tap()
                }
            },
    ) {
        frame() // Read the frame counter so the canvas redraws every tick.
        drawWorld(session, textures, camera(size.width, size.height), seamless)
    }
}
