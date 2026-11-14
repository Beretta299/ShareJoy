package io.github.beretta299.sharejoy

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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

/** The whole match on one screen: world canvas underneath, HUD on top. */
@Composable
fun GameScreen(session: GameSession) {
    val textures = remember { Textures() }
    var frame by remember { mutableIntStateOf(0) }
    var canvasSize by remember { mutableStateOf(IntSize(2, 1)) }

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

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Canvas(
            Modifier
                .fillMaxSize()
                .onSizeChanged { canvasSize = it }
                .pointerInput(session) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val start = screenToWorld(session, w, h, down.position.x, down.position.y)
                        val grabbed = session.press(start.x, start.y)
                        var moved = 0f
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            val delta = change.positionChange()
                            moved += delta.getDistance()
                            if (grabbed) {
                                val p = screenToWorld(session, w, h, change.position.x, change.position.y)
                                session.drag(p.x, p.y)
                            } else {
                                // Dragging anywhere else slides the camera, to peek at the enemy castle.
                                session.pan(delta.x / (w / session.camera.viewW))
                            }
                            change.consume()
                        }
                        if (grabbed) session.release() else if (moved < 14f) session.tap()
                    }
                },
        ) {
            frame // Read the frame counter so the canvas redraws every tick.
            drawWorld(session, textures)
        }
        Hud(session)
    }
}
