package io.github.beretta299.sharejoy.ui.match

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.beretta299.sharejoy.presentation.match.MatchAction
import io.github.beretta299.sharejoy.presentation.match.MatchScene
import io.github.beretta299.sharejoy.presentation.match.MatchUiState
import io.github.beretta299.sharejoy.presentation.match.MatchViewModel
import io.github.beretta299.sharejoy.presentation.match.camera.Camera
import io.github.beretta299.sharejoy.ui.match.hud.MatchHud
import io.github.beretta299.sharejoy.ui.match.render.Textures

/** A screen wider than this (width / height) stands in for two paired phones side by side. */
private const val PAIRED_ASPECT = 0.8f
private val SEAM = 10.dp
private val PHONE_CORNERS = RoundedCornerShape(18.dp)

/** Connects the [MatchViewModel] to the stateless [MatchScreen] and drives it once per display frame. */
@Composable
fun MatchRoute(viewModel: MatchViewModel) {
    val state by viewModel.uiState.collectAsState()
    var frame by remember { mutableIntStateOf(0) }
    var phoneSize by remember { mutableStateOf(IntSize(1, 2)) }

    LaunchedEffect(viewModel) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else (now - last) / 1_000_000_000f
                last = now
                viewModel.onFrame(dt, phoneSize.width.toFloat() / phoneSize.height.coerceAtLeast(1))
                frame++
            }
        }
    }

    MatchScreen(
        state = state,
        scene = viewModel.scene,
        frame = { frame },
        onAction = viewModel::onAction,
        pointer = remember(viewModel) {
            WorldPointerHandler(
                onDown = viewModel::onPointerDown,
                onAimDrag = viewModel::onAimDrag,
                onAimRelease = viewModel::onAimRelease,
                onPan = viewModel::onPan,
                onTap = viewModel::onTap,
            )
        },
        onPhoneSize = { phoneSize = it },
    )
}

/**
 * A phone paired with another shows its owner's half of the joined picture. Otherwise a portrait screen is one
 * shared phone: it shows one half and the camera slides to whoever is up. A wide screen previews two paired
 * phones side by side, each with its own camera and its own HUD.
 */
@Composable
fun MatchScreen(
    state: MatchUiState,
    scene: MatchScene,
    frame: () -> Int,
    onAction: (MatchAction) -> Unit,
    pointer: WorldPointerHandler,
    onPhoneSize: (IntSize) -> Unit,
) {
    val textures = remember { Textures() }
    var rootSize by remember { mutableStateOf(IntSize(1, 2)) }
    val paired = rootSize.width > rootSize.height * PAIRED_ASPECT

    Box(Modifier.fillMaxSize().background(Color.Black).onSizeChanged { rootSize = it }) {
        val localPlayer = state.localPlayer
        if (localPlayer != null) {
            // One of two real paired phones: show only this phone's half of the joined picture.
            PhoneScreen(
                Modifier.fillMaxSize().onSizeChanged(onPhoneSize),
                state, scene, textures, frame, onAction, pointer,
                camera = { scene.pairCameras[localPlayer] }, viewer = localPlayer,
            )
        } else if (paired) {
            Row(Modifier.fillMaxSize()) {
                for (player in 0..1) {
                    if (player == 1) Spacer(Modifier.width(SEAM))
                    val sizeModifier = if (player == 0) Modifier.onSizeChanged(onPhoneSize) else Modifier
                    PhoneScreen(
                        Modifier.weight(1f).fillMaxSize().clip(PHONE_CORNERS).then(sizeModifier),
                        state, scene, textures, frame, onAction, pointer,
                        camera = { scene.pairCameras[player] }, viewer = player,
                    )
                }
            }
        } else {
            PhoneScreen(
                Modifier.fillMaxSize().onSizeChanged(onPhoneSize),
                state, scene, textures, frame, onAction, pointer,
                camera = { scene.camera }, viewer = null,
            )
        }
    }
}

/** One phone: the world underneath, the HUD on top. [viewer] is the phone's owner, or null on a shared phone. */
@Composable
private fun PhoneScreen(
    modifier: Modifier,
    state: MatchUiState,
    scene: MatchScene,
    textures: Textures,
    frame: () -> Int,
    onAction: (MatchAction) -> Unit,
    pointer: WorldPointerHandler,
    camera: () -> Camera,
    viewer: Int?,
) {
    Box(modifier) {
        WorldCanvas(scene, textures, frame, camera, seamless = viewer != null, pointer = pointer)
        MatchHud(state, viewer, onAction)
    }
}
